package com.example.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class MarkdownHandler(private val context: Context) {

    suspend fun open(uri: Uri, isAsset: Boolean, assetPath: String?): DocumentContent = withContext(Dispatchers.IO) {
        try {
            val stream = if (isAsset && assetPath != null) {
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext DocumentContent.Error("Unable to open Markdown file. Permission may have expired.", isAccessRevoked = true)
            }

            val rawText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            val blocks = parseMarkdown(rawText)

            DocumentContent.Markdown(rawText = rawText, blocks = blocks)
        } catch (e: Exception) {
            DocumentContent.Error("Error reading Markdown: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun parseMarkdown(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = text.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.startsWith("```") -> {
                    val lang = trimmed.removePrefix("```").trim()
                    val codeBuilder = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeBuilder.append(lines[i]).append("\n")
                        i++
                    }
                    blocks.add(MarkdownBlock.CodeBlock(lang, codeBuilder.toString().trimEnd()))
                }
                trimmed.startsWith("### ") -> {
                    blocks.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                }
                trimmed.startsWith("## ") -> {
                    blocks.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim()))
                }
                trimmed.startsWith("# ") -> {
                    blocks.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim()))
                }
                trimmed.startsWith("> ") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quoteLines.add(lines[i].trim().removePrefix(">").trim())
                        i++
                    }
                    i-- // step back for loop increment
                    blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
                }
                trimmed.matches(Regex("^[-*_]{3,}$")) -> {
                    blocks.add(MarkdownBlock.HorizontalRule)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                        items.add(lines[i].trim().substring(2).trim())
                        i++
                    }
                    i--
                    blocks.add(MarkdownBlock.BulletList(items))
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val items = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s+.*"))) {
                        val itemText = lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s+"), "")
                        items.add(itemText)
                        i++
                    }
                    i--
                    blocks.add(MarkdownBlock.NumberedList(items))
                }
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    i--
                    val parsedTable = parseMarkdownTable(tableLines)
                    if (parsedTable != null) {
                        blocks.add(parsedTable)
                    }
                }
                trimmed.isNotBlank() -> {
                    val paraBuilder = StringBuilder(trimmed)
                    while (i + 1 < lines.size && lines[i + 1].trim().isNotBlank() &&
                        !lines[i + 1].trim().startsWith("#") &&
                        !lines[i + 1].trim().startsWith("```") &&
                        !lines[i + 1].trim().startsWith(">") &&
                        !lines[i + 1].trim().startsWith("- ") &&
                        !lines[i + 1].trim().startsWith("* ") &&
                        !lines[i + 1].trim().startsWith("|")
                    ) {
                        i++
                        paraBuilder.append(" ").append(lines[i].trim())
                    }
                    blocks.add(MarkdownBlock.Paragraph(paraBuilder.toString()))
                }
            }
            i++
        }

        return blocks
    }

    private fun parseMarkdownTable(lines: List<String>): MarkdownBlock.Table? {
        if (lines.size < 2) return null
        val headerLine = lines[0]
        val headers = headerLine.split("|").map { it.trim() }.filter { it.isNotEmpty() }

        val rows = mutableListOf<List<String>>()
        val startIdx = if (lines[1].contains("---")) 2 else 1
        for (idx in startIdx until lines.size) {
            val rowCells = lines[idx].split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (rowCells.isNotEmpty()) {
                rows.add(rowCells)
            }
        }
        return MarkdownBlock.Table(headers, rows)
    }
}
