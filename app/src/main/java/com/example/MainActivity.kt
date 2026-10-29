package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.browse.BrowseScreen
import com.example.ui.home.HomeScreen
import com.example.ui.reader.ReaderScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.UniversalReaderTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isOpenedFromExternalIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming document intent from external apps
        isOpenedFromExternalIntent = handleIntent(intent)

        setContent {
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val recentDocs by viewModel.recentDocuments.collectAsStateWithLifecycle()
            val activeDoc by viewModel.activeDocument.collectAsStateWithLifecycle()
            val activeContent by viewModel.activeContent.collectAsStateWithLifecycle()
            val isLoadingContent by viewModel.isLoading.collectAsStateWithLifecycle()

            // Storage Access Framework document picker
            val documentPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    isOpenedFromExternalIntent = false
                    viewModel.openFromUri(uri)
                }
            }

            UniversalReaderTheme(themeMode = preferences.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (activeDoc != null) {
                        // Reader takes priority over navigation bar
                        ReaderScreen(
                            document = activeDoc!!,
                            content = activeContent,
                            isLoading = isLoadingContent,
                            pdfHandler = viewModel.parser.pdfHandler,
                            continuousPdfScroll = preferences.continuousPdfScroll,
                            fontSizeSp = preferences.fontSizeSp,
                            showLineNumbers = preferences.showLineNumbers,
                            wordWrap = preferences.wordWrap,
                            onBack = {
                                if (isOpenedFromExternalIntent) {
                                    viewModel.closeDocument()
                                    finish()
                                } else {
                                    viewModel.closeDocument()
                                }
                            },
                            onReload = { viewModel.reloadCurrentDocument() },
                            onUpdateReadingProgress = { pos, total ->
                                viewModel.updateReadingProgress(pos, total)
                            },
                            onRemoveFromHistory = {
                                viewModel.removeFromHistory(activeDoc!!)
                                if (isOpenedFromExternalIntent) {
                                    finish()
                                }
                            }
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    windowInsets = WindowInsets.navigationBars,
                                    modifier = Modifier.testTag("main_bottom_nav")
                                ) {
                                    NavigationBarItem(
                                        selected = currentTab == MainTab.HOME,
                                        onClick = { viewModel.setTab(MainTab.HOME) },
                                        icon = {
                                            Icon(
                                                imageVector = if (currentTab == MainTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                                contentDescription = "Home"
                                            )
                                        },
                                        label = { Text("Home") },
                                        modifier = Modifier.testTag("nav_item_home")
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == MainTab.BROWSE,
                                        onClick = { viewModel.setTab(MainTab.BROWSE) },
                                        icon = {
                                            Icon(
                                                imageVector = if (currentTab == MainTab.BROWSE) Icons.Filled.Folder else Icons.Outlined.Folder,
                                                contentDescription = "Browse"
                                            )
                                        },
                                        label = { Text("Browse") },
                                        modifier = Modifier.testTag("nav_item_browse")
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == MainTab.SETTINGS,
                                        onClick = { viewModel.setTab(MainTab.SETTINGS) },
                                        icon = {
                                            Icon(
                                                imageVector = if (currentTab == MainTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                                contentDescription = "Settings"
                                            )
                                        },
                                        label = { Text("Settings") },
                                        modifier = Modifier.testTag("nav_item_settings")
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (currentTab) {
                                    MainTab.HOME -> {
                                        HomeScreen(
                                            recentDocuments = recentDocs,
                                            onOpenDocumentPicker = {
                                                documentPickerLauncher.launch(
                                                    arrayOf(
                                                        "application/pdf",
                                                        "application/vnd.openxmlformats-officedocument.*",
                                                        "application/msword",
                                                        "application/vnd.ms-excel",
                                                        "application/vnd.ms-powerpoint",
                                                        "text/*",
                                                        "image/*",
                                                        "application/json",
                                                        "application/xml",
                                                        "application/epub+zip",
                                                        "*/*"
                                                    )
                                                )
                                            },
                                            onDocumentClick = { doc ->
                                                isOpenedFromExternalIntent = false
                                                viewModel.openDocument(doc)
                                            },
                                            onToggleFavorite = { doc -> viewModel.toggleFavorite(doc) },
                                            onRemoveDocument = { doc -> viewModel.removeFromHistory(doc) },
                                            onNavigateToBrowse = { viewModel.setTab(MainTab.BROWSE) },
                                            onNavigateToSettings = { viewModel.setTab(MainTab.SETTINGS) }
                                        )
                                    }
                                    MainTab.BROWSE -> {
                                        BrowseScreen(
                                            documents = recentDocs,
                                            onOpenDocumentPicker = {
                                                documentPickerLauncher.launch(arrayOf("*/*"))
                                            },
                                            onDocumentClick = { doc ->
                                                isOpenedFromExternalIntent = false
                                                viewModel.openDocument(doc)
                                            },
                                            onToggleFavorite = { doc -> viewModel.toggleFavorite(doc) },
                                            onRemoveDocument = { doc -> viewModel.removeFromHistory(doc) }
                                        )
                                    }
                                    MainTab.SETTINGS -> {
                                        SettingsScreen(
                                            preferences = preferences,
                                            pdfHandler = viewModel.parser.pdfHandler,
                                            onThemeChanged = { mode -> viewModel.setThemeMode(mode) },
                                            onFontSizeChanged = { size -> viewModel.setFontSize(size) },
                                            onContinuousPdfChanged = { enabled -> viewModel.setContinuousPdfScroll(enabled) },
                                            onLineNumbersChanged = { enabled -> viewModel.setShowLineNumbers(enabled) },
                                            onWordWrapChanged = { enabled -> viewModel.setWordWrap(enabled) },
                                            onClearHistory = { viewModel.clearAllHistory() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // If another document is opened while the app is running, switch directly to the newly requested document
        isOpenedFromExternalIntent = handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action
        val mimeType = intent.type

        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> {
                intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            Intent.ACTION_SEND -> {
                // When sharing a file from WhatsApp or other apps, the URI is passed in EXTRA_STREAM or ClipData
                val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                streamUri ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: intent.data
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                streamUris?.firstOrNull() ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }

        if (uri != null) {
            viewModel.openFromUri(uri, mimeType)
            return true
        }
        return false
    }
}
