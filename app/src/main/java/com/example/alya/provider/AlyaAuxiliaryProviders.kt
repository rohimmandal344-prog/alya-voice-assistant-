package com.example.alya.provider

import kotlinx.coroutines.flow.Flow

/**
 * AlyaEmbeddingProvider
 *
 * Pluggable contract for text embedding generation used across Alya RAG & Semantic Memory.
 */
interface AlyaEmbeddingProvider {
    val providerId: String
    val dimension: Int
    val isLocal: Boolean

    suspend fun embed(text: String): FloatArray
    suspend fun embedBatch(texts: List<String>): List<FloatArray>
}

/**
 * AlyaSearchProvider
 *
 * Pluggable contract for external web search grounding.
 */
interface AlyaSearchProvider {
    val providerId: String
    suspend fun search(query: String, maxResults: Int = 5): List<AlyaSearchResult>
}

data class AlyaSearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String = "Web"
)
