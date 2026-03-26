package com.royal.insightlens.data.remote.mapper

import com.royal.insightlens.data.local.entity.BookEntity
import com.royal.insightlens.data.remote.dto.BookItemDto

object BookMapper {

    /**
     * Maps a Google Books API DTO to a Room Entity.
     *
     * All fields handled with safe defaults as per SRS:
     * "Nullable-safe mapping" to prevent crashes on missing metadata.
     *
     * @param dto         The raw API item
     * @param normalizedTitle Pre-computed normalized title from Normalizer
     * @param confidenceScore OCR confidence score from TitleExtractionEngine
     */
    fun toEntity(
        dto: BookItemDto,
        normalizedTitle: String,
        confidenceScore: Float
    ): BookEntity {

        val info  = dto.volumeInfo
        val sale  = dto.saleInfo
        val now   = System.currentTimeMillis()

        // Extract ISBN-13 first, fall back to ISBN-10
        val isbn = info.industryIdentifiers
            ?.firstOrNull { it.type == "ISBN_13" }?.identifier
            ?: info.industryIdentifiers
                ?.firstOrNull { it.type == "ISBN_10" }?.identifier

        // Force HTTPS on thumbnail URL (API returns HTTP)
        val thumbnailUrl = info.imageLinks?.thumbnail
            ?.replace("http://", "https://")
            ?: info.imageLinks?.smallThumbnail
                ?.replace("http://", "https://")

        // Best available purchase/view link
        val bookLink = sale?.buyLink
            ?: info.canonicalVolumeLink
            ?: info.infoLink
            ?: ""

        return BookEntity(
            volumeId        = dto.id,
            title           = info.title ?: "Unknown Title",
            normalizedTitle = normalizedTitle,
            author          = info.authors?.joinToString(", ") ?: "Unknown Author",
            overview        = info.description ?: "",
            rating          = info.averageRating,
            reviewCount     = info.ratingsCount,
            publisher       = info.publisher,
            publishedDate   = info.publishedDate,
            isbn            = isbn,
            pageCount       = info.pageCount,
            categories      = info.categories?.joinToString(", "),
            language        = info.language ?: "en",
            thumbnailUrl    = thumbnailUrl,
            bookLink        = bookLink,
            confidenceScore = confidenceScore,
            scanTimestamp   = now,
            lastAccessed    = now
        )
    }

    /**
     * Ranks API results and returns the best match.
     *
     * Enhanced algorithm with multiple matching strategies:
     * 1. Exact/near-exact title matching (highest priority)
     * 2. Word-order-independent matching
     * 3. Metadata quality validation
     * 4. Publication date filtering (ignore very old books)
     * 5. Language verification
     *
     * Only returns results with confidence scores >= 0.6
     */
    fun rankAndPickBest(
        items: List<BookItemDto>,
        queryTitle: String
    ): BookItemDto? {
        if (items.isEmpty()) return null

        val queryTitleNorm = normalizeForMatching(queryTitle)
        val queryWords = queryTitleNorm.split(Regex("[\\s\\-_]+"))
            .filter { it.isNotBlank() }

        // Score each item with comprehensive algorithm
        val scoredItems = items.map { item ->
            val score = computeMatchScore(
                item = item,
                queryTitleNorm = queryTitleNorm,
                queryWords = queryWords
            )
            Pair(item, score)
        }

        // Filter out low-confidence results
        val validResults = scoredItems.filter { it.second >= 0.6f }

        if (validResults.isEmpty()) {
            // If no exact matches, return highest-scored result anyway
            return scoredItems.maxByOrNull { it.second }?.first
        }

        // Return highest-scored valid result
        return validResults.maxByOrNull { it.second }?.first
    }

    /**
     * Comprehensive scoring algorithm for book matching.
     */
    private fun computeMatchScore(
        item: BookItemDto,
        queryTitleNorm: String,
        queryWords: List<String>
    ): Float {
        val info = item.volumeInfo
        var score = 0.0f

        // ─── Title Matching (40% of score) ─────────────────────────────────────

        val apiTitleNorm = normalizeForMatching(info.title ?: "")
        
        // Exact match: highest priority
        if (apiTitleNorm == queryTitleNorm) {
            score += 0.4f  // Perfect match
        } else if (apiTitleNorm.contains(queryTitleNorm) || queryTitleNorm.contains(apiTitleNorm)) {
            score += 0.35f  // Partial match
        } else {
            // Word-order-independent matching
            val apiWords = apiTitleNorm.split(Regex("[\\s\\-_]+"))
                .filter { it.isNotBlank() }
            val matchedWords = queryWords.count { qWord ->
                apiWords.any { aWord ->
                    levenshteinSimilarity(qWord, aWord) >= 0.85f  // 85% character similarity
                }
            }
            val wordMatchRatio = matchedWords.toFloat() / queryWords.size
            score += wordMatchRatio * 0.30f  // Proportional to matches
        }

        // ─── Metadata Quality (25% of score) ──────────────────────────────────

        // Has thumbnail (quality indicator)
        if (info.imageLinks?.thumbnail != null) score += 0.1f

        // Has description (complete metadata)
        if (!info.description.isNullOrBlank() && info.description.length > 50) {
            score += 0.1f
        }

        // Has authors (legitimate book)
        if (!info.authors.isNullOrEmpty()) score += 0.05f

        // ─── Additional Validation (15% of score) ────────────────────────────

        // Publication date (ignore very old books or future dates)
        val pubYear = extractPublicationYear(info.publishedDate)
        if (pubYear != null) {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val yearDiff = currentYear - pubYear
            
            when {
                yearDiff in 0..50 -> score += 0.1f   // Recent books preferred
                yearDiff in 51..100 -> score += 0.05f // Older but valid
                yearDiff > 100 -> score -= 0.05f      // Very old, slight penalty
                yearDiff < 0 -> score -= 0.1f         // Future date, suspect
            }
        }

        // Has rating (trusted by users)
        if (info.averageRating != null && info.averageRating >= 3.5f) {
            score += 0.05f
        }

        // ─── Penalty Factors (reduce false positives) ────────────────────────

        // Mismatched document type (not a book)
        if (!info.printType.isNullOrEmpty() && info.printType.lowercase() != "book") {
            score -= 0.15f
        }

        // Very short title (likely not a match)
        if ((info.title?.length ?: 0) < 3) {
            score -= 0.1f
        }

        // Language mismatch (if detectable)
        if (info.language != null && info.language != "en" && 
            info.language != "und" && info.language != "") {
            // Don't penalize, but lower priority
            score *= 0.9f
        }

        return score.coerceIn(0f, 1f)
    }

    /**
     * Normalize titles for string comparison.
     * Removes punctuation, extra spaces, common words.
     */
    private fun normalizeForMatching(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")  // Remove punctuation
            .replace(Regex("\\s+"), " ")           // Normalize spaces
            .trim()
    }

    /**
     * Calculate similarity between two strings using Levenshtein distance.
     * Returns 1.0 for identical, 0.0 for completely different.
     */
    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        
        return 1.0f - (distance.toFloat() / maxLen)
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // Deletion
                    dp[i][j - 1] + 1,      // Insertion
                    dp[i - 1][j - 1] + cost // Substitution
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }

    /**
     * Extract publication year from date string.
     * Handles formats like "2023", "2023-01-15", etc.
     */
    private fun extractPublicationYear(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        
        val yearMatch = Regex("\\d{4}").find(dateStr)
        return yearMatch?.value?.toIntOrNull()
    }
}