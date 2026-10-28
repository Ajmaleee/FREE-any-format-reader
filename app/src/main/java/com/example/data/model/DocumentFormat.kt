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

            return when {
                ext == "pdf" || mime == "application/pdf" -> PDF
                ext in listOf("docx", "doc") || mime?.contains("word") == true -> WORD
                ext in listOf("xlsx", "xls") || mime?.contains("spreadsheetml") == true || mime?.contains("ms-excel") == true -> EXCEL
                ext in listOf("pptx", "ppt") || mime?.contains("presentationml") == true || mime?.contains("ms-powerpoint") == true -> POWERPOINT
                ext in listOf("csv", "tsv") || mime == "text/csv" || mime == "text/tab-separated-values" -> CSV
                ext in listOf("md", "markdown") || mime == "text/markdown" -> MARKDOWN
                ext in listOf("json", "jsonl") || mime == "application/json" -> JSON
                ext in listOf("xml", "html", "htm") || mime == "application/xml" || mime == "text/xml" || mime == "text/html" -> XML
                ext in listOf("png", "jpg", "jpeg", "webp", "bmp", "gif") || mime?.startsWith("image/") == true -> IMAGE
                ext == "epub" || mime == "application/epub+zip" -> EPUB
                ext in listOf("txt", "log", "ini", "conf", "yaml", "yml", "gradle", "kt", "java") || mime?.startsWith("text/") == true -> TEXT
                else -> UNKNOWN
            }
        }
    }
}
