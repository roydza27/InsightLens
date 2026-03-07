package com.royal.insightlens.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.royal.insightlens.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // ─── INSERT ────────────────────────────────────────────────────────────

    // IGNORE conflict — prevents duplicate volumeId inserts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    // ─── UPDATE ────────────────────────────────────────────────────────────

    @Update
    suspend fun update(book: BookEntity)

    // Update lastAccessed when user opens book details
    @Query("UPDATE books SET lastAccessed = :timestamp WHERE volumeId = :volumeId")
    suspend fun updateLastAccessed(volumeId: String, timestamp: Long)

    // ─── QUERY — Cache-First Strategy ──────────────────────────────────────

    // Used by Repository to check cache before API call
    @Query("SELECT * FROM books WHERE normalizedTitle = :normalizedTitle LIMIT 1")
    suspend fun findByNormalizedTitle(normalizedTitle: String): BookEntity?

    // Used by Repository to check by volumeId
    @Query("SELECT * FROM books WHERE volumeId = :volumeId LIMIT 1")
    suspend fun findByVolumeId(volumeId: String): BookEntity?

    // ─── OBSERVE — UI observes these as SSOT ───────────────────────────────

    // History screen — sorted by scan time descending
    @Query("SELECT * FROM books ORDER BY scanTimestamp DESC")
    fun observeAllSortedByTimestamp(): Flow<List<BookEntity>>

    // Home screen — recently scanned (latest 10)
    @Query("SELECT * FROM books ORDER BY scanTimestamp DESC LIMIT 10")
    fun observeRecentScans(): Flow<List<BookEntity>>

    // Book details screen — observe single book
    @Query("SELECT * FROM books WHERE volumeId = :volumeId LIMIT 1")
    fun observeByVolumeId(volumeId: String): Flow<BookEntity?>

    // History tab filters
    @Query("SELECT * FROM books WHERE categories LIKE '%book%' OR categories IS NULL ORDER BY scanTimestamp DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE categories LIKE '%article%' ORDER BY scanTimestamp DESC")
    fun observeArticles(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE categories LIKE '%document%' ORDER BY scanTimestamp DESC")
    fun observeDocuments(): Flow<List<BookEntity>>

    // ─── DELETE ────────────────────────────────────────────────────────────

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    // ─── COUNT ─────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int
}