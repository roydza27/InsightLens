package com.royal.insightlens.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["volumeId"], unique = true),
        Index(value = ["normalizedTitle"])
    ]
)
data class BookEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Unique identifier from Google Books API
    val volumeId: String,

    // Original title from API
    val title: String,

    // Normalized for dedup prevention (lowercase, trimmed, no punctuation)
    val normalizedTitle: String,

    val author: String,

    val overview: String,

    // Nullable fields as per SRS
    val rating: Float? = null,

    val reviewCount: Int? = null,

    val publisher: String? = null,

    val publishedDate: String? = null,

    val isbn: String? = null,

    val pageCount: Int? = null,

    val categories: String? = null,

    // ISO language code e.g. "en"
    val language: String,

    val thumbnailUrl: String? = null,

    // External purchase / view link
    val bookLink: String,

    // OCR confidence score from TitleExtractionEngine
    val confidenceScore: Float,

    // Epoch millis — when first scanned
    val scanTimestamp: Long,

    // Epoch millis — last time user viewed details
    val lastAccessed: Long
)