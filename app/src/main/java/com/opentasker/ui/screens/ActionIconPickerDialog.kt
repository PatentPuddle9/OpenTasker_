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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.opentasker.ui.icons.WidgetIconImage
import com.opentasker.widget.CustomWidgetIcon
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.widget.WidgetIconCatalog
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

/**
 * Searchable icon picker. The lazy grid only creates visible cells; each cell gets a cached bitmap
 * thumbnail prepared off the main thread so quick flings do not inflate hundreds of vector files
 * during frame rendering.
 */

private data class IconPickerChoice(
    val key: String,
    val label: String,
    val drawableRes: Int? = null,
    val customIcon: CustomWidgetIcon? = null,
)

/** One searchable chooser for the complete built-in and custom icon library. */
@Composable
internal fun ActionIconPickerDialog(
    selectedIcon: String,
    onIconSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current.applicationContext
    val customIcons by CustomWidgetIconStore.observe(context).collectAsState()
    val choices = remember(customIcons, searchQuery) {
        (WidgetIconCatalog.all.map { IconPickerChoice(it.key, it.label, drawableRes = it.drawableRes) } +
            customIcons.map { IconPickerChoice(it.key, it.name, customIcon = it) })
            .filter { choice ->
                searchQuery.isBlank() ||
                    choice.label.contains(searchQuery, ignoreCase = true) ||
                    choice.key.contains(searchQuery, ignoreCase = true)
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search icons") },
                    singleLine = true,
                )
                Text(
                    "${choices.size} matching icons",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(choices, key = { it.key }) { choice ->
                        val selected = choice.key == selectedIcon
                        val thumbnail by produceState<ImageBitmap?>(
                            initialValue = null,
                            choice.drawableRes,
                        ) {
                            value = choice.drawableRes?.let { WidgetIconThumbnailCache.get(context, it) }
                        }
                        Surface(
                            modifier = Modifier.size(48.dp).clickable {
                                onIconSelected(choice.key)
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
                                if (choice.customIcon != null) {
                                    WidgetIconImage(
                                        iconKey = choice.key,
                                        contentDescription = choice.label,
                                        tint = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(28.dp),
                                    )
                                } else if (thumbnail != null) {
                                    Image(
                                        bitmap = thumbnail!!,
                                        contentDescription = choice.label,
                                        colorFilter = ColorFilter.tint(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        ),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
