package com.example.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class CsvHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to open CSV file. Permission may have expired.", isAccessRevoked = true)
            }

            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            val lines = mutableListOf<String>()
            var line = reader.readLine()
            while (line != null && lines.size < 5000) { // Safety ceiling for smooth mobile display
                lines.add(line)
                line = reader.readLine()
            }
            reader.close()

            if (lines.isEmpty()) {
                return@withContext DocumentContent.Csv(',', emptyList(), emptyList())
            }

            // Auto-detect delimiter from first line
            val firstLine = lines.first()
            val delimiter = detectDelimiter(firstLine)

            val parsedRows = parseCsvLines(lines, delimiter)
            val headers = parsedRows.firstOrNull() ?: emptyList()
            val rows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()

            DocumentContent.Csv(
                delimiter = delimiter,
                headers = headers,
                rows = rows
            )
        } catch (e: Exception) {
            DocumentContent.Error("Error parsing CSV: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun detectDelimiter(sample: String): Char {
        val commaCount = sample.count { it == ',' }
        val tabCount = sample.count { it == '\t' }
        val semiCount = sample.count { it == ';' }

        return when {
            tabCount > commaCount && tabCount > semiCount -> '\t'
            semiCount > commaCount && semiCount > tabCount -> ';'
            else -> ','
        }
    }

    private fun parseCsvLines(lines: List<String>, delimiter: Char): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var inQuotes = false
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()

        for (l in lines) {
            var i = 0
            while (i < l.length) {
                val c = l[i]
                if (c == '\"') {
                    if (inQuotes && i + 1 < l.length && l[i + 1] == '\"') {
                        currentField.append('\"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                } else if (c == delimiter && !inQuotes) {
                    currentRow.add(currentField.toString().trim())
                    currentField.clear()
                } else {
                    currentField.append(c)
                }
                i++
            }

            if (!inQuotes) {
                currentRow.add(currentField.toString().trim())
                currentField.clear()
                result.add(currentRow.toList())
                currentRow.clear()
            } else {
                currentField.append("\n")
            }
        }

        if (currentRow.isNotEmpty() || currentField.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            result.add(currentRow.toList())
        }

        return result
    }
}
