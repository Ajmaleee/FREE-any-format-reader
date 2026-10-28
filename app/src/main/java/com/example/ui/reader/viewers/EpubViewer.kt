package com.example.ui.reader.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.DocumentContent

@Composable
fun EpubViewer(
    epub: DocumentContent.Epub,
    initialChapterIndex: Int = 0,
    onChapterChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentChapterIndex by remember {
        mutableStateOf(initialChapterIndex.coerceIn(0, (epub.chapters.size - 1).coerceAtLeast(0)))
    }
    var fontSizeSp by remember { mutableStateOf(16f) }
    var useSerif by remember { mutableStateOf(true) }
    var showChapterSelector by remember { mutableStateOf(false) }

    val currentChapter = epub.chapters.getOrNull(currentChapterIndex) ?: return
    val scrollState = rememberScrollState()

    // Reset scroll when chapter changes
    LaunchedEffect(currentChapterIndex) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // eBook Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Table of Contents button
                TextButton(
                    onClick = { showChapterSelector = true },
                    modifier = Modifier.testTag("epub_toc_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = "Chapters",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chapter ${currentChapterIndex + 1} of ${epub.chapters.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = useSerif,
                        onClick = { useSerif = !useSerif },
                        label = { Text(if (useSerif) "Serif" else "Sans", fontSize = 11.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(13f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("A-", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = { fontSizeSp = (fontSizeSp + 1f).coerceAtMost(24f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("A+", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Chapter Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = currentChapter.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSizeSp + 6).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                epub.author?.let { author ->
                    Text(
                        text = "by $author",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Text(
                    text = currentChapter.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.SansSerif,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.6f).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Bottom Chapter navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    if (currentChapterIndex > 0) {
                        currentChapterIndex--
                        onChapterChanged(currentChapterIndex, epub.chapters.size)
                    }
                },
                enabled = currentChapterIndex > 0,
                modifier = Modifier.testTag("epub_prev_chapter")
            ) {
                Icon(imageVector = Icons.Default.NavigateBefore, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Previous Chapter")
            }

            TextButton(
                onClick = {
                    if (currentChapterIndex < epub.chapters.size - 1) {
                        currentChapterIndex++
                        onChapterChanged(currentChapterIndex, epub.chapters.size)
                    }
                },
                enabled = currentChapterIndex < epub.chapters.size - 1,
                modifier = Modifier.testTag("epub_next_chapter")
            ) {
                Text("Next Chapter")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(imageVector = Icons.Default.NavigateNext, contentDescription = null)
            }
        }
    }

    if (showChapterSelector) {
        AlertDialog(
            onDismissRequest = { showChapterSelector = false },
            title = { Text("Table of Contents", style = MaterialTheme.typography.titleMedium) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(epub.chapters) { idx, chapter ->
                        val isCurrent = idx == currentChapterIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    currentChapterIndex = idx
                                    onChapterChanged(idx, epub.chapters.size)
                                    showChapterSelector = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. ${chapter.title}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterSelector = false }) { Text("Close") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
