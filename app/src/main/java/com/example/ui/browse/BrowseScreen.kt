package com.example.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentCategory
import com.example.data.model.DocumentRef
import com.example.ui.components.DocumentRow
import com.example.ui.components.EmptyState
import com.example.ui.components.FileInfoDialog
import com.example.ui.components.RestrainedSearchBar
import com.example.ui.theme.WarningGold

enum class SortOption(val label: String) {
    RECENT("Last Opened"),
    NAME("Name (A-Z)"),
    SIZE("File Size")
}

@Composable
fun BrowseScreen(
    documents: List<DocumentRef>,
    onOpenDocumentPicker: () -> Unit,
    onDocumentClick: (DocumentRef) -> Unit,
    onToggleFavorite: (DocumentRef) -> Unit,
    onRemoveDocument: (DocumentRef) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocumentCategory.ALL) }
    var sortOption by remember { mutableStateOf(SortOption.RECENT) }
    var onlyFavorites by remember { mutableStateOf(false) }

    var selectedDocForInfo by remember { mutableStateOf<DocumentRef?>(null) }
    var overflowDoc by remember { mutableStateOf<DocumentRef?>(null) }

    val filteredDocuments = remember(documents, searchQuery, selectedCategory, sortOption, onlyFavorites) {
        documents
            .filter { doc ->
                val matchesSearch = searchQuery.isBlank() || doc.displayName.contains(searchQuery, ignoreCase = true)
                val matchesCategory = selectedCategory == DocumentCategory.ALL || doc.format.category == selectedCategory
                val matchesFavorite = !onlyFavorites || doc.isFavorite
                matchesSearch && matchesCategory && matchesFavorite
            }
            .let { list ->
                when (sortOption) {
                    SortOption.RECENT -> list.sortedByDescending { it.lastOpenedTimestamp }
                    SortOption.NAME -> list.sortedBy { it.displayName.lowercase() }
                    SortOption.SIZE -> list.sortedByDescending { it.fileSizeBytes }
                }
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Box
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            RestrainedSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search by filename..."
            )
        }

        // Category Filter Tabs
        val categories = listOf(
            DocumentCategory.ALL to "All",
            DocumentCategory.PDF to "PDF",
            DocumentCategory.WORD to "Word",
            DocumentCategory.EXCEL to "Spreadsheets",
            DocumentCategory.POWERPOINT to "Slides",
            DocumentCategory.TEXT_CODE to "Text & Code",
            DocumentCategory.IMAGE to "Images",
            DocumentCategory.EPUB to "Books"
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(categories) { (cat, label) ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.height(32.dp).testTag("filter_chip_$label")
                )
            }
        }

        // Sort & Favorites Filter Sub-bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sort Dropdown
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { showSortMenu = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sortOption.label,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                sortOption = option
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Favorite Only Toggle
            FilterChip(
                selected = onlyFavorites,
                onClick = { onlyFavorites = !onlyFavorites },
                leadingIcon = {
                    Icon(
                        imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (onlyFavorites) WarningGold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = { Text("Starred", fontSize = 11.sp) },
                modifier = Modifier.height(28.dp).testTag("filter_starred")
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

        // Document list or empty state
        if (filteredDocuments.isEmpty()) {
            EmptyState(
                title = if (searchQuery.isNotBlank()) "No matching documents" else "No documents found",
                description = if (searchQuery.isNotBlank()) "Try searching with a different term." else "Open a document with the file picker.",
                actionLabel = if (searchQuery.isNotBlank()) null else "Open Document",
                onActionClick = onOpenDocumentPicker,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredDocuments, key = { it.uri }) { document ->
                    DocumentRow(
                        document = document,
                        onClick = { onDocumentClick(document) },
                        onOverflowClick = { overflowDoc = document },
                        onToggleFavorite = { onToggleFavorite(document) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }

    overflowDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { overflowDoc = null },
            title = { Text(doc.displayName, maxLines = 1, style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            overflowDoc = null
                            selectedDocForInfo = doc
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("File Information")
                        }
                    }
                    TextButton(
                        onClick = {
                            overflowDoc = null
                            onRemoveDocument(doc)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Remove from Recents", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { overflowDoc = null }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp)
        )
    }

    selectedDocForInfo?.let { doc ->
        FileInfoDialog(document = doc, onDismiss = { selectedDocForInfo = null })
    }
}
