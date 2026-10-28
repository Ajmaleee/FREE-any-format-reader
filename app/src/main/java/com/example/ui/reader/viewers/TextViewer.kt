package com.example.ui.reader.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.DocumentContent
import com.example.ui.theme.WarningGold

@Composable
fun TextViewer(
    textDoc: DocumentContent.Text,
    initialFontSizeSp: Float = 14f,
    showLineNumbersDefault: Boolean = true,
    wordWrapDefault: Boolean = true,
    modifier: Modifier = Modifier
) {
    var fontSizeSp by remember { mutableStateOf(initialFontSizeSp) }
    var showLineNumbers by remember { mutableStateOf(showLineNumbersDefault) }
    var isMonospace by remember { mutableStateOf(true) }
    var isWordWrap by remember { mutableStateOf(wordWrapDefault) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Controls Toolbar
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = showLineNumbers,
                        onClick = { showLineNumbers = !showLineNumbers },
                        label = { Text("Line #", fontSize = 11.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = isMonospace,
                        onClick = { isMonospace = !isMonospace },
                        label = { Text(if (isMonospace) "Mono" else "Sans", fontSize = 11.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                    FilterChip(
                        selected = isWordWrap,
                        onClick = { isWordWrap = !isWordWrap },
                        label = { Text("Wrap", fontSize = 11.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(11f) },
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
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.size(32.dp).testTag("text_toggle_search")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search text",
                            tint = if (isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Search Bar
        if (isSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f).testTag("text_search_input")
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp).testTag("text_clear_search")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Text display body
        val textModifier = if (!isWordWrap) {
            Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)
        } else {
            Modifier.fillMaxWidth()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = textModifier.fillMaxHeight(),
                contentPadding = PaddingValues(12.dp)
            ) {
                itemsIndexed(textDoc.lines) { index, line ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (showLineNumbers) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (fontSizeSp - 2).coerceAtLeast(9f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .width(36.dp)
                                    .padding(end = 8.dp)
                            )
                        }

                        val annotated = if (searchQuery.isNotBlank() && line.contains(searchQuery, ignoreCase = true)) {
                            buildAnnotatedString {
                                var startIndex = 0
                                val lowerLine = line.lowercase()
                                val lowerQuery = searchQuery.lowercase()
                                while (startIndex < line.length) {
                                    val hit = lowerLine.indexOf(lowerQuery, startIndex)
                                    if (hit == -1) {
                                        append(line.substring(startIndex))
                                        break
                                    } else {
                                        append(line.substring(startIndex, hit))
                                        val matchEnd = hit + searchQuery.length
                                        val span = SpanStyle(
                                            background = WarningGold.copy(alpha = 0.35f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        val matchText = line.substring(hit, matchEnd)
                                        withStyle(span) { append(matchText) }
                                        startIndex = matchEnd
                                    }
                                }
                            }
                        } else {
                            buildAnnotatedString { append(line) }
                        }

                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.SansSerif,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
