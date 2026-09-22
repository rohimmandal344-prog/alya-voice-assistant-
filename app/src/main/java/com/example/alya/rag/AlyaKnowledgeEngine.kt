package com.example.alya.rag

import com.example.alya.provider.AlyaEmbeddingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Knowledge document representation.
 */
data class KnowledgeChunk(
    val id: String,
    val sourceTitle: String,
    val content: String,
    val embedding: FloatArray? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KnowledgeChunk) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * AlyaKnowledgeEngine
 *
 * Lightweight on-device vector search & retrieval engine with cosine similarity ranking.
 */
class AlyaKnowledgeEngine(
    private val embeddingProvider: AlyaEmbeddingProvider? = null
) {
    private val chunks = mutableListOf<KnowledgeChunk>()

    suspend fun ingestDocument(title: String, text: String, chunkSize: Int = 300) = withContext(Dispatchers.Default) {
        val rawChunks = text.split("\n\n").filter { it.isNotBlank() }
        for ((idx, chunkText) in rawChunks.withIndex()) {
            val emb = embeddingProvider?.embed(chunkText)
            chunks.add(
                KnowledgeChunk(
                    id = "${title.hashCode()}_$idx",
                    sourceTitle = title,
                    content = chunkText,
                    embedding = emb
                )
            )
        }
    }

    suspend fun search(query: String, topK: Int = 3): List<KnowledgeChunk> = withContext(Dispatchers.Default) {
        if (chunks.isEmpty()) return@withContext emptyList()

        // 1. Vector Search if embeddings available
        val queryEmbedding = embeddingProvider?.embed(query)
        if (queryEmbedding != null) {
            val scored = chunks.mapNotNull { chunk ->
                val emb = chunk.embedding ?: return@mapNotNull null
                val sim = cosineSimilarity(queryEmbedding, emb)
                chunk to sim
            }.sortedByDescending { it.second }
            return@withContext scored.take(topK).map { it.first }
        }

        // 2. Keyword fallback search
        val queryKeywords = query.lowercase().split(" ").filter { it.length > 2 }
        chunks.sortedByDescending { chunk ->
            val contentLower = chunk.content.lowercase()
            queryKeywords.count { contentLower.contains(it) }
        }.take(topK)
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f
        var n1 = 0.0f
        var n2 = 0.0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        val denom = sqrt(n1.toDouble()) * sqrt(n2.toDouble())
        return if (denom == 0.0) 0.0f else (dot / denom).toFloat()
    }
}
