package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream

class WordHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to read document. Access permission may have expired.", isAccessRevoked = true)
            }

            var documentXmlString: String? = null
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        documentXmlString = zis.bufferedReader(Charsets.UTF_8).readText()
                        break
                    }
                    entry = zis.nextEntry
                }
            }

            if (documentXmlString == null) {
                return@withContext DocumentContent.Error("Invalid Word document: missing word/document.xml")
            }

            val elements = parseDocumentXml(documentXmlString!!)
            val title = (elements.firstOrNull { it is WordElement.Heading } as? WordElement.Heading)?.text
                ?: (elements.firstOrNull { it is WordElement.Paragraph } as? WordElement.Paragraph)?.text
                ?: "Document"

            DocumentContent.Word(title = title, elements = elements)
        } catch (e: Exception) {
            DocumentContent.Error("Error parsing Word document: ${e.localizedMessage ?: "Corrupted OpenXML format"}")
        }
    }

    private fun parseDocumentXml(xml: String): List<WordElement> {
        return try {
            val elements = mutableListOf<WordElement>()
            val parser = XmlSanitizer.createParser(xml)

            var eventType = parser.eventType
        var inTable = false
        var currentTableHeaders = mutableListOf<String>()
        var currentTableRows = mutableListOf<List<String>>()
        var currentRowCells = mutableListOf<String>()
        var currentCellText = StringBuilder()

        var inParagraph = false
        var pStyle = ""
        var isBullet = false
        var paragraphRuns = mutableListOf<WordRun>()
        var currentRunText = StringBuilder()
        var runBold = false
        var runItalic = false
        var runUnderline = false
        var runColor: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfterLast(':')?.lowercase() ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "tbl" -> {
                            inTable = true
                            currentTableHeaders.clear()
                            currentTableRows.clear()
                        }
                        "tr" -> {
                            currentRowCells = mutableListOf()
                        }
                        "tc" -> {
                            currentCellText = StringBuilder()
                        }
                        "p" -> {
                            inParagraph = true
                            pStyle = ""
                            isBullet = false
                            paragraphRuns = mutableListOf()
                        }
                        "pstyle", "pStyle" -> {
                            val v = parser.getAttributeValue(null, "val")
                                ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                ?: ""
                            pStyle = v
                        }
                        "numpr", "numPr" -> {
                            isBullet = true
                        }
                        "r" -> {
                            currentRunText = StringBuilder()
                            runBold = false
                            runItalic = false
                            runUnderline = false
                            runColor = null
                        }
                        "b" -> runBold = true
                        "i" -> runItalic = true
                        "u" -> runUnderline = true
                        "color" -> runColor = parser.getAttributeValue(null, "val")
                        "t" -> {
                            val text = parser.nextText()
                            currentRunText.append(text)
                            if (inTable) {
                                currentCellText.append(text).append(" ")
                            }
                        }
                        "br" -> {
                            val type = parser.getAttributeValue(null, "type")
                            if (type == "page") {
                                if (inParagraph && paragraphRuns.isNotEmpty()) {
                                    val full = paragraphRuns.joinToString("") { it.text }
                                    elements.add(WordElement.Paragraph(full, paragraphRuns.toList()))
                                    paragraphRuns.clear()
                                }
                                elements.add(WordElement.Divider)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "r" -> {
                            if (currentRunText.isNotEmpty()) {
                                paragraphRuns.add(
                                    WordRun(
                                        text = currentRunText.toString(),
                                        isBold = runBold,
                                        isItalic = runItalic,
                                        isUnderline = runUnderline,
                                        hexColor = runColor
                                    )
                                )
                                currentRunText = StringBuilder()
                            }
                        }
                        "p" -> {
                            inParagraph = false
                            if (!inTable) {
                                val fullText = paragraphRuns.joinToString("") { it.text }.trim()
                                if (fullText.isNotEmpty()) {
                                    val lowerStyle = pStyle.lowercase()
                                    when {
                                        lowerStyle.contains("heading1") || lowerStyle.contains("title") -> {
                                            elements.add(WordElement.Heading(1, fullText))
                                        }
                                        lowerStyle.contains("heading2") -> {
                                            elements.add(WordElement.Heading(2, fullText))
                                        }
                                        lowerStyle.contains("heading3") -> {
                                            elements.add(WordElement.Heading(3, fullText))
                                        }
                                        isBullet || fullText.startsWith("•") || fullText.startsWith("-") -> {
                                            val cleanText = fullText.removePrefix("•").removePrefix("-").trim()
                                            elements.add(WordElement.BulletItem(cleanText))
                                        }
                                        else -> {
                                            elements.add(WordElement.Paragraph(fullText, paragraphRuns.toList()))
                                        }
                                    }
                                }
                            }
                        }
                        "tc" -> {
                            currentRowCells.add(currentCellText.toString().trim())
                        }
                        "tr" -> {
                            if (currentTableHeaders.isEmpty()) {
                                currentTableHeaders.addAll(currentRowCells)
                            } else {
                                currentTableRows.add(currentRowCells.toList())
                            }
                        }
                        "tbl" -> {
                            inTable = false
                            if (currentTableHeaders.isNotEmpty() || currentTableRows.isNotEmpty()) {
                                elements.add(WordElement.Table(currentTableHeaders.toList(), currentTableRows.toList()))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        elements
    } catch (e: Exception) {
        parseDocumentXmlFallback(xml)
    }
}

    private fun parseDocumentXmlFallback(xml: String): List<WordElement> {
        val elements = mutableListOf<WordElement>()
        val pPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?p\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?p>", RegexOption.DOT_MATCHES_ALL)
        val tPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?t\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?t>", RegexOption.DOT_MATCHES_ALL)
        val pStylePattern = Regex("<(?:[a-zA-Z0-9_-]+:)?pStyle\\b[^>]*\\bval=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

        for (pMatch in pPattern.findAll(xml)) {
            val pContent = pMatch.groupValues[1]
            val styleMatch = pStylePattern.find(pContent)?.groupValues?.get(1)?.lowercase() ?: ""
            val texts = tPattern.findAll(pContent).map { XmlSanitizer.unescapeXml(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
            val fullText = texts.joinToString("").trim()
            if (fullText.isNotBlank()) {
                when {
                    styleMatch.contains("heading1") || styleMatch.contains("title") -> {
                        elements.add(WordElement.Heading(1, fullText))
                    }
                    styleMatch.contains("heading2") -> {
                        elements.add(WordElement.Heading(2, fullText))
                    }
                    styleMatch.contains("heading3") -> {
                        elements.add(WordElement.Heading(3, fullText))
                    }
                    else -> {
                        elements.add(WordElement.Paragraph(fullText, listOf(WordRun(fullText))))
                    }
                }
            }
        }
        return elements
    }
}
