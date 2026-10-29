package com.example.ui.reader.viewers

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.DocumentContent
import com.example.parser.PdfHandler
import kotlinx.coroutines.launch

@Composable
fun PdfViewer(
    pdf: DocumentContent.Pdf,
    pdfHandler: PdfHandler,
    initialPage: Int = 0,
    continuousScroll: Boolean = true,
    onPageChanged: (pageIndex: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0)))

    var currentPage by remember { mutableStateOf(initialPage.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))) }
    var zoomScale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var isNightReadingMode by remember { mutableStateOf(false) }
    var showNightModeInfo by remember { mutableStateOf(false) }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val visible = listState.firstVisibleItemIndex
        if (visible != currentPage && visible < pdf.pageCount) {
            currentPage = visible
            onPageChanged(visible, pdf.pageCount)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (continuousScroll) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                            if (zoomScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(pdf.pageCount) { pageIndex ->
                    PdfPageItem(
                        pdf = pdf,
                        pdfHandler = pdfHandler,
                        pageIndex = pageIndex,
                        isNightReadingMode = isNightReadingMode
                    )
                }
            }
        } else {
            // Single page mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                PdfPageItem(
                    pdf = pdf,
                    pdfHandler = pdfHandler,
                    pageIndex = currentPage,
                    isNightReadingMode = isNightReadingMode,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                )
            }
        }

        // Bottom floating navigation bar for page controls (adapts to theme)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = {
                        if (currentPage > 0) {
                            val prev = currentPage - 1
                            currentPage = prev
                            coroutineScope.launch { listState.animateScrollToItem(prev) }
                            onPageChanged(prev, pdf.pageCount)
                        }
                    },
                    enabled = currentPage > 0,
                    modifier = Modifier.size(36.dp).testTag("pdf_prev_page")
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateBefore,
                        contentDescription = "Previous page",
                        tint = if (currentPage > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Page indicator button (tap to jump)
                TextButton(
                    onClick = { showJumpDialog = true },
                    modifier = Modifier.height(36.dp).testTag("pdf_page_indicator")
                ) {
                    Text(
                        text = "${currentPage + 1} / ${pdf.pageCount}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        if (currentPage < pdf.pageCount - 1) {
                            val next = currentPage + 1
                            currentPage = next
                            coroutineScope.launch { listState.animateScrollToItem(next) }
                            onPageChanged(next, pdf.pageCount)
                        }
                    },
                    enabled = currentPage < pdf.pageCount - 1,
                    modifier = Modifier.size(36.dp).testTag("pdf_next_page")
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateNext,
                        contentDescription = "Next page",
                        tint = if (currentPage < pdf.pageCount - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Optional Night Reading Mode toggle (warm tint filter without inverting original graphics)
                IconButton(
                    onClick = {
                        isNightReadingMode = !isNightReadingMode
                        if (isNightReadingMode) showNightModeInfo = true
                    },
                    modifier = Modifier.size(36.dp).testTag("pdf_toggle_night_reading")
                ) {
                    Icon(
                        imageVector = if (isNightReadingMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Toggle night reading filter",
                        tint = if (isNightReadingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom reset if zoomed
                if (zoomScale > 1.05f) {
                    IconButton(
                        onClick = {
                            zoomScale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier.size(36.dp).testTag("pdf_reset_zoom")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOutMap,
                            contentDescription = "Reset zoom",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = currentPage + 1,
            totalPages = pdf.pageCount,
            onDismiss = { showJumpDialog = false },
            onJump = { page ->
                val targetIndex = (page - 1).coerceIn(0, pdf.pageCount - 1)
                currentPage = targetIndex
                coroutineScope.launch { listState.scrollToItem(targetIndex) }
                onPageChanged(targetIndex, pdf.pageCount)
                showJumpDialog = false
            }
        )
    }

    if (showNightModeInfo) {
        AlertDialog(
            onDismissRequest = { showNightModeInfo = false },
            title = { Text("Night Reading Mode", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text = "Applies a gentle warm ambient filter to reduce glare in dark rooms. Document page colors and diagrams are preserved by default to prevent visual distortion.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showNightModeInfo = false }) {
                    Text("Got it")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun PdfPageItem(
    pdf: DocumentContent.Pdf,
    pdfHandler: PdfHandler,
    pageIndex: Int,
    isNightReadingMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    LaunchedEffect(pageIndex) {
        isLoading = true
        bitmap = pdfHandler.renderPage(pdf, pageIndex, scale = 2.0f)
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .aspectRatio(0.77f), // Standard letter/A4 aspect ratio
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap!!.isRecycled) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Optional gentle night reading tint overlay
            if (isNightReadingMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A241C).copy(alpha = 0.12f))
                )
            }
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Unable to render page ${pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var input by remember { mutableStateOf(currentPage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Page", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a page number between 1 and $totalPages:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("jump_page_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val p = input.toIntOrNull() ?: currentPage
                    onJump(p.coerceIn(1, totalPages))
                }
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    )
}
