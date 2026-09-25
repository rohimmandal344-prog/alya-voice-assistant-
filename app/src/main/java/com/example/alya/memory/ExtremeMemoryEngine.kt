package com.example.alya.memory

import android.content.Context
import com.example.data.local.dao.MemoryDao
import com.example.data.local.entity.MemoryCategory
import com.example.data.local.entity.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Scored memory item with detailed recall breakdown.
 */
data class ScoredMemory(
    val memory: MemoryEntity,
    val semanticScore: Float,
    val recencyScore: Float,
    val importanceScore: Float,
    val compositeScore: Float
)

/**
 * Auto-extracted fact proposal from user interaction.
 */
data class ExtractedFact(
    val category: MemoryCategory,
    val key: String,
    val content: String,
    val confidence: Float
)

/**
 * ExtremeMemoryEngine
 *
 * Ultra-high-capacity long-term memory engine for Alya Assistant.
 * Provides:
 * - Multi-factor recall scoring (Semantic relevance + Recency decay + Importance)
 * - Automatic fact extraction & user preference profiling from dialogues
 * - Associative linking across episodic and semantic memory nodes
 * - Deduplication & safe memory consolidation
 */
class ExtremeMemoryEngine(
    private val context: Context,
    private val memoryDao: MemoryDao
) {

    /**
     * Retrieve the most contextually relevant memories for a prompt using multi-factor ranking.
     */
    suspend fun retrieveMemories(
        query: String,
        topK: Int = 5,
        minScoreThreshold: Float = 0.25f
    ): List<ScoredMemory> = withContext(Dispatchers.IO) {
        val allMemories = memoryDao.getAllMemories()
        if (allMemories.isEmpty()) return@withContext emptyList()

        val queryTokens = tokenize(query)
        val now = System.currentTimeMillis()
        val oneDayMs = 86400000.0

        val scored = allMemories.map { memory ->
            val memoryTokens = tokenize("${memory.key} ${memory.content}")
            
            // 1. Semantic Token Overlap (Jaccard + Keyword Match)
            val intersection = queryTokens.intersect(memoryTokens).size
            val union = (queryTokens + memoryTokens).distinct().size
            val jaccard = if (union > 0) intersection.toFloat() / union.toFloat() else 0f
            
            val directSubstringMatch = if (memory.content.lowercase(Locale.ROOT).contains(query.lowercase(Locale.ROOT)) ||
                query.lowercase(Locale.ROOT).contains(memory.key.lowercase(Locale.ROOT))) 0.4f else 0f
            
            val semanticScore = min(1.0f, (jaccard * 1.5f) + directSubstringMatch)

            // 2. Recency Decay (Exponential Half-life of 30 days)
            val ageDays = max(0.0, (now - memory.updatedAt).toDouble() / oneDayMs)
            val recencyScore = exp(-ageDays / 30.0).toFloat()

            // 3. Importance Score based on category heuristics
            val importanceScore = when (memory.category) {
                MemoryCategory.IMPORTANT_FACTS.name -> 0.9f
                MemoryCategory.USER_PREFERENCES.name -> 0.85f
                MemoryCategory.ASSISTANT_PREFERENCES.name -> 0.8f
                MemoryCategory.REMINDERS.name -> 0.75f
                MemoryCategory.TASKS.name -> 0.7f
                MemoryCategory.CONVERSATION_CONTEXT.name -> 0.5f
                else -> 0.6f
            }

            // 4. Weighted Composite Score
            // Semantic weight = 60%, Importance weight = 25%, Recency weight = 15%
            val composite = (semanticScore * 0.60f) + (importanceScore * 0.25f) + (recencyScore * 0.15f)

            ScoredMemory(
                memory = memory,
                semanticScore = semanticScore,
                recencyScore = recencyScore,
                importanceScore = importanceScore,
                compositeScore = composite
            )
        }

        scored.filter { it.compositeScore >= minScoreThreshold }
            .sortedByDescending { it.compositeScore }
            .take(topK)
    }

    /**
     * Automatic Fact Extraction from user message.
     * Heuristically detects statements of preference, identity, hobbies, or rules.
     */
    suspend fun analyzeAndExtractFacts(userMessage: String): List<ExtractedFact> = withContext(Dispatchers.Default) {
        val facts = mutableListOf<ExtractedFact>()
        val lower = userMessage.trim().lowercase(Locale.ROOT)

        // Pattern 1: User name / identity
        val nameMatch = Regex("(?:my name is|call me|i am|i'm)\\s+([a-zA-Z]+)", RegexOption.IGNORE_CASE).find(userMessage)
        if (nameMatch != null && !lower.contains("happy") && !lower.contains("sad") && !lower.contains("tired")) {
            val name = nameMatch.groupValues[1]
            if (name.length in 2..25) {
                facts.add(
                    ExtractedFact(
                        category = MemoryCategory.USER_PREFERENCES,
                        key = "User Name",
                        content = "User prefers to be called $name",
                        confidence = 0.95f
                    )
                )
            }
        }

        // Pattern 2: Favorites (Food, Color, Movie, Song, Sport)
        val favMatch = Regex("(?:my favorite|i love|i really like)\\s+([a-zA-Z\\s]+?)(?:\\s+is|\\s+are|\\.|\\,|$)", RegexOption.IGNORE_CASE).find(userMessage)
        if (favMatch != null) {
            val topic = favMatch.groupValues[1].trim()
            if (topic.isNotBlank() && topic.length < 50) {
                facts.add(
                    ExtractedFact(
                        category = MemoryCategory.USER_PREFERENCES,
                        key = "Favorite: $topic",
                        content = userMessage.trim(),
                        confidence = 0.85f
                    )
                )
            }
        }

        // Pattern 3: Work / Profession / Education
        if (lower.contains("i work as") || lower.contains("i work at") || lower.contains("my job is") || lower.contains("my profession is")) {
            facts.add(
                ExtractedFact(
                    category = MemoryCategory.IMPORTANT_FACTS,
                    key = "Profession & Career",
                    content = userMessage.trim(),
                    confidence = 0.90f
                )
            )
        }

        // Pattern 4: Explicit "Remember that..." or "Never forget..."
        val rememberMatch = Regex("(?:remember that|don't forget that|keep in mind that|note that)\\s+(.+)", RegexOption.IGNORE_CASE).find(userMessage)
        if (rememberMatch != null) {
            val directive = rememberMatch.groupValues[1].trim()
            facts.add(
                ExtractedFact(
                    category = MemoryCategory.IMPORTANT_FACTS,
                    key = "User Note",
                    content = directive,
                    confidence = 0.95f
                )
            )
        }

        // Pattern 5: Health & Dietary preferences
        if (lower.contains("i am allergic to") || lower.contains("i'm allergic to") || lower.contains("i am vegan") || lower.contains("i am vegetarian")) {
            facts.add(
                ExtractedFact(
                    category = MemoryCategory.IMPORTANT_FACTS,
                    key = "Dietary / Allergy Info",
                    content = userMessage.trim(),
                    confidence = 0.98f
                )
            )
        }

        facts
    }

    /**
     * Consolidate and store extracted facts safely without cluttering duplicates.
     */
    suspend fun saveExtractedFacts(facts: List<ExtractedFact>) = withContext(Dispatchers.IO) {
        val existing = memoryDao.getAllMemories()
        for (fact in facts) {
            val duplicate = existing.find { 
                it.key.equals(fact.key, ignoreCase = true) || 
                it.content.equals(fact.content, ignoreCase = true) 
            }
            if (duplicate != null) {
                // Update timestamp & content
                memoryDao.updateMemory(
                    duplicate.copy(
                        content = fact.content,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                memoryDao.insertMemory(
                    MemoryEntity(
                        category = fact.category.name,
                        key = fact.key,
                        content = fact.content,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun tokenize(text: String): Set<String> {
        val stopWords = setOf("the", "a", "an", "is", "are", "was", "were", "and", "or", "in", "on", "at", "to", "for", "with", "of", "my", "your", "i", "you", "it", "this", "that")
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && !stopWords.contains(it) }
            .toSet()
    }
}
