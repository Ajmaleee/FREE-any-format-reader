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

    private val bitmapCache = object : LruCache<Int, Bitmap>(15) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC handle bitmap memory or keep active
        }
    }

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
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
                // content:// scheme. PdfRenderer requires a seekable file descriptor.
                // Many content providers return non-seekable pipes, so copy to temp cache file.
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Cannot access document. Permission may have expired.", isAccessRevoked = true)
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
                val width = (page.width * scale).toInt().coerceAtLeast(300)
                val height = (page.height * scale).toInt().coerceAtLeast(400)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fill with white background
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
