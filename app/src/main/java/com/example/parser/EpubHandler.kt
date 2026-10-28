package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class EpubHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to open eBook. Permission may have expired.", isAccessRevoked = true)
            }

            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entries[entry.name] = zis.readBytes()
                    entry = zis.nextEntry
                }
            }

            val containerBytes = entries["META-INF/container.xml"]
                ?: return@withContext DocumentContent.Error("Invalid EPUB: missing META-INF/container.xml")

            val opfPath = parseContainerOpfPath(containerBytes)
            val opfBytes = entries[opfPath]
                ?: return@withContext DocumentContent.Error("Invalid EPUB: missing package manifest ($opfPath)")

            val basePath = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            val (title, author, spineHrefs) = parseOpf(opfBytes, basePath)

            val chapters = mutableListOf<EpubChapter>()
            spineHrefs.forEachIndexed { index, href ->
                val fullPath = basePath + href
                val chapterBytes = entries[fullPath] ?: entries[href]
                if (chapterBytes != null) {
                    val rawXhtml = String(chapterBytes, Charsets.UTF_8)
                    val (chapterTitle, cleanContent) = parseChapterXhtml(rawXhtml, index + 1)
                    chapters.add(
                        EpubChapter(
                            index = index,
                            title = chapterTitle,
                            content = cleanContent
                        )
                    )
                }
            }

            if (chapters.isEmpty()) {
                return@withContext DocumentContent.Error("No readable chapters found in EPUB file.")
            }

            DocumentContent.Epub(
                title = title ?: "eBook",
                author = author,
                chapters = chapters
            )
        } catch (e: Exception) {
            DocumentContent.Error("Error opening EPUB: ${e.localizedMessage ?: "Corrupted archive"}")
        }
    }

    private fun parseContainerOpfPath(bytes: ByteArray): String {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val path = parser.getAttributeValue(null, "full-path")
                if (!path.isNullOrBlank()) return path
            }
            eventType = parser.next()
        }
        return "OEBPS/content.opf"
    }

    private fun parseOpf(bytes: ByteArray, basePath: String): Triple<String?, String?, List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var title: String? = null
        var author: String? = null
        val manifest = mutableMapOf<String, String>() // id to href
        val spine = mutableListOf<String>() // idrefs

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "title", "dc:title" -> title = parser.nextText()
                        "creator", "dc:creator" -> author = parser.nextText()
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                manifest[id] = href
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) {
                                spine.add(idref)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val spineHrefs = spine.mapNotNull { manifest[it] }
        return Triple(title, author, spineHrefs)
    }

    private fun parseChapterXhtml(xhtml: String, chapterNumber: Int): Pair<String, String> {
        val lines = mutableListOf<String>()
        var chapterTitle: String? = null

        val parser = Xml.newPullParser()
        try {
            parser.setInput(StringReader(xhtml))
            var eventType = parser.eventType
            var inTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        inTag = parser.name.lowercase()
                        if (inTag in listOf("h1", "h2", "h3", "title") && chapterTitle == null) {
                            chapterTitle = parser.nextText().trim()
                            lines.add(chapterTitle)
                        } else if (inTag == "p") {
                            val text = parser.nextText().trim()
                            if (text.isNotBlank()) lines.add(text)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Regex fallback if XHTML is not strictly well-formed
            val clean = xhtml
                .replace(Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", RegexOption.IGNORE_CASE)) {
                    val t = it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    if (chapterTitle == null) chapterTitle = t
                    "\n\n$t\n\n"
                }
                .replace(Regex("<p[^>]*>(.*?)</p>", RegexOption.IGNORE_CASE)) {
                    val t = it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
                    "\n$t\n"
                }
                .replace(Regex("<[^>]*>"), "")
                .trim()
            return Pair(chapterTitle ?: "Chapter $chapterNumber", clean)
        }

        val content = lines.joinToString("\n\n")
        return Pair(chapterTitle ?: "Chapter $chapterNumber", content)
    }
}
