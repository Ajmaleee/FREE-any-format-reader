package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class DocumentCategory {
    ALL,
    PDF,
    WORD,
    EXCEL,
    POWERPOINT,
    TEXT_CODE,
    IMAGE,
    EPUB
}

enum class DocumentFormat(
    val label: String,
    val primaryExtension: String,
    val badgeColor: Color,
    val category: DocumentCategory
) {
    PDF("PDF", "pdf", FormatPdfColor, DocumentCategory.PDF),
    WORD("Word", "docx", FormatWordColor, DocumentCategory.WORD),
    EXCEL("Excel", "xlsx", FormatExcelColor, DocumentCategory.EXCEL),
    POWERPOINT("Presentation", "pptx", FormatPptColor, DocumentCategory.POWERPOINT),
    CSV("CSV", "csv", FormatCsvColor, DocumentCategory.EXCEL),
    MARKDOWN("Markdown", "md", FormatMdColor, DocumentCategory.TEXT_CODE),
    TEXT("Text", "txt", FormatGenericColor, DocumentCategory.TEXT_CODE),
    JSON("JSON", "json", FormatJsonColor, DocumentCategory.TEXT_CODE),
    XML("XML", "xml", FormatXmlColor, DocumentCategory.TEXT_CODE),
    IMAGE("Image", "png", FormatImageColor, DocumentCategory.IMAGE),
    EPUB("eBook", "epub", FormatEpubColor, DocumentCategory.EPUB),
    UNKNOWN("Document", "", FormatGenericColor, DocumentCategory.ALL);

    companion object {
        fun fromFileNameOrMime(name: String, mime: String?): DocumentFormat {
            val lowerName = name.lowercase()
            val ext = lowerName.substringAfterLast('.', "")
            val lowerMime = mime?.lowercase() ?: ""

            return when {
                ext == "pdf" || lowerMime == "application/pdf" -> PDF
                ext in listOf("docx", "doc", "dotx", "dot") || lowerMime.contains("word") -> WORD
                ext in listOf("xlsx", "xls", "xltx", "xlt") || lowerMime.contains("spreadsheetml") || lowerMime.contains("ms-excel") || lowerMime.contains("excel") -> EXCEL
                ext in listOf("pptx", "ppt", "pps", "ppsx", "pot", "potx") || lowerMime.contains("presentationml") || lowerMime.contains("ms-powerpoint") || lowerMime.contains("powerpoint") -> POWERPOINT
                ext in listOf("csv", "tsv") || lowerMime == "text/csv" || lowerMime == "text/tab-separated-values" || lowerMime.contains("comma-separated") -> CSV
                ext in listOf("md", "markdown") || lowerMime == "text/markdown" -> MARKDOWN
                ext in listOf("json", "jsonl") || lowerMime == "application/json" -> JSON
                ext in listOf("xml", "html", "htm") || lowerMime == "application/xml" || lowerMime == "text/xml" || lowerMime == "text/html" -> XML
                ext in listOf("png", "jpg", "jpeg", "webp", "bmp", "gif") || lowerMime.startsWith("image/") -> IMAGE
                ext == "epub" || lowerMime == "application/epub+zip" -> EPUB
                ext in listOf("txt", "log", "ini", "conf", "yaml", "yml", "gradle", "kt", "java") || lowerMime.startsWith("text/") -> TEXT
                else -> UNKNOWN
            }
        }
    }
}
