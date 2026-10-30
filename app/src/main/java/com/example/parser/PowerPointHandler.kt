package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PowerPointHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream: InputStream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to open presentation. Permission may have expired.", isAccessRevoked = true)
            }

            val rawBytes = stream.use { it.readBytes() }
            if (rawBytes.isEmpty()) {
                return@withContext DocumentContent.Error("The presentation file is empty.")
            }

            // Check if this is a modern ZIP archive (.pptx)
            val isZip = rawBytes.size >= 4 &&
                    rawBytes[0] == 0x50.toByte() &&
                    rawBytes[1] == 0x4B.toByte() &&
                    rawBytes[2] == 0x03.toByte() &&
                    rawBytes[3] == 0x04.toByte()

            if (isZip) {
                return@withContext parsePptx(rawBytes)
            } else {
                // Legacy binary .ppt format fallback
                return@withContext parseLegacyPpt(rawBytes)
            }
        } catch (e: Exception) {
            DocumentContent.Error("Error opening presentation: ${e.localizedMessage ?: "Unknown format"}")
        }
    }

    private fun parsePptx(rawBytes: ByteArray): DocumentContent {
        val slideEntries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(rawBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val lowerName = entryName.lowercase()
                    if (lowerName.contains("ppt/slides/slide") && lowerName.endsWith(".xml")) {
                        slideEntries[entryName] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return parseLegacyPpt(rawBytes)
        }

        if (slideEntries.isEmpty()) {
            // Attempt fallback extraction in case slides are in a non-standard subfolder
            return parseLegacyPpt(rawBytes)
        }

        // Natural sort slide1.xml, slide2.xml ... slide10.xml
        val sortedSlideNames = slideEntries.keys.sortedWith { a, b ->
            val numA = a.filter { it.isDigit() }.toIntOrNull() ?: 0
            val numB = b.filter { it.isDigit() }.toIntOrNull() ?: 0
            numA.compareTo(numB)
        }

        val slides = mutableListOf<SlideItem>()
        sortedSlideNames.forEachIndexed { index, name ->
            val slideItem = parseSlideXml(slideEntries[name], index + 1)
            slides.add(slideItem)
        }

        return DocumentContent.PowerPoint(slides = slides)
    }

    private fun parseSlideXml(bytes: ByteArray?, slideNum: Int): SlideItem {
        if (bytes == null) {
            return SlideItem(slideNum, "Slide $slideNum")
        }

        return try {
            val parser = XmlSanitizer.createParser(bytes)
            var eventType = parser.eventType
            val textBlocks = mutableListOf<String>()
            var currentPara = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val rawTag = parser.name
                val localTag = rawTag?.substringAfterLast(':')?.lowercase() ?: ""

                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (localTag == "p") {
                            currentPara = StringBuilder()
                        } else if (localTag == "t") {
                            currentPara.append(parser.nextText()).append(" ")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (localTag == "p") {
                            val text = currentPara.toString().trim()
                            if (text.isNotBlank()) {
                                textBlocks.add(text)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            val title = textBlocks.firstOrNull() ?: "Slide $slideNum"
            val subtitle = if (textBlocks.size > 1 && textBlocks[1].length < 100 && !textBlocks[1].startsWith("•")) {
                textBlocks[1]
            } else null

            val startIndex = if (subtitle != null) 2 else 1
            val bullets = if (textBlocks.size > startIndex) {
                textBlocks.subList(startIndex, textBlocks.size).map {
                    it.removePrefix("•").removePrefix("-").trim()
                }.filter { it.isNotBlank() }
            } else emptyList()

            SlideItem(
                slideNumber = slideNum,
                title = title,
                subtitle = subtitle,
                bulletPoints = bullets
            )
        } catch (e: Exception) {
            parseSlideFallback(bytes, slideNum)
        }
    }

    private fun parseSlideFallback(bytes: ByteArray, slideNum: Int): SlideItem {
        val raw = String(bytes, Charsets.UTF_8)
        val textBlocks = mutableListOf<String>()
        val pPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?p\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?p>", RegexOption.DOT_MATCHES_ALL)
        val tPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?t\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?t>", RegexOption.DOT_MATCHES_ALL)

        val pMatches = pPattern.findAll(raw).toList()
        if (pMatches.isNotEmpty()) {
            for (pMatch in pMatches) {
                val pContent = pMatch.groupValues[1]
                val tTexts = tPattern.findAll(pContent).map { XmlSanitizer.unescapeXml(it.groupValues[1].trim()) }.filter { it.isNotBlank() }.toList()
                if (tTexts.isNotEmpty()) {
                    textBlocks.add(tTexts.joinToString(" "))
                }
            }
        } else {
            val allT = tPattern.findAll(raw).map { XmlSanitizer.unescapeXml(it.groupValues[1].trim()) }.filter { it.isNotBlank() }.toList()
            textBlocks.addAll(allT)
        }

        val title = textBlocks.firstOrNull() ?: "Slide $slideNum"
        val subtitle = if (textBlocks.size > 1 && textBlocks[1].length < 100 && !textBlocks[1].startsWith("•")) {
            textBlocks[1]
        } else null

        val startIndex = if (subtitle != null) 2 else 1
        val bullets = if (textBlocks.size > startIndex) {
            textBlocks.subList(startIndex, textBlocks.size).map {
                it.removePrefix("•").removePrefix("-").trim()
            }.filter { it.isNotBlank() }
        } else emptyList()

        return SlideItem(
            slideNumber = slideNum,
            title = title,
            subtitle = subtitle,
            bulletPoints = bullets
        )
    }

    private fun parseLegacyPpt(rawBytes: ByteArray): DocumentContent {
        // Extract text runs from legacy binary PowerPoint compound format
        val extractedTexts = mutableListOf<String>()
        val minLength = 4

        // 1. Scan UTF-16LE strings (common in Office binary streams)
        var i = 0
        val sbUtf16 = StringBuilder()
        while (i < rawBytes.size - 1) {
            val b1 = rawBytes[i].toInt() and 0xFF
            val b2 = rawBytes[i + 1].toInt() and 0xFF
            if (b2 == 0 && (b1 in 32..126 || b1 in 160..255 || b1 == 10 || b1 == 13)) {
                sbUtf16.append(b1.toChar())
                i += 2
            } else {
                if (sbUtf16.length >= minLength) {
                    val str = sbUtf16.toString().trim()
                    if (str.isNotBlank() && !isGarbageString(str)) {
                        extractedTexts.add(str)
                    }
                }
                sbUtf16.clear()
                i += 2
            }
        }

        // 2. Scan ASCII strings if UTF-16LE yielded sparse results
        if (extractedTexts.size < 3) {
            val sbAscii = StringBuilder()
            for (b in rawBytes) {
                val v = b.toInt() and 0xFF
                if (v in 32..126) {
                    sbAscii.append(v.toChar())
                } else {
                    if (sbAscii.length >= minLength) {
                        val s = sbAscii.toString().trim()
                        if (s.isNotBlank() && !isGarbageString(s)) {
                            extractedTexts.add(s)
                        }
                    }
                    sbAscii.clear()
                }
            }
        }

        if (extractedTexts.isEmpty()) {
            return DocumentContent.PowerPoint(
                slides = listOf(
                    SlideItem(
                        slideNumber = 1,
                        title = "PowerPoint Presentation",
                        subtitle = "Legacy .ppt binary presentation",
                        bulletPoints = listOf(
                            "This document is stored in legacy binary (.ppt) format.",
                            "For full visual layout and slide graphics, convert to modern .pptx format."
                        )
                    )
                )
            )
        }

        // Group into slide chunks of 4-6 text items per slide
        val slides = mutableListOf<SlideItem>()
        val chunkSize = 5
        val chunks = extractedTexts.chunked(chunkSize)

        chunks.forEachIndexed { index, chunk ->
            val slideTitle = chunk.firstOrNull() ?: "Slide ${index + 1}"
            val subtitle = if (chunk.size > 1 && index == 0) chunk[1] else null
            val bullets = if (subtitle != null) chunk.drop(2) else chunk.drop(1)

            slides.add(
                SlideItem(
                    slideNumber = index + 1,
                    title = slideTitle,
                    subtitle = subtitle,
                    bulletPoints = bullets
                )
            )
        }

        return DocumentContent.PowerPoint(slides = slides)
    }

    private fun isGarbageString(s: String): Boolean {
        // Filter out binary signatures and system header strings
        val lower = s.lowercase()
        return lower.contains("font") && lower.contains("arial") ||
                lower.startsWith("microsoft") && lower.length < 20 ||
                lower.startsWith("powerpoint document") ||
                lower.startsWith("currentuser") ||
                lower.contains("xml") && lower.contains("xmlns")
    }
}
