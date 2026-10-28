package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentRef

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val formatName: String,
    val fileSizeBytes: Long,
    val lastOpenedTimestamp: Long,
    val lastReadPosition: Int = 0,
    val totalPages: Int = 0,
    val isFavorite: Boolean = false,
    val isAsset: Boolean = false,
    val assetPath: String? = null
) {
    fun toDomain(): DocumentRef {
        val format = try {
            DocumentFormat.valueOf(formatName)
        } catch (e: Exception) {
            DocumentFormat.fromFileNameOrMime(displayName, mimeType)
        }

        return DocumentRef(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            format = format,
            fileSizeBytes = fileSizeBytes,
            lastOpenedTimestamp = lastOpenedTimestamp,
            lastReadPosition = lastReadPosition,
            totalPages = totalPages,
            isFavorite = isFavorite,
            isAsset = isAsset,
            assetPath = assetPath
        )
    }

    companion object {
        fun fromDomain(doc: DocumentRef): RecentDocumentEntity {
            return RecentDocumentEntity(
                uri = doc.uri,
                displayName = doc.displayName,
                mimeType = doc.mimeType,
                formatName = doc.format.name,
                fileSizeBytes = doc.fileSizeBytes,
                lastOpenedTimestamp = doc.lastOpenedTimestamp,
                lastReadPosition = doc.lastReadPosition,
                totalPages = doc.totalPages,
                isFavorite = doc.isFavorite,
                isAsset = doc.isAsset,
                assetPath = doc.assetPath
            )
        }
    }
}
