package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PdfHandler(private val context: Context) {

    // Dynamic memory-based LRU cache (approx 12-24MB depending on device memory)
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSizeKb = (maxMemoryKb / 8).coerceIn(8 * 1024, 24 * 1024)

    private val bitmapCache = object : LruCache<Int, Bitmap>(cacheSizeKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }

        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Memory freed automatically; recycle if strictly evicted
            if (evicted && oldValue != null && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        // Clear prior bitmap cache when a new document is opened
        clearCache()

        try {
            var tempFile: File? = null
            val pfd: ParcelFileDescriptor

            if (isAsset && assetPath != null) {
                tempFile = copyStreamToTemp(context.assets.open(assetPath), "pdf_asset")
                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } else if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists() || !file.canRead()) {
                    return@withContext DocumentContent.Error("File not found or unreadable: ${file.name}")
                }
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                // Content URI (from Downloads, Files app, SAF, or file manager)
                // Use ContentResolver without assuming filesystem path
                val stream = try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: SecurityException) {
                    return@withContext DocumentContent.Error("Access to this document was revoked by Android.", isAccessRevoked = true)
                } ?: return@withContext DocumentContent.Error("Cannot read document. Permission may have expired or file was moved.", isAccessRevoked = true)

                // Android PdfRenderer requires a seekable file descriptor. Copy safely to app cache:
                tempFile = copyStreamToTemp(stream, "pdf_doc")
                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            val renderer = PdfRenderer(pfd)
            DocumentContent.Pdf(
                renderer = renderer,
                pfd = pfd,
                pageCount = renderer.pageCount,
                tempFile = tempFile
            )
        } catch (e: SecurityException) {
            DocumentContent.Error("Access to this document was revoked by Android.", isAccessRevoked = true)
        } catch (e: Exception) {
            DocumentContent.Error("Unable to open PDF: ${e.localizedMessage ?: "Invalid or encrypted format"}")
        }
    }

    suspend fun renderPage(
        pdf: DocumentContent.Pdf,
        pageIndex: Int,
        scale: Float = 2.0f
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= pdf.pageCount) return@withContext null

        synchronized(bitmapCache) {
            val cached = bitmapCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        try {
            synchronized(pdf.renderer) {
                val page = pdf.renderer.openPage(pageIndex)

                // Cap maximum rendering dimension to 2048px to prevent OOM on enormous vector blueprints
                val baseW = page.width
                val baseH = page.height
                val maxDim = 2048
                val effectiveScale = minOf(scale, maxDim.toFloat() / maxOf(baseW, baseH).coerceAtLeast(1).toFloat())

                val width = (baseW * effectiveScale).toInt().coerceAtLeast(200)
                val height = (baseH * effectiveScale).toInt().coerceAtLeast(200)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Retain natural page white background by default to preserve diagram/photo contrast
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                synchronized(bitmapCache) {
                    bitmapCache.put(pageIndex, bitmap)
                }
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.evictAll()
        }
    }

    private fun copyStreamToTemp(input: InputStream, prefix: String): File {
        val tempFile = File.createTempFile(prefix, ".pdf", context.cacheDir)
        input.use { inStream ->
            FileOutputStream(tempFile).use { outStream ->
                inStream.copyTo(outStream)
            }
        }
        return tempFile
    }
}
