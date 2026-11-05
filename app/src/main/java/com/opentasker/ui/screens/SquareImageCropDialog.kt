package com.opentasker.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.opentasker.widget.CustomWidgetIconStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun SquareImageCropDialog(
    source: Uri,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val viewportPx = with(LocalDensity.current) { 248.dp.toPx() }
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, source) {
        value = CustomWidgetIconStore.loadPreviewBitmap(context, source, 2048)
    }
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var offsetX by remember(source) { mutableFloatStateOf(0f) }
    var offsetY by remember(source) { mutableFloatStateOf(0f) }
    var cropping by remember { mutableStateOf(false) }
    var cropError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!cropping) onDismiss() },
        title = { Text("Crop to a square") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Drag to position the image and pinch to zoom. The saved icon will be a square.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (bitmap == null) {
                    Box(
                        modifier = Modifier.size(248.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Loading image…", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Box(
                        modifier = Modifier.size(248.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(bitmap, zoom) {
                                detectTransformGestures { _, pan, zoomChange, _ ->
                                    val nextZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                    zoom = nextZoom
                                    val maxOffset = viewportPx * (nextZoom - 1f) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Square crop preview",
                            modifier = Modifier.size(248.dp).graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offsetX
                                translationY = offsetY
                            },
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Zoom", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = zoom,
                            onValueChange = { next ->
                                zoom = next
                                val maxOffset = viewportPx * (next - 1f) / 2f
                                offsetX = offsetX.coerceIn(-maxOffset, maxOffset)
                                offsetY = offsetY.coerceIn(-maxOffset, maxOffset)
                            },
                            valueRange = 1f..5f,
                        )
                    }
                }
                cropError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = bitmap != null && !cropping,
                onClick = {
                    val sourceBitmap = bitmap ?: return@Button
                    cropping = true
                    cropError = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.Default) {
                                cropSquareIcon(sourceBitmap, viewportPx, zoom, offsetX, offsetY)
                            }
                        }.onSuccess(onCropped)
                            .onFailure {
                                cropping = false
                                cropError = it.message ?: "Could not crop this image."
                            }
                    }
                },
            ) { Text(if (cropping) "Cropping…" else "Use square crop") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !cropping) { Text("Cancel") }
        },
    )
}

internal fun cropSquareIcon(
    source: Bitmap,
    viewportSizePx: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    outputSize: Int = 512,
): Bitmap {
    require(viewportSizePx > 0f && zoom >= 1f && outputSize > 0)
    val shortestSide = minOf(source.width, source.height).toFloat()
    val displayScale = viewportSizePx / shortestSide
    val cropSide = (shortestSide / zoom).roundToInt().coerceIn(1, minOf(source.width, source.height))
    val centerX = source.width / 2f - offsetX / (displayScale * zoom)
    val centerY = source.height / 2f - offsetY / (displayScale * zoom)
    val left = (centerX - cropSide / 2f).roundToInt().coerceIn(0, source.width - cropSide)
    val top = (centerY - cropSide / 2f).roundToInt().coerceIn(0, source.height - cropSide)
    val square = Bitmap.createBitmap(source, left, top, cropSide, cropSide)
    return if (square.width == outputSize) square else Bitmap.createScaledBitmap(square, outputSize, outputSize, true)
}
