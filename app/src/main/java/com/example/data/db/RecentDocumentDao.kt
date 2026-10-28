package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {

    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecentDocuments(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavoriteDocuments(): Flow<List<RecentDocumentEntity>>

    @Query("SELECT * FROM recent_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): RecentDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(document: RecentDocumentEntity)

    @Query("DELETE FROM recent_documents WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("UPDATE recent_documents SET lastReadPosition = :position, totalPages = :total, lastOpenedTimestamp = :timestamp WHERE uri = :uri")
    suspend fun updateReadingProgress(uri: String, position: Int, total: Int, timestamp: Long)

    @Query("UPDATE recent_documents SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("DELETE FROM recent_documents")
    suspend fun clearAll()
}
