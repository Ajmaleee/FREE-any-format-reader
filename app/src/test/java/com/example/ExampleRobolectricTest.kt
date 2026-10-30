package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentCategory
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentRef
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.parser.CsvHandler
import com.example.parser.XmlSanitizer
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun verifyAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Universal Reader", appName)
    }

    @Test
    fun verifyDocumentFormatDetection() {
        assertEquals(DocumentFormat.PDF, DocumentFormat.fromFileNameOrMime("report.pdf", null))
        assertEquals(DocumentFormat.WORD, DocumentFormat.fromFileNameOrMime("memo.docx", null))
        assertEquals(DocumentFormat.EXCEL, DocumentFormat.fromFileNameOrMime("budget.xlsx", null))
        assertEquals(DocumentFormat.POWERPOINT, DocumentFormat.fromFileNameOrMime("deck.pptx", null))
        assertEquals(DocumentFormat.CSV, DocumentFormat.fromFileNameOrMime("data.csv", null))
        assertEquals(DocumentFormat.MARKDOWN, DocumentFormat.fromFileNameOrMime("notes.md", null))
        assertEquals(DocumentFormat.JSON, DocumentFormat.fromFileNameOrMime("config.json", null))
        assertEquals(DocumentFormat.XML, DocumentFormat.fromFileNameOrMime("schema.xml", null))
        assertEquals(DocumentFormat.IMAGE, DocumentFormat.fromFileNameOrMime("photo.png", null))
        assertEquals(DocumentFormat.EPUB, DocumentFormat.fromFileNameOrMime("book.epub", null))
        assertEquals(DocumentFormat.TEXT, DocumentFormat.fromFileNameOrMime("server.log", null))
    }

    @Test
    fun verifyDocumentRefFormatting() {
        assertEquals("1.5 MB", DocumentRef.formatFileSize(1572864))
        assertEquals("500.0 KB", DocumentRef.formatFileSize(512000))
        assertEquals("Just now", DocumentRef.formatTimestamp(System.currentTimeMillis()))
    }

    @Test
    fun verifyUserPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)

        prefs.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, prefs.preferences.value.themeMode)

        prefs.setContinuousPdfScroll(false)
        assertFalse(prefs.preferences.value.continuousPdfScroll)

        prefs.setFontSize(18f)
        assertEquals(18f, prefs.preferences.value.fontSizeSp, 0.01f)
    }

    @Test
    fun verifyPowerPointFormatDetection() {
        // Modern pptx extension
        assertEquals(DocumentFormat.POWERPOINT, DocumentFormat.fromFileNameOrMime("presentation.pptx", null))
        // Legacy ppt extension
        assertEquals(DocumentFormat.POWERPOINT, DocumentFormat.fromFileNameOrMime("slides.ppt", null))
        // Modern PPTX MIME type
        assertEquals(
            DocumentFormat.POWERPOINT,
            DocumentFormat.fromFileNameOrMime("unnamed", "application/vnd.openxmlformats-officedocument.presentationml.presentation")
        )
        // Legacy PPT MIME type
        assertEquals(
            DocumentFormat.POWERPOINT,
            DocumentFormat.fromFileNameOrMime("unnamed", "application/vnd.ms-powerpoint")
        )
        // WhatsApp or alternative ppt mime
        assertEquals(
            DocumentFormat.POWERPOINT,
            DocumentFormat.fromFileNameOrMime("unnamed", "application/powerpoint")
        )
    }

    @Test
    fun verifySampleSeeding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DocumentRepository(context)

        repo.seedSampleDocumentsIfFirstRun()
        val recents = repo.recentDocuments.first()
        assertTrue("Expected seeded sample documents", recents.isNotEmpty())
        assertTrue("Expected PDF sample in recents", recents.any { it.format == DocumentFormat.PDF })
        assertTrue("Expected Word sample in recents", recents.any { it.format == DocumentFormat.WORD })
        assertTrue("Expected Excel sample in recents", recents.any { it.format == DocumentFormat.EXCEL })
        assertTrue("Expected PowerPoint sample in recents", recents.any { it.format == DocumentFormat.POWERPOINT })
    }

    @Test
    fun verifyXmlSanitization() {
        val input = "R&D and Privacy & Performance &amp; 1 < 2 & 3 > 2 &quot;hello&quot;"
        val sanitized = XmlSanitizer.sanitize(input)
        assertFalse("Should not contain bare unescaped ampersand followed by letters", sanitized.contains("R&D"))
        assertTrue("Should contain escaped R&amp;D", sanitized.contains("R&amp;D"))
        assertTrue("Should preserve existing entity &amp;", sanitized.contains("&amp;"))
        assertTrue("Should preserve existing entity &quot;", sanitized.contains("&quot;"))
    }

    @Test
    fun verifyPowerPointSampleParsesSuccessfully() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = com.example.parser.DocumentParser(context)
        val doc = DocumentRef(
            uri = "asset://samples/product_design_systems.pptx",
            displayName = "Product Design Systems.pptx",
            mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            format = DocumentFormat.POWERPOINT,
            fileSizeBytes = 1000,
            lastOpenedTimestamp = System.currentTimeMillis(),
            isAsset = true,
            assetPath = "samples/product_design_systems.pptx"
        )
        val content = parser.parse(doc)
        assertTrue("Expected PowerPoint content, got: $content", content is com.example.parser.DocumentContent.PowerPoint)
        val ppt = content as com.example.parser.DocumentContent.PowerPoint
        assertTrue("Expected slides in presentation", ppt.slides.isNotEmpty())
    }

    @Test
    fun verifyExcelSampleParsesSuccessfully() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = com.example.parser.DocumentParser(context)
        val doc = DocumentRef(
            uri = "asset://samples/financial_model_q3.xlsx",
            displayName = "Financial Model Q3.xlsx",
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            format = DocumentFormat.EXCEL,
            fileSizeBytes = 1000,
            lastOpenedTimestamp = System.currentTimeMillis(),
            isAsset = true,
            assetPath = "samples/financial_model_q3.xlsx"
        )
        val content = parser.parse(doc)
        assertTrue("Expected Excel content, got: $content", content is com.example.parser.DocumentContent.Excel)
        val excel = content as com.example.parser.DocumentContent.Excel
        assertTrue("Expected sheets in spreadsheet", excel.sheets.isNotEmpty())
        assertTrue("Expected rows in sheet", excel.sheets[0].rows.isNotEmpty())
    }
}
