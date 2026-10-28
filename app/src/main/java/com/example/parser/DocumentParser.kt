package com.example.parser

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentRef

class DocumentParser(private val context: Context) {

    val pdfHandler = PdfHandler(context)
    val wordHandler = WordHandler(context)
    val excelHandler = ExcelHandler(context)
    val powerPointHandler = PowerPointHandler(context)
    val csvHandler = CsvHandler(context)
    val markdownHandler = MarkdownHandler(context)
    val structuredTextHandler = StructuredTextHandler(context)
    val epubHandler = EpubHandler(context)
    val imageHandler = ImageHandler(context)

    suspend fun parse(document: DocumentRef): DocumentContent {
        val uri = Uri.parse(document.uri)
        val isAsset = document.isAsset
        val assetPath = document.assetPath

        return when (document.format) {
            DocumentFormat.PDF -> pdfHandler.open(uri, isAsset, assetPath)
            DocumentFormat.WORD -> wordHandler.open(uri, isAsset, assetPath)
            DocumentFormat.EXCEL -> excelHandler.open(uri, isAsset, assetPath)
            DocumentFormat.POWERPOINT -> powerPointHandler.open(uri, isAsset, assetPath)
            DocumentFormat.CSV -> csvHandler.open(uri, isAsset, assetPath)
            DocumentFormat.MARKDOWN -> markdownHandler.open(uri, isAsset, assetPath)
            DocumentFormat.JSON -> structuredTextHandler.openJson(uri, isAsset, assetPath)
            DocumentFormat.XML -> structuredTextHandler.openXml(uri, isAsset, assetPath)
            DocumentFormat.TEXT -> structuredTextHandler.openPlainText(uri, isAsset, assetPath)
            DocumentFormat.IMAGE -> imageHandler.open(uri, isAsset, assetPath)
            DocumentFormat.EPUB -> epubHandler.open(uri, isAsset, assetPath)
            DocumentFormat.UNKNOWN -> {
                // Try plain text fallback
                structuredTextHandler.openPlainText(uri, isAsset, assetPath)
            }
        }
    }
}
