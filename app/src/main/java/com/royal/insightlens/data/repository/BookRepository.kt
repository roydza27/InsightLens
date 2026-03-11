package com.royal.insightlens.data.repository

import com.royal.insightlens.data.local.dao.BookDao
import com.royal.insightlens.data.local.entity.BookEntity
import com.royal.insightlens.data.remote.api.RetrofitClient
import com.royal.insightlens.data.remote.mapper.BookMapper
import kotlinx.coroutines.flow.Flow

/**
 * BookRepository
 *
 * Implements strict cache-first strategy as defined in SRS §3.5:
 *
 * 1. Normalize title
 * 2. Query Room DB
 * 3. If exists → return cached via DB observation
 * 4. If not → call API
 * 5. Rank API results
 * 6. Map DTO → Entity safely
 * 7. Persist to DB
 * 8. UI auto-updates via observation
 *
 * Repository NEVER directly returns network results to UI.
 */
class BookRepository(private val bookDao: BookDao) {

    // ─── Cache-First Fetch ─────────────────────────────────────────────────

    /**
     * Main entry point called by ViewModel after OCR extraction.
     *
     * Returns a Result wrapping the volumeId of the found/inserted book,
     * or a failure with the error.
     */


    suspend fun fetchBook(
        normalizedTitle: String,
        rawTitle: String,
        confidenceScore: Float
    ): Result<String> {
        return try {

            // Step 1: Check Room cache
            val cached = bookDao.findByNormalizedTitle(normalizedTitle)

            if (cached != null) {
                // Cache hit — update lastAccessed and return volumeId
                bookDao.updateLastAccessed(
                    volumeId  = cached.volumeId,
                    timestamp = System.currentTimeMillis()
                )
                return Result.success(cached.volumeId)
            }

            // Step 2: Cache miss — call Google Books API
            val response = RetrofitClient.api.searchBooks(
                query      = rawTitle,
                maxResults = 5
            )

            val items = response.items
            if (items.isNullOrEmpty()) {
                return Result.failure(Exception("No results found for: $rawTitle"))
            }

            // Step 3: Rank results and pick best match
            val bestMatch = BookMapper.rankAndPickBest(
                items      = items,
                queryTitle = rawTitle
            ) ?: return Result.failure(Exception("Could not rank results"))

            // Step 4: Map DTO → Entity safely
            val entity = BookMapper.toEntity(
                dto             = bestMatch,
                normalizedTitle = normalizedTitle,
                confidenceScore = confidenceScore
            )

            // Step 5: Persist to Room — UI observes automatically
            val insertedId = bookDao.insert(entity)

            if (insertedId == -1L) {
                // IGNORE conflict — already exists, find by volumeId
                val existing = bookDao.findByVolumeId(entity.volumeId)
                if (existing != null) {
                    bookDao.updateLastAccessed(
                        volumeId  = existing.volumeId,
                        timestamp = System.currentTimeMillis()
                    )
                    return Result.success(existing.volumeId)
                }
            }

            Result.success(entity.volumeId)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Observe (SSOT) ────────────────────────────────────────────────────




    // History screen
    fun observeAllBooks(): Flow<List<BookEntity>> =
        bookDao.observeAllSortedByTimestamp()

    // Home screen recent scans
    fun observeRecentScans(): Flow<List<BookEntity>> =
        bookDao.observeRecentScans()

    // Book details
    fun observeBook(volumeId: String): Flow<BookEntity?> =
        bookDao.observeByVolumeId(volumeId)

    // History tab filters
    fun observeBooksByCategory(category: String): Flow<List<BookEntity>> =
        when (category) {
            "books"     -> bookDao.observeBooks()
            "articles"  -> bookDao.observeArticles()
            "documents" -> bookDao.observeDocuments()
            else        -> bookDao.observeAllSortedByTimestamp()
        }

    // ─── Delete ────────────────────────────────────────────────────────────

    suspend fun deleteBook(id: Long) = bookDao.deleteById(id)

    suspend fun deleteAll() = bookDao.deleteAll()
}