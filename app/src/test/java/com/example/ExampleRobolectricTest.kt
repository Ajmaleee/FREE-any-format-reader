package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentCategory
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentRef
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.parser.CsvHandler
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
    fun verifySampleSeeding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DocumentRepository(context)

        repo.seedSampleDocumentsIfFirstRun()
        val recents = repo.recentDocuments.first()
        assertTrue("Expected seeded sample documents", recents.isNotEmpty())
        assertTrue("Expected PDF sample in recents", recents.any { it.format == DocumentFormat.PDF })
        assertTrue("Expected Word sample in recents", recents.any { it.format == DocumentFormat.WORD })
        assertTrue("Expected Excel sample in recents", recents.any { it.format == DocumentFormat.EXCEL })
    }
}
