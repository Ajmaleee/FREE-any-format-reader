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
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideSi = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name
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
        return list
    }

    private fun parseWorkbookSheets(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name")
                if (!name.isNullOrBlank()) {
                    list.add(name)
                }
            }
            eventType = parser.next()
        }
        return list
    }

    private fun parseWorksheet(bytes: ByteArray?, sharedStrings: List<String>, sheetName: String): ExcelSheet {
        if (bytes == null) {
            return ExcelSheet(sheetName, emptyList(), emptyList(), 0)
        }

        val rowsMap = mutableMapOf<Int, MutableMap<Int, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var eventType = parser.eventType
        var currentRowIndex = 0
        var currentCellRef = ""
        var currentCellType = ""
        var maxColIndex = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name
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
        val effectiveHeaders = if (headers.all { it.isBlank() }) {
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
