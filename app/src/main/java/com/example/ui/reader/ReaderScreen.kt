package com.example.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentRef
import com.example.parser.DocumentContent
import com.example.parser.PdfHandler
import com.example.ui.components.FileInfoDialog
import com.example.ui.components.FormatBadge
import com.example.ui.reader.viewers.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    document: DocumentRef,
    content: DocumentContent?,
    isLoading: Boolean,
    pdfHandler: PdfHandler,
    continuousPdfScroll: Boolean,
    fontSizeSp: Float,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onUpdateReadingProgress: (position: Int, total: Int) -> Unit,
    onRemoveFromHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormatBadge(format = document.format)
                        Text(
                            text = document.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to library"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("reader_overflow_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Reader options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("File Information") },
                                onClick = {
                                    showMenu = false
                                    showInfoDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Document") },
                                onClick = {
                                    showMenu = false
                                    try {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = document.mimeType.ifBlank { "*/*" }
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(document.uri))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share ${document.displayName}"))
                                    } catch (e: Exception) {
                                        // Ignore or fallback
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reopen Document") },
                                onClick = {
                                    showMenu = false
                                    onReload()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Remove from Recents") },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromHistory()
                                    onBack()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Opening ${document.displayName}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                content is DocumentContent.Error -> {
                    ErrorStateView(
                        error = content,
                        fileName = document.displayName,
                        onRetry = onReload,
                        onBack = onBack
                    )
                }
                content is DocumentContent.Pdf -> {
                    PdfViewer(
                        pdf = content,
                        pdfHandler = pdfHandler,
                        initialPage = document.lastReadPosition,
                        continuousScroll = continuousPdfScroll,
                        onPageChanged = { page, total ->
                            onUpdateReadingProgress(page, total)
                        }
                    )
                }
                content is DocumentContent.Word -> {
                    WordViewer(
                        word = content,
                        fontSizeSp = fontSizeSp
                    )
                }
                content is DocumentContent.Excel -> {
                    ExcelViewer(
                        excel = content,
                        initialSheetIndex = document.lastReadPosition,
                        onSheetChanged = { sheetIdx, total ->
                            onUpdateReadingProgress(sheetIdx, total)
                        }
                    )
                }
                content is DocumentContent.PowerPoint -> {
                    PowerPointViewer(
                        powerPoint = content,
                        initialSlideIndex = document.lastReadPosition,
                        onSlideChanged = { slideIdx, total ->
                            onUpdateReadingProgress(slideIdx, total)
                        }
                    )
                }
                content is DocumentContent.Csv -> {
                    CsvViewer(csv = content)
                }
                content is DocumentContent.Markdown -> {
                    MarkdownViewer(
                        markdown = content,
                        fontSizeSp = fontSizeSp
                    )
                }
                content is DocumentContent.Text -> {
                    TextViewer(
                        textDoc = content,
                        initialFontSizeSp = fontSizeSp,
                        showLineNumbersDefault = showLineNumbers,
                        wordWrapDefault = wordWrap
                    )
                }
                content is DocumentContent.StructuredText -> {
                    StructuredTextViewer(structured = content)
                }
                content is DocumentContent.ImageDoc -> {
                    ImageViewer(imageDoc = content)
                }
                content is DocumentContent.Epub -> {
                    EpubViewer(
                        epub = content,
                        initialChapterIndex = document.lastReadPosition,
                        onChapterChanged = { chapterIdx, total ->
                            onUpdateReadingProgress(chapterIdx, total)
                        }
                    )
                }
            }
        }
    }

    if (showInfoDialog) {
        FileInfoDialog(
            document = document,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
fun ErrorStateView(
    error: DocumentContent.Error,
    fileName: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (error.isAccessRevoked) "File Access Revoked" else "Unable to Open Document",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Back to Library")
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Retry")
            }
        }
    }
}
