package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.db.RecentDocumentEntity
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val dao = database.recentDocumentDao()

    val recentDocuments: Flow<List<DocumentRef>> = dao.getAllRecentDocuments().map { entities ->
        entities.map { it.toDomain() }
    }

    val favoriteDocuments: Flow<List<DocumentRef>> = dao.getFavoriteDocuments().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getDocument(uriString: String): DocumentRef? = withContext(Dispatchers.IO) {
        dao.getDocumentByUri(uriString)?.toDomain()
    }

    suspend fun recordDocumentOpened(
        uri: Uri,
        isAsset: Boolean = false,
        assetPath: String? = null,
        mimeTypeHint: String? = null
    ): DocumentRef = withContext(Dispatchers.IO) {
        val uriString = uri.toString()

        // Attempt to persist SAF URI permission if it's a content URI
        if (!isAsset && uri.scheme == "content") {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.w("DocumentRepository", "Could not persist URI permission for $uri: ${e.message}")
            }
        }

        // Query metadata
        val metadata = extractMetadata(uri, isAsset, assetPath, mimeTypeHint)
        val existing = dao.getDocumentByUri(uriString)

        val entity = RecentDocumentEntity(
            uri = uriString,
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            formatName = metadata.format.name,
            fileSizeBytes = metadata.fileSizeBytes,
            lastOpenedTimestamp = System.currentTimeMillis(),
            lastReadPosition = existing?.lastReadPosition ?: 0,
            totalPages = existing?.totalPages ?: 0,
            isFavorite = existing?.isFavorite ?: false,
            isAsset = isAsset,
            assetPath = assetPath
        )

        dao.insertOrUpdate(entity)
        entity.toDomain()
    }

    suspend fun updateReadingProgress(uriString: String, position: Int, total: Int) = withContext(Dispatchers.IO) {
        dao.updateReadingProgress(uriString, position, total, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(uriString: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(uriString, isFavorite)
    }

    suspend fun removeFromHistory(uriString: String) = withContext(Dispatchers.IO) {
        dao.deleteByUri(uriString)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    fun isFileAccessible(doc: DocumentRef): Boolean {
        if (doc.isAsset) return true
        val uri = Uri.parse(doc.uri)
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                file.exists() && file.canRead()
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractMetadata(
        uri: Uri,
        isAsset: Boolean,
        assetPath: String?,
        mimeTypeHint: String? = null
    ): DocumentRef {
        var name = "Document"
        var size: Long = 0
        var mime = context.contentResolver.getType(uri) ?: ""
        if (mime.isBlank() || mime == "application/octet-stream") {
            if (!mimeTypeHint.isNullOrBlank()) {
                mime = mimeTypeHint
            }
        }

        if (isAsset && assetPath != null) {
            name = assetPath.substringAfterLast('/')
            try {
                context.assets.open(assetPath).use { stream ->
                    size = stream.available().toLong()
                }
            } catch (e: Exception) {
                size = 0
            }
        } else if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: name
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DocumentRepository", "Metadata query failed: ${e.message}")
            }

            // Fallback for file size via file descriptor if provider did not report size
            if (size <= 0) {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        if (pfd.statSize > 0) size = pfd.statSize
                    }
                } catch (e: Exception) {}
            }

            // Fallback for filename from URI path segment
            if (name == "Document") {
                val seg = uri.lastPathSegment?.substringAfterLast('/')
                if (!seg.isNullOrBlank()) {
                    name = seg
                } else {
                    // Try extension from mime
                    val formatGuess = DocumentFormat.fromFileNameOrMime(name, mime)
                    name = when (formatGuess) {
                        DocumentFormat.POWERPOINT -> "Presentation.pptx"
                        DocumentFormat.WORD -> "Document.docx"
                        DocumentFormat.EXCEL -> "Spreadsheet.xlsx"
                        DocumentFormat.PDF -> "Document.pdf"
                        DocumentFormat.EPUB -> "Book.epub"
                        DocumentFormat.MARKDOWN -> "Document.md"
                        DocumentFormat.CSV -> "Data.csv"
                        DocumentFormat.TEXT -> "Document.txt"
                        DocumentFormat.JSON -> "Document.json"
                        DocumentFormat.XML -> "Document.xml"
                        DocumentFormat.IMAGE -> "Image.png"
                        else -> "Document"
                    }
                }
            }
        } else if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                name = file.name
                size = file.length()
            }
        }

        val format = DocumentFormat.fromFileNameOrMime(name, mime)
        return DocumentRef(
            uri = uri.toString(),
            displayName = name,
            mimeType = mime,
            format = format,
            fileSizeBytes = size,
            lastOpenedTimestamp = System.currentTimeMillis(),
            isAsset = isAsset,
            assetPath = assetPath
        )
    }

    suspend fun seedSampleDocumentsIfFirstRun() = withContext(Dispatchers.IO) {
        val count = dao.getAllRecentDocuments()
        // If user already has recent documents, do not re-seed
        val existing = dao.getDocumentByUri("asset://samples/executive_briefing.pdf")
        if (existing != null) return@withContext

        val samples = listOf(
            SampleSeed("samples/executive_briefing.pdf", "Executive Briefing.pdf", "application/pdf", DocumentFormat.PDF),
            SampleSeed("samples/annual_strategy_report.docx", "Annual Strategy Report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentFormat.WORD),
            SampleSeed("samples/financial_model_q3.xlsx", "Financial Model Q3.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DocumentFormat.EXCEL),
            SampleSeed("samples/product_design_systems.pptx", "Product Design Systems.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", DocumentFormat.POWERPOINT),
            SampleSeed("samples/quarterly_transactions.csv", "Quarterly Transactions.csv", "text/csv", DocumentFormat.CSV),
            SampleSeed("samples/technical_specification.md", "Technical Specification.md", "text/markdown", DocumentFormat.MARKDOWN),
            SampleSeed("samples/system_configuration.json", "System Configuration.json", "application/json", DocumentFormat.JSON),
            SampleSeed("samples/application_spec.xml", "Application Spec.xml", "application/xml", DocumentFormat.XML),
            SampleSeed("samples/the_odyssey_sample.epub", "The Odyssey (Selection).epub", "application/epub+zip", DocumentFormat.EPUB)
        )

        var timeOffset = 900000L
        for (sample in samples) {
            try {
                val assetUri = "asset://${sample.path}"
                var size = 0L
                context.assets.open(sample.path).use { size = it.available().toLong() }
                val entity = RecentDocumentEntity(
                    uri = assetUri,
                    displayName = sample.name,
                    mimeType = sample.mime,
                    formatName = sample.format.name,
                    fileSizeBytes = size,
                    lastOpenedTimestamp = System.currentTimeMillis() - timeOffset,
                    lastReadPosition = 0,
                    totalPages = 0,
                    isFavorite = sample.format in listOf(DocumentFormat.PDF, DocumentFormat.WORD, DocumentFormat.EXCEL),
                    isAsset = true,
                    assetPath = sample.path
                )
                dao.insertOrUpdate(entity)
                timeOffset += 1200000L
            } catch (e: Exception) {
                Log.w("DocumentRepository", "Could not seed sample ${sample.path}: ${e.message}")
            }
        }
    }

    private data class SampleSeed(
        val path: String,
        val name: String,
        val mime: String,
        val format: DocumentFormat
    )
}
