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

class ExcelHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to open spreadsheet. Permission may have expired.", isAccessRevoked = true)
            }

            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(stream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.startsWith("xl/worksheets/sheet") ||
                        name == "xl/workbook.xml" ||
                        name == "xl/sharedStrings.xml"
                    ) {
                        entries[name] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }

            val sharedStrings = parseSharedStrings(entries["xl/sharedStrings.xml"])
            val sheetNames = parseWorkbookSheets(entries["xl/workbook.xml"])

            val parsedSheets = mutableListOf<ExcelSheet>()
            val sheetFiles = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.sorted()

            sheetFiles.forEachIndexed { index, sheetPath ->
                val sheetName = sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"
                val sheetData = parseWorksheet(entries[sheetPath], sharedStrings, sheetName)
                parsedSheets.add(sheetData)
            }

            if (parsedSheets.isEmpty()) {
                return@withContext DocumentContent.Error("Empty spreadsheet or unsupported sheet structure.")
            }

            DocumentContent.Excel(sheets = parsedSheets)
        } catch (e: Exception) {
            DocumentContent.Error("Error parsing spreadsheet: ${e.localizedMessage ?: "Corrupted XLSX archive"}")
        }
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        return try {
            val list = mutableListOf<String>()
            val parser = XmlSanitizer.createParser(bytes)

            var eventType = parser.eventType
            var currentText = StringBuilder()
            var insideSi = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.substringAfterLast(':')?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "si") {
                            insideSi = true
                            currentText = StringBuilder()
                        } else if (tag == "t" && insideSi) {
                            currentText.append(parser.nextText())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "si") {
                            list.add(currentText.toString())
                            insideSi = false
                        }
                    }
                }
                eventType = parser.next()
            }
            list
        } catch (e: Exception) {
            parseSharedStringsFallback(bytes)
        }
    }

    private fun parseSharedStringsFallback(bytes: ByteArray): List<String> {
        val raw = String(bytes, Charsets.UTF_8)
        val list = mutableListOf<String>()
        val siPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?si\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?si>", RegexOption.DOT_MATCHES_ALL)
        val tPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?t\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?t>", RegexOption.DOT_MATCHES_ALL)

        for (siMatch in siPattern.findAll(raw)) {
            val siContent = siMatch.groupValues[1]
            val textParts = tPattern.findAll(siContent).map { XmlSanitizer.unescapeXml(it.groupValues[1]) }
            list.add(textParts.joinToString(""))
        }
        return list
    }

    private fun parseWorkbookSheets(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        return try {
            val list = mutableListOf<String>()
            val parser = XmlSanitizer.createParser(bytes)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val localTag = parser.name?.substringAfterLast(':')?.lowercase() ?: ""
                if (eventType == XmlPullParser.START_TAG && localTag == "sheet") {
                    val name = parser.getAttributeValue(null, "name")
                    if (!name.isNullOrBlank()) {
                        list.add(name)
                    }
                }
                eventType = parser.next()
            }
            list
        } catch (e: Exception) {
            parseWorkbookSheetsFallback(bytes)
        }
    }

    private fun parseWorkbookSheetsFallback(bytes: ByteArray): List<String> {
        val raw = String(bytes, Charsets.UTF_8)
        val sheetPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?sheet\\b[^>]*\\bname=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        return sheetPattern.findAll(raw).map { XmlSanitizer.unescapeXml(it.groupValues[1]) }.toList()
    }

    private fun parseWorksheet(bytes: ByteArray?, sharedStrings: List<String>, sheetName: String): ExcelSheet {
        if (bytes == null) {
            return ExcelSheet(sheetName, emptyList(), emptyList(), 0)
        }

        return try {
            val rowsMap = mutableMapOf<Int, MutableMap<Int, String>>()
            val parser = XmlSanitizer.createParser(bytes)

            var eventType = parser.eventType
            var currentRowIndex = 0
            var currentCellRef = ""
            var currentCellType = ""
            var maxColIndex = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.substringAfterLast(':')?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "row" -> {
                                val r = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (currentRowIndex + 1)
                                currentRowIndex = r
                                if (!rowsMap.containsKey(currentRowIndex)) {
                                    rowsMap[currentRowIndex] = mutableMapOf()
                                }
                            }
                            "c" -> {
                                currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            }
                            "v" -> {
                                val rawValue = parser.nextText()
                                val displayValue = if (currentCellType == "s") {
                                    val sIndex = rawValue.toIntOrNull() ?: -1
                                    sharedStrings.getOrNull(sIndex) ?: rawValue
                                } else {
                                    rawValue
                                }

                                val colIndex = parseColumnIndex(currentCellRef)
                                if (colIndex > maxColIndex) maxColIndex = colIndex
                                rowsMap.getOrPut(currentRowIndex) { mutableMapOf() }[colIndex] = displayValue
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            buildSheetFromRowsMap(rowsMap, maxColIndex, sheetName)
        } catch (e: Exception) {
            parseWorksheetFallback(bytes, sharedStrings, sheetName)
        }
    }

    private fun buildSheetFromRowsMap(rowsMap: Map<Int, Map<Int, String>>, maxColIndex: Int, sheetName: String): ExcelSheet {
        val sortedRowIndices = rowsMap.keys.sorted()
        val headers = mutableListOf<String>()
        val contentRows = mutableListOf<List<String>>()

        val totalCols = (maxColIndex + 1).coerceAtLeast(1)

        sortedRowIndices.forEachIndexed { index, rowIndex ->
            val rowData = rowsMap[rowIndex] ?: emptyMap()
            val rowList = (0 until totalCols).map { col -> rowData[col] ?: "" }

            if (index == 0) {
                headers.addAll(rowList)
            } else {
                contentRows.add(rowList)
            }
        }

        // If headers are all blank, generate A, B, C...
        val effectiveHeaders = if (headers.isEmpty() || headers.all { it.isBlank() }) {
            (0 until totalCols).map { indexToColumnName(it) }
        } else {
            headers.mapIndexed { idx, h -> if (h.isBlank()) indexToColumnName(idx) else h }
        }

        return ExcelSheet(
            name = sheetName,
            headers = effectiveHeaders,
            rows = contentRows,
            totalRowCount = rowsMap.size
        )
    }

    private fun parseWorksheetFallback(bytes: ByteArray, sharedStrings: List<String>, sheetName: String): ExcelSheet {
        val raw = String(bytes, Charsets.UTF_8)
        val rowsMap = mutableMapOf<Int, MutableMap<Int, String>>()
        var maxColIndex = 0

        val rowPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?row\\b(?:[^>]*?\\br=\"(\\d+)\")?[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?row>", RegexOption.DOT_MATCHES_ALL)
        val cellPattern = Regex("<(?:[a-zA-Z0-9_-]+:)?c\\b(?:[^>]*?\\br=\"([A-Za-z0-9]+)\")?(?:[^>]*?\\bt=\"([^\"]*)\")?[^>]*>.*?<(?:[a-zA-Z0-9_-]+:)?v>(.*?)</(?:[a-zA-Z0-9_-]+:)?v>", RegexOption.DOT_MATCHES_ALL)

        var fallbackRowIdx = 1
        for (rowMatch in rowPattern.findAll(raw)) {
            val rAttr = rowMatch.groupValues[1].toIntOrNull() ?: fallbackRowIdx
            val rowContent = rowMatch.groupValues[2]

            for (cellMatch in cellPattern.findAll(rowContent)) {
                val cellRef = cellMatch.groupValues[1]
                val cellType = cellMatch.groupValues[2]
                val rawVal = cellMatch.groupValues[3].trim()

                val displayVal = if (cellType == "s") {
                    val sIdx = rawVal.toIntOrNull() ?: -1
                    sharedStrings.getOrNull(sIdx) ?: rawVal
                } else {
                    XmlSanitizer.unescapeXml(rawVal)
                }

                val colIndex = parseColumnIndex(cellRef)
                if (colIndex > maxColIndex) maxColIndex = colIndex
                rowsMap.getOrPut(rAttr) { mutableMapOf() }[colIndex] = displayVal
            }
            fallbackRowIdx = rAttr + 1
        }

        return buildSheetFromRowsMap(rowsMap, maxColIndex, sheetName)
    }

    private fun parseColumnIndex(cellRef: String): Int {
        val colLetters = cellRef.filter { it.isLetter() }.uppercase()
        if (colLetters.isEmpty()) return 0
        var col = 0
        for (char in colLetters) {
            col = col * 26 + (char - 'A' + 1)
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun indexToColumnName(index: Int): String {
        var num = index + 1
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.code + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }
}
