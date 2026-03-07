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
     * Priority:
     * 1. Has matching title
     * 2. Has thumbnail
     * 3. Has description
     * 4. Has rating
     */
    fun rankAndPickBest(
        items: List<BookItemDto>,
        queryTitle: String
    ): BookItemDto? {
        if (items.isEmpty()) return null

        return items.maxByOrNull { item ->
            var score = 0
            val info = item.volumeInfo

            // Title similarity boost
            val apiTitle = info.title?.lowercase() ?: ""
            val query    = queryTitle.lowercase()
            if (apiTitle.contains(query) || query.contains(apiTitle)) score += 40

            // Quality boosts
            if (info.imageLinks?.thumbnail != null)  score += 20
            if (!info.description.isNullOrBlank())   score += 20
            if (info.averageRating != null)          score += 10
            if (info.pageCount != null)              score += 5
            if (info.publisher != null)              score += 5

            score
        }
    }
}