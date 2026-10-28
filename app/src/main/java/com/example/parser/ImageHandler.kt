package com.example.parser

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ImageHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val effectiveUri: Uri = if (isAsset && assetPath != null) {
                // Copy asset image to cache file for universal Uri rendering
                val tempFile = File.createTempFile("asset_img_", ".png", context.cacheDir)
                context.assets.open(assetPath).use { inStream ->
                    FileOutputStream(tempFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                Uri.fromFile(tempFile)
            } else {
                uri
            }

            // Decode dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            val stream = context.contentResolver.openInputStream(effectiveUri)
                ?: return@withContext DocumentContent.Error("Unable to open image file.", isAccessRevoked = true)
            stream.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val mime = options.outMimeType ?: "image/jpeg"
            val width = options.outWidth
            val height = options.outHeight

            DocumentContent.ImageDoc(
                uri = effectiveUri,
                width = width,
                height = height,
                mimeType = mime
            )
        } catch (e: Exception) {
            DocumentContent.Error("Error opening image: ${e.localizedMessage}")
        }
    }
}
