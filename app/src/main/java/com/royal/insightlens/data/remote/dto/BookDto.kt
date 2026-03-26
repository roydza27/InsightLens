package com.royal.insightlens.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── Root response ─────────────────────────────────────────────────────────

data class GoogleBooksResponse(
    @SerializedName("totalItems") val totalItems: Int = 0,
    @SerializedName("items")      val items: List<BookItemDto>? = null
)

// ─── Single book item ──────────────────────────────────────────────────────

data class BookItemDto(
    @SerializedName("id")          val id: String,
    @SerializedName("volumeInfo")  val volumeInfo: VolumeInfoDto,
    @SerializedName("saleInfo")    val saleInfo: SaleInfoDto? = null
)

// ─── Volume info ───────────────────────────────────────────────────────────

data class VolumeInfoDto(
    @SerializedName("title")               val title: String? = null,
    @SerializedName("authors")             val authors: List<String>? = null,
    @SerializedName("description")         val description: String? = null,
    @SerializedName("averageRating")       val averageRating: Float? = null,
    @SerializedName("ratingsCount")        val ratingsCount: Int? = null,
    @SerializedName("publisher")           val publisher: String? = null,
    @SerializedName("publishedDate")       val publishedDate: String? = null,
    @SerializedName("industryIdentifiers") val industryIdentifiers: List<IndustryIdentifierDto>? = null,
    @SerializedName("pageCount")           val pageCount: Int? = null,
    @SerializedName("categories")          val categories: List<String>? = null,
    @SerializedName("language")            val language: String? = null,
    @SerializedName("imageLinks")          val imageLinks: ImageLinksDto? = null,
    @SerializedName("canonicalVolumeLink") val canonicalVolumeLink: String? = null,
    @SerializedName("infoLink")            val infoLink: String? = null,
    @SerializedName("printType")           val printType: String? = null
)

// ─── ISBN identifiers ──────────────────────────────────────────────────────

data class IndustryIdentifierDto(
    @SerializedName("type")       val type: String? = null,   // "ISBN_13" or "ISBN_10"
    @SerializedName("identifier") val identifier: String? = null
)

// ─── Image links ───────────────────────────────────────────────────────────

data class ImageLinksDto(
    @SerializedName("smallThumbnail") val smallThumbnail: String? = null,
    @SerializedName("thumbnail")      val thumbnail: String? = null
)

// ─── Sale info ─────────────────────────────────────────────────────────────

data class SaleInfoDto(
    @SerializedName("buyLink") val buyLink: String? = null
)