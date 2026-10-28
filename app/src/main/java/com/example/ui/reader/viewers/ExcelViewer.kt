package com.example.ui.reader.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.DocumentContent

@Composable
fun ExcelViewer(
    excel: DocumentContent.Excel,
    initialSheetIndex: Int = 0,
    onSheetChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSheetIndex by remember {
        mutableStateOf(initialSheetIndex.coerceIn(0, (excel.sheets.size - 1).coerceAtLeast(0)))
    }
    var selectedCellCoordinate by remember { mutableStateOf<String?>(null) }
    var selectedCellValue by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val currentSheet = excel.sheets.getOrNull(selectedSheetIndex) ?: return

    val filteredRows = remember(currentSheet, searchQuery) {
        if (searchQuery.isBlank()) {
            currentSheet.rows
        } else {
            currentSheet.rows.filter { row ->
                row.any { cell -> cell.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top search bar if active
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
                    modifier = Modifier.weight(1f).testTag("excel_search_input")
                )
                IconButton(
                    onClick = {
                        searchQuery = ""
                        isSearchActive = false
                    },
                    modifier = Modifier.size(28.dp).testTag("excel_search_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Cell formula / value inspector bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = selectedCellCoordinate ?: "Cell",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = selectedCellValue ?: "Tap any cell to inspect value",
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedCellValue == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!isSearchActive) {
                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.size(28.dp).testTag("excel_open_search")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search sheet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2D Scrollable Table Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight()
            ) {
                // Sticky Header Row
                item {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        // Corner empty box for row numbering column
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(36.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        currentSheet.headers.forEachIndexed { colIndex, header ->
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(36.dp)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Data Rows
                itemsIndexed(filteredRows) { rowIndex, row ->
                    val isSelectedRow = selectedCellCoordinate?.endsWith("${rowIndex + 1}") == true
                    val rowBg = if (isSelectedRow) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else if (rowIndex % 2 == 1) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        // Row Number
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${rowIndex + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Cell values
                        currentSheet.headers.indices.forEach { colIndex ->
                            val cellText = row.getOrNull(colIndex) ?: ""
                            val colLetter = ('A'.code + (colIndex % 26)).toChar().toString()
                            val coord = "$colLetter${rowIndex + 1}"
                            val isSelectedCell = selectedCellCoordinate == coord

                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(34.dp)
                                    .background(
                                        if (isSelectedCell) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        if (isSelectedCell) 1.5.dp else 0.5.dp,
                                        if (isSelectedCell) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                    .clickable {
                                        selectedCellCoordinate = coord
                                        selectedCellValue = cellText
                                    }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cellText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sheet Selector Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedSheetIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 8.dp
        ) {
            excel.sheets.forEachIndexed { index, sheet ->
                Tab(
                    selected = selectedSheetIndex == index,
                    onClick = {
                        selectedSheetIndex = index
                        selectedCellCoordinate = null
                        selectedCellValue = null
                        onSheetChanged(index, excel.sheets.size)
                    },
                    text = {
                        Text(
                            text = sheet.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selectedSheetIndex == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    },
                    modifier = Modifier.testTag("excel_tab_${sheet.name}")
                )
            }
        }
    }
}
