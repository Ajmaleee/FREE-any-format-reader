package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DocumentRef(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val format: DocumentFormat,
    val fileSizeBytes: Long,
    val lastOpenedTimestamp: Long,
    val lastReadPosition: Int = 0,
    val totalPages: Int = 0,
    val isFavorite: Boolean = false,
    val isAsset: Boolean = false,
    val assetPath: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(fileSizeBytes)

    val formattedTime: String
        get() = formatTimestamp(lastOpenedTimestamp)

    val progressDescription: String?
        get() {
            if (totalPages <= 0) return null
            val current = (lastReadPosition + 1).coerceAtMost(totalPages)
            return when (format) {
                DocumentFormat.PDF -> "Page $current of $totalPages"
                DocumentFormat.POWERPOINT -> "Slide $current of $totalPages"
                DocumentFormat.EPUB -> "Chapter $current of $totalPages"
                DocumentFormat.EXCEL -> "Sheet $current of $totalPages"
                else -> null
            }
        }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "Unknown size"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }

        fun formatTimestamp(timestamp: Long): String {
            if (timestamp <= 0) return "Never opened"
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val oneDay = 24 * 60 * 60 * 1000L

            return when {
                diff < 60 * 1000L -> "Just now"
                diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)}m ago"
                diff < oneDay -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
                diff < 2 * oneDay -> "Yesterday"
                else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}
