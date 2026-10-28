package com.example.ui.reader.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.DocumentContent
import com.example.parser.WordElement

@Composable
fun WordViewer(
    word: DocumentContent.Word,
    fontSizeSp: Float = 15f,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(word.elements) { element ->
            when (element) {
                is WordElement.Heading -> {
                    val (style, topSpace) = when (element.level) {
                        1 -> MaterialTheme.typography.headlineMedium.copy(fontSize = (fontSizeSp + 8).sp) to 12.dp
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = (fontSizeSp + 3).sp) to 8.dp
                        else -> MaterialTheme.typography.titleSmall.copy(fontSize = (fontSizeSp + 1).sp) to 4.dp
                    }
                    Column {
                        Spacer(modifier = Modifier.height(topSpace))
                        Text(
                            text = element.text,
                            style = style,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is WordElement.Paragraph -> {
                    if (element.runs.isNotEmpty()) {
                        val annotated = buildAnnotatedString {
                            element.runs.forEach { run ->
                                val color = run.hexColor?.let { hex ->
                                    try {
                                        Color(android.graphics.Color.parseColor("#$hex"))
                                    } catch (e: Exception) {
                                        null
                                    }
                                } ?: MaterialTheme.colorScheme.onSurface

                                val spanStyle = SpanStyle(
                                    fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                    color = color,
                                    fontSize = fontSizeSp.sp
                                )
                                withStyle(spanStyle) {
                                    append(run.text)
                                }
                            }
                        }
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = element.text,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is WordElement.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (element.level * 16).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = element.text,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is WordElement.Table -> {
                    WordTableCard(table = element)
                }
                is WordElement.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WordTableCard(table: WordElement.Table) {
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                // Header row
                if (table.headers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        table.headers.forEach { header ->
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }

                // Data rows
                table.rows.forEachIndexed { rowIndex, row ->
                    val bg = if (rowIndex % 2 == 1) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    }
                    Row(
                        modifier = Modifier
                            .background(bg)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .widthIn(min = 100.dp, max = 220.dp)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    if (rowIndex < table.rows.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
