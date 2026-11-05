package com.opentasker.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.widget.WidgetIconCatalog
import com.opentasker.widget.CustomWidgetIcon
import com.opentasker.widget.CustomWidgetIconStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caches raster thumbnails for the picker only. The app continues to use each original vector
 * drawable for Action cards, widgets, and previews.
 *
 * Vector inflation and drawing happen on a worker thread, not during a fast LazyVerticalGrid fling.
 * A small process-wide cache keeps scrolling back through the catalog from decoding icons again.
 */
private object WidgetIconThumbnailCache {
    private const val THUMBNAIL_SIZE_PX = 48
    private const val CACHE_SIZE_BYTES = 5 * 1024 * 1024

    private val cache = object : LruCache<Int, ImageBitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: Int, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    suspend fun get(context: Context, drawableRes: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            cache.get(drawableRes) ?: synchronized(cache) {
                cache.get(drawableRes) ?: runCatching {
                    val drawable = context.applicationContext.getDrawable(drawableRes)
                        ?: return@runCatching null
                    val bitmap = Bitmap.createBitmap(
                        THUMBNAIL_SIZE_PX,
                        THUMBNAIL_SIZE_PX,
                        Bitmap.Config.ARGB_8888,
                    )
                    drawable.setBounds(0, 0, THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX)
                    drawable.draw(Canvas(bitmap))
                    bitmap.asImageBitmap().also { cache.put(drawableRes, it) }
                }.getOrNull()
            }
        }
}

private data class IconPickerEntry(
    val key: String,
    val label: String,
    val category: String,
    val drawableRes: Int? = null,
    val customIcon: CustomWidgetIcon? = null,
)

/**
 * Searchable, categorized picker shared by profiles, widgets, and popup actions. Uploaded icons
 * appear in their own Custom category alongside the original Coolicons collection.
 */
@Composable
internal fun ActionIconPickerDialog(
    selectedIcon: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    val context = LocalContext.current.applicationContext
    val customIcons by CustomWidgetIconStore.observe(context).collectAsState()
    val pickerIcons = remember(customIcons) {
        WidgetIconCatalog.all.map { option ->
            IconPickerEntry(
                key = option.key,
                label = option.label,
                category = WidgetIconCatalog.categoryFor(option),
                drawableRes = option.drawableRes,
            )
        } + customIcons.map { icon ->
            IconPickerEntry(
                key = icon.key,
                label = icon.name,
                category = "Custom",
                customIcon = icon,
            )
        }
    }
    val categories = remember(pickerIcons) {
        val order = listOf(
            "Custom", "Actions", "Arrows", "Communication", "Devices", "Files & Data",
            "Media", "Navigation", "Places & Things", "Productivity", "Symbols & Shapes",
            "Time", "Other",
        )
        listOf("All") + order.filter { category ->
            category == "Custom" || pickerIcons.any { it.category == category }
        }
    }
    val filteredIcons = remember(pickerIcons, searchQuery, selectedCategory) {
        pickerIcons.filter { option ->
            (selectedCategory == "All" || option.category == selectedCategory) &&
                (searchQuery.isBlank() ||
                    option.label.contains(searchQuery, ignoreCase = true) ||
                    option.key.contains(searchQuery, ignoreCase = true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose action icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search icons") },
                    singleLine = true,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories, key = { it }) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                        )
                    }
                }
                Text(
                    "${filteredIcons.size} icons",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (filteredIcons.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (selectedCategory == "Custom") {
                                "No custom icons yet. Upload icons in Settings > General > Custom icons."
                            } else {
                                "No icons match this search."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 68.dp),
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredIcons, key = { it.key }) { option ->
                            val selected = option.key == selectedIcon
                            val thumbnail by produceState<ImageBitmap?>(
                                initialValue = null,
                                option.key,
                                option.drawableRes,
                                option.customIcon?.id,
                            ) {
                                value = when {
                                    option.drawableRes != null ->
                                        WidgetIconThumbnailCache.get(context, option.drawableRes)
                                    option.customIcon != null ->
                                        CustomWidgetIconStore.loadBitmap(context, option.customIcon, 48)?.asImageBitmap()
                                    else -> null
                                }
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(72.dp).clickable {
                                    onIconSelected(option.key)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (thumbnail != null) {
                                            val tintCustomIcon = option.customIcon?.recolorable != false
                                            Image(
                                                bitmap = thumbnail!!,
                                                contentDescription = option.label,
                                                colorFilter = if (option.customIcon == null || tintCustomIcon) {
                                                    ColorFilter.tint(
                                                        if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface,
                                                        BlendMode.SrcIn,
                                                    )
                                                } else {
                                                    null
                                                },
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                        Text(
                                            option.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
