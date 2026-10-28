package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PowerPointHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to open presentation. Permission may have expired.", isAccessRevoked = true)
            }

            val slideEntries = mutableMapOf<String, ByteArray>()
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                        slideEntries[name] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }

            if (slideEntries.isEmpty()) {
                return@withContext DocumentContent.Error("No slides found in presentation archive.")
            }

            // Natural sort slide1.xml, slide2.xml ... slide10.xml
            val sortedSlideNames = slideEntries.keys.sortedWith(Comparator { a, b ->
                val numA = a.filter { it.isDigit() }.toIntOrNull() ?: 0
                val numB = b.filter { it.isDigit() }.toIntOrNull() ?: 0
                numA.compareTo(numB)
            })

            val slides = mutableListOf<SlideItem>()
            sortedSlideNames.forEachIndexed { index, name ->
                val slideItem = parseSlideXml(slideEntries[name], index + 1)
                slides.add(slideItem)
            }

            DocumentContent.PowerPoint(slides = slides)
        } catch (e: Exception) {
            DocumentContent.Error("Error parsing presentation: ${e.localizedMessage ?: "Invalid PPTX file"}")
        }
    }

    private fun parseSlideXml(bytes: ByteArray?, slideNum: Int): SlideItem {
        if (bytes == null) {
            return SlideItem(slideNum, "Slide $slideNum")
        }

        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        val textBlocks = mutableListOf<String>()
        var currentPara = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tag == "p") {
                        currentPara = StringBuilder()
                    } else if (tag == "t") {
                        currentPara.append(parser.nextText()).append(" ")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "p") {
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
        val subtitle = if (textBlocks.size > 1 && textBlocks[1].length < 80 && !textBlocks[1].startsWith("•")) {
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
}
