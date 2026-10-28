package com.example.ui.reader.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
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
fun PowerPointViewer(
    powerPoint: DocumentContent.PowerPoint,
    initialSlideIndex: Int = 0,
    onSlideChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentSlideIndex by remember {
        mutableStateOf(initialSlideIndex.coerceIn(0, (powerPoint.slides.size - 1).coerceAtLeast(0)))
    }

    val currentSlide = powerPoint.slides.getOrNull(currentSlideIndex) ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main 16:9 Slide Presentation Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Slide Title
                Text(
                    text = currentSlide.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Subtitle
                currentSlide.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                // Bullets
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    currentSlide.bulletPoints.forEach { point ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Slide navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    if (currentSlideIndex > 0) {
                        currentSlideIndex--
                        onSlideChanged(currentSlideIndex, powerPoint.slides.size)
                    }
                },
                enabled = currentSlideIndex > 0,
                modifier = Modifier.size(36.dp).testTag("pptx_prev_slide")
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateBefore,
                    contentDescription = "Previous slide"
                )
            }

            Text(
                text = "Slide ${currentSlideIndex + 1} of ${powerPoint.slides.size}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = {
                    if (currentSlideIndex < powerPoint.slides.size - 1) {
                        currentSlideIndex++
                        onSlideChanged(currentSlideIndex, powerPoint.slides.size)
                    }
                },
                enabled = currentSlideIndex < powerPoint.slides.size - 1,
                modifier = Modifier.size(36.dp).testTag("pptx_next_slide")
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateNext,
                    contentDescription = "Next slide"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Thumbnail strip
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(powerPoint.slides) { index, slide ->
                val isSelected = index == currentSlideIndex
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            currentSlideIndex = index
                            onSlideChanged(index, powerPoint.slides.size)
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}. ${slide.title}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
