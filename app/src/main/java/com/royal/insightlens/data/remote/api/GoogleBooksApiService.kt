package com.royal.insightlens.data.remote.api

import com.royal.insightlens.data.remote.dto.GoogleBooksResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksApiService {

    /**
     * Search books by title query.
     *
     * Example:
     * GET https://www.googleapis.com/books/v1/volumes?q=Atomic+Habits&maxResults=5&orderBy=relevance
     */
    @GET("volumes")
    suspend fun searchBooks(
        @Query("q")          query: String,
        @Query("maxResults") maxResults: Int = 5,
        @Query("orderBy")    orderBy: String = "relevance",
        @Query("printType")  printType: String = "books"
    ): GoogleBooksResponse
}

// ─── Retrofit instance ─────────────────────────────────────────────────────

object RetrofitClient {

    private const val BASE_URL = "https://www.googleapis.com/books/v1/"

    val api: GoogleBooksApiService by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(
                retrofit2.converter.gson.GsonConverterFactory.create()
            )
            .build()
            .create(GoogleBooksApiService::class.java)
    }
}