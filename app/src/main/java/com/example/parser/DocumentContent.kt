package com.example.parser

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

sealed interface DocumentContent {
    data class Pdf(
        val renderer: PdfRenderer,
        val pfd: ParcelFileDescriptor,
        val pageCount: Int,
        val tempFile: File? = null
    ) : DocumentContent {
        fun close() {
            try {
                renderer.close()
            } catch (e: Exception) {}
            try {
                pfd.close()
            } catch (e: Exception) {}
            tempFile?.delete()
        }
    }

    data class Word(
        val title: String,
        val elements: List<WordElement>
    ) : DocumentContent

    data class Excel(
        val sheets: List<ExcelSheet>
    ) : DocumentContent

    data class PowerPoint(
        val slides: List<SlideItem>
    ) : DocumentContent

    data class Csv(
        val delimiter: Char,
        val headers: List<String>,
        val rows: List<List<String>>
    ) : DocumentContent

    data class Markdown(
        val rawText: String,
        val blocks: List<MarkdownBlock>
    ) : DocumentContent

    data class Text(
        val lines: List<String>,
        val totalLines: Int,
        val rawText: String
    ) : DocumentContent

    data class StructuredText(
        val formattedText: String,
        val rawText: String,
        val isJson: Boolean,
        val isValid: Boolean,
        val errorHint: String? = null
    ) : DocumentContent

    data class ImageDoc(
        val uri: Uri,
        val width: Int,
        val height: Int,
        val mimeType: String
    ) : DocumentContent

    data class Epub(
        val title: String,
        val author: String?,
        val chapters: List<EpubChapter>
    ) : DocumentContent

    data class Error(
        val message: String,
        val isAccessRevoked: Boolean = false,
        val details: String? = null
    ) : DocumentContent
}

// Word models
sealed interface WordElement {
    data class Heading(val level: Int, val text: String) : WordElement
    data class Paragraph(val text: String, val runs: List<WordRun> = emptyList()) : WordElement
    data class BulletItem(val text: String, val level: Int = 0) : WordElement
    data class Table(val headers: List<String>, val rows: List<List<String>>) : WordElement
    object Divider : WordElement
}

data class WordRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val hexColor: String? = null
)

// Excel models
data class ExcelSheet(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val totalRowCount: Int
)

// PowerPoint models
data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val subtitle: String? = null,
    val bulletPoints: List<String> = emptyList()
)

// Markdown models
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<String>) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
}

// EPUB models
data class EpubChapter(
    val index: Int,
    val title: String,
    val content: String
)
