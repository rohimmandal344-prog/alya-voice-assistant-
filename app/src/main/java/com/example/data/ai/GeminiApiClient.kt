package com.example.data.ai

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

class GeminiRateLimitException(
    message: String,
    val retryAfterSeconds: Long = 60L
) : Exception(message)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse

    @POST("v1beta/models/{model}:streamGenerateContent")
    @retrofit2.http.Streaming
    suspend fun generateContentStream(
        @retrofit2.http.Path("model") model: String,
        @Query("alt") alt: String = "sse",
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): okhttp3.ResponseBody
}

class GeminiApiClient {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS) // Resilient for 128kbps low bandwidth connections
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 3, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Cancel all active network requests and evict all idle/active sockets in the connection pool.
     * Ensures immediate release of sockets upon session close asynchronously without blocking main thread.
     */
    fun evictActiveConnections() {
        ioScope.launch {
            try {
                android.util.Log.i("GeminiApiClient", "SessionManager: Evicting all network connections and cancelling pending HTTP requests on IO dispatcher.")
                okHttpClient.dispatcher.cancelAll()
                okHttpClient.connectionPool.evictAll()
            } catch (e: Exception) {
                android.util.Log.e("GeminiApiClient", "SessionManager: Error evicting active connections", e)
            }
        }
    }

    // Rate Limiting & Throttling
    private val requestMutex = Mutex()
    private val lastRequestTimestamp = AtomicLong(0L)
    private val minRequestIntervalMillis = 200L // Low latency interval for real-time conversation
    private val rateLimitedUntilMillis = AtomicLong(0L)

    private val _isRateLimited = MutableStateFlow(false)
    val isRateLimited: StateFlow<Boolean> = _isRateLimited.asStateFlow()

    private val _rateLimitSecondsRemaining = MutableStateFlow(0L)
    val rateLimitSecondsRemaining: StateFlow<Long> = _rateLimitSecondsRemaining.asStateFlow()

    fun checkCooldownStatus(): Boolean {
        val now = System.currentTimeMillis()
        val cooldownUntil = rateLimitedUntilMillis.get()
        if (now < cooldownUntil) {
            val remaining = (cooldownUntil - now) / 1000L
            _isRateLimited.value = true
            _rateLimitSecondsRemaining.value = remaining
            return true
        } else {
            _isRateLimited.value = false
            _rateLimitSecondsRemaining.value = 0L
            return false
        }
    }

    suspend fun generateResponseStream(
        messages: List<Pair<String, String>>,
        systemInstruction: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024,
        preferredModel: String? = null,
        onChunk: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("API key not configured."))
        }

        val request = buildRequest(messages, systemInstruction, temperature, maxTokens)
        val baseCandidateModels = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash-exp"
        )
        val candidateModels = if (!preferredModel.isNullOrBlank() && preferredModel != "models/gemini-2.0-flash-exp") {
            listOf(preferredModel) + baseCandidateModels.filter { it != preferredModel }
        } else {
            baseCandidateModels
        }

        var lastException: Throwable? = null
        for (model in candidateModels) {
            try {
                val responseBody = service.generateContentStream(model = model, apiKey = apiKey, request = request)
                responseBody.byteStream().bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.startsWith("data: ")) {
                            val jsonData = line!!.substring(6)
                            try {
                                val chunk = moshi.adapter(GeminiGenerateResponse::class.java).fromJson(jsonData)
                                val parts = chunk?.candidates?.firstOrNull()?.content?.parts
                                parts?.forEach { part ->
                                    if (part.thought == true) {
                                        return@forEach // Skip thoughts
                                    }
                                    val text = part.text
                                    if (!text.isNullOrEmpty()) {
                                        withContext(Dispatchers.Main) { onChunk(text) }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("GeminiApiClient", "Error parsing chunk: ${e.message}")
                            }
                        }
                    }
                }
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.w("GeminiApiClient", "Streaming model '$model' failed: ${e.message}. Trying next candidate...")
                lastException = e
            }
        }
        Result.failure(lastException ?: IllegalStateException("All candidate streaming models failed."))
    }

    private fun buildRequest(
        messages: List<Pair<String, String>>,
        systemInstruction: String,
        temperature: Float,
        maxTokens: Int
    ): GeminiGenerateRequest {
        val formattedContents = mutableListOf<GeminiContent>()
        var currentRole: String? = null
        val currentParts = mutableListOf<String>()

        for ((role, text) in messages.filter { it.second.isNotBlank() }) {
            val apiRole = if (role == "user") "user" else "model"
            if (currentRole == apiRole) {
                currentParts.add(text)
            } else {
                if (currentRole != null && currentParts.isNotEmpty()) {
                    formattedContents.add(GeminiContent(currentRole, listOf(GeminiPart(text = currentParts.joinToString("\n\n")))))
                    currentParts.clear()
                }
                currentRole = apiRole
                currentParts.add(text)
            }
        }
        if (currentRole != null && currentParts.isNotEmpty()) {
            formattedContents.add(GeminiContent(currentRole, listOf(GeminiPart(text = currentParts.joinToString("\n\n")))))
        }

        return GeminiGenerateRequest(
            contents = formattedContents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
            generationConfig = GeminiGenerationConfig(
                temperature = temperature,
                maxOutputTokens = maxTokens,
                responseModalities = listOf("TEXT")
            )
        )
    }

    suspend fun generateResponse(
        messages: List<Pair<String, String>>, // role ("user" / "model"), content
        systemInstruction: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 512,
        preferredModel: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please provide an API key in the AI Studio Secrets panel.")
            )
        }

        // Fast-fail if currently under rate limit cooldown
        if (checkCooldownStatus()) {
            val remaining = _rateLimitSecondsRemaining.value
            return@withContext Result.failure(
                GeminiRateLimitException("Gemini quota rate limit active. Cooldown remaining: ${remaining}s.", remaining)
            )
        }

        // Format and sanitize conversation history for Gemini API
        val rawContents = messages.filter { it.second.isNotBlank() }
        val formattedContents = mutableListOf<GeminiContent>()

        var currentRole: String? = null
        val currentParts = mutableListOf<String>()

        for ((role, text) in rawContents) {
            val apiRole = if (role == "user") "user" else "model"
            if (formattedContents.isEmpty() && currentParts.isEmpty() && apiRole != "user") {
                continue
            }
            if (currentRole == apiRole) {
                currentParts.add(text)
            } else {
                if (currentRole != null && currentParts.isNotEmpty()) {
                    formattedContents.add(
                        GeminiContent(
                            role = currentRole,
                            parts = listOf(GeminiPart(text = currentParts.joinToString("\n\n")))
                        )
                    )
                    currentParts.clear()
                }
                currentRole = apiRole
                currentParts.add(text)
            }
        }
        if (currentRole != null && currentParts.isNotEmpty()) {
            formattedContents.add(
                GeminiContent(
                    role = currentRole,
                    parts = listOf(GeminiPart(text = currentParts.joinToString("\n\n")))
                )
            )
        }

        if (formattedContents.isEmpty()) {
            formattedContents.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = "Hello Alya"))
                )
            )
        }

        val request = GeminiGenerateRequest(
            contents = formattedContents,
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemInstruction))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = temperature,
                maxOutputTokens = maxTokens,
                responseModalities = listOf("TEXT")
            ),
            tools = listOf(GeminiTool(googleSearch = GoogleSearchTool()))
        )

        // Resilient execution with valid models per skill guidelines
        val maxRetries = 3
        var attempt = 0
        var lastException: Throwable? = null

        val baseCandidateModels = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash-exp"
        )
        val candidateModels = if (!preferredModel.isNullOrBlank()) {
            listOf(preferredModel) + baseCandidateModels.filter { it != preferredModel }
        } else {
            baseCandidateModels
        }

        while (attempt < maxRetries) {
            requestMutex.withLock {
                val now = System.currentTimeMillis()
                val elapsed = now - lastRequestTimestamp.get()
                if (elapsed < minRequestIntervalMillis) {
                    delay(minRequestIntervalMillis - elapsed)
                }
                lastRequestTimestamp.set(System.currentTimeMillis())
            }

            for (modelName in candidateModels) {
                try {
                    val response = service.generateContent(modelName, apiKey, request)
                    val parts = response.candidates?.firstOrNull()?.content?.parts
                    val candidateText = parts?.filter { it.thought != true }
                        ?.mapNotNull { it.text }
                        ?.joinToString("")
                    if (!candidateText.isNullOrBlank()) {
                        _isRateLimited.value = false
                        _rateLimitSecondsRemaining.value = 0L
                        return@withContext Result.success(candidateText.trim())
                    } else if (response.error != null) {
                        val code = response.error.code ?: 0
                        val msg = response.error.message ?: ""
                        if (code == 429 || msg.contains("quota", ignoreCase = true) || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                            throw GeminiRateLimitException("API Quota reached: $msg", 30L)
                        }
                        if (code in listOf(503, 500, 502, 504, 404) || msg.contains("overloaded", ignoreCase = true) || msg.contains("UNAVAILABLE", ignoreCase = true)) {
                            android.util.Log.w("GeminiApiClient", "Model $modelName transient error ($code: $msg). Trying next model candidate.")
                            continue
                        }
                        lastException = Exception(msg.ifBlank { "Gemini API error occurred." })
                    }
                } catch (e: HttpException) {
                    lastException = e
                    val httpCode = e.code()
                    android.util.Log.w("GeminiApiClient", "Model $modelName returned HTTP $httpCode: ${e.message()}. Retrying gracefully.")

                    if (httpCode in listOf(503, 500, 502, 504, 404)) {
                        // Transient service error, try next candidate model
                        continue
                    }

                    if (httpCode == 429) {
                        break
                    }
                } catch (e: GeminiRateLimitException) {
                    lastException = e
                    rateLimitedUntilMillis.set(System.currentTimeMillis() + (e.retryAfterSeconds * 1000L))
                    _isRateLimited.value = true
                    _rateLimitSecondsRemaining.value = e.retryAfterSeconds
                    return@withContext Result.failure(e)
                } catch (e: Exception) {
                    lastException = e
                    android.util.Log.w("GeminiApiClient", "Model $modelName request error: ${e.message}")
                    continue
                }
            }

            attempt++
            if (attempt < maxRetries) {
                val backoffDelay = (400L * (1 shl attempt)) + Random.nextLong(50, 150)
                delay(backoffDelay)
            }
        }

        val finalException = lastException ?: Exception("Cloud AI service temporarily busy. Local intelligence active.")
        com.example.util.diagnostics.DiagnosticLogManager.instance.logException(
            finalException, "GeminiApiClient", "Failed after $attempt attempts across candidate models"
        )
        Result.failure(finalException)
    }
}
