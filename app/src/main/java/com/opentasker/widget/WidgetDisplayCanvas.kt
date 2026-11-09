package com.opentasker.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.opentasker.core.scenes.AlignmentGuide
import com.opentasker.core.scenes.CanvasElementBounds
import com.opentasker.core.scenes.SceneAlignmentGuides
import com.opentasker.ui.icons.WidgetIconImage
import com.opentasker.ui.screens.CanvasAlignmentGuideOverlay
import kotlin.math.roundToInt

@Composable
internal fun WidgetDisplayCanvas(
    state: WidgetDisplayState,
    selectedElementId: String?,
    guides: List<AlignmentGuide>,
    onSelect: (String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    onResize: (String, Int, Int) -> Unit,
    onGuidesChanged: (List<AlignmentGuide>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Design canvas", style = MaterialTheme.typography.titleMedium)
            Text(
                "${WidgetDisplayCanvasMetrics.WIDTH_DP} × ${WidgetDisplayCanvasMetrics.HEIGHT_DP} dp",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Drag an item to move it. Select it to edit its size and position. Items snap to the canvas and to each other.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scale = maxWidth.value / WidgetDisplayCanvasMetrics.WIDTH_DP.toFloat()
            val canvasHeight = WidgetDisplayCanvasMetrics.HEIGHT_DP * scale
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(width = maxWidth, height = canvasHeight.dp)
                    .clipToBounds(),
                color = Color(state.backgroundColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (state.title.isNotBlank() || state.subtitle.isNotBlank()) {
                        Column(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            if (state.title.isNotBlank()) {
                                Text(
                                    state.title,
                                    color = Color(state.textColor),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (state.subtitle.isNotBlank()) {
                                Text(
                                    state.subtitle,
                                    color = Color(state.textColor).copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    state.elements.take(WidgetDisplayCanvasMetrics.MAX_ELEMENTS).forEachIndexed { index, element ->
                        WidgetDisplayCanvasItem(
                            element = element,
                            index = index,
                            state = state,
                            scale = scale,
                            selected = element.id == selectedElementId,
                            onSelect = { onSelect(element.id) },
                            onMove = { x, y -> onMove(element.id, x, y) },
                            onResize = { width, height -> onResize(element.id, width, height) },
                            onGuidesChanged = onGuidesChanged,
                        )
                    }
                    CanvasAlignmentGuideOverlay(
                        guides = guides,
                        canvasWidthDp = maxWidth.value,
                        canvasHeightDp = canvasHeight,
                        designWidthDp = WidgetDisplayCanvasMetrics.WIDTH_DP.toFloat(),
                        designHeightDp = WidgetDisplayCanvasMetrics.HEIGHT_DP.toFloat(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetDisplayCanvasItem(
    element: WidgetDisplayElement,
    index: Int,
    state: WidgetDisplayState,
    scale: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onResize: (Int, Int) -> Unit,
    onGuidesChanged: (List<AlignmentGuide>) -> Unit,
) {
    val density = LocalDensity.current
    var dragX by remember(state.id, element.id, element.xDp, element.yDp) { mutableFloatStateOf(0f) }
    var dragY by remember(state.id, element.id, element.xDp, element.yDp) { mutableFloatStateOf(0f) }
    var resizeX by remember(state.id, element.id, element.widthDp) { mutableFloatStateOf(0f) }
    var resizeY by remember(state.id, element.id, element.heightDp) { mutableFloatStateOf(0f) }

    val accent = Color(state.accentColor)
    val foreground = Color(state.textColor)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else accent.copy(alpha = 0.72f)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = ((element.xDp + dragX) * scale * density.density).roundToInt(),
                    y = ((element.yDp + dragY) * scale * density.density).roundToInt(),
                )
            }
            .size(
                width = (element.widthDp * scale).coerceAtLeast(24f).dp,
                height = (element.heightDp * scale).coerceAtLeast(20f).dp,
            ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onSelect)
                .pointerInput(state.id, element.id, element.xDp, element.yDp, scale, density.density) {
                    detectDragGestures(
                        onDragStart = { onSelect() },
                        onDrag = { change, amount ->
                            change.consume()
                            dragX += amount.x / density.density / scale
                            dragY += amount.y / density.density / scale
                            val candidateX = (element.xDp + dragX).roundToInt()
                            val candidateY = (element.yDp + dragY).roundToInt()
                            val result = SceneAlignmentGuides.findGuides(
                                canvasWidthDp = WidgetDisplayCanvasMetrics.WIDTH_DP,
                                canvasHeightDp = WidgetDisplayCanvasMetrics.HEIGHT_DP,
                                elements = state.elements.map {
                                    CanvasElementBounds(it.xDp, it.yDp, it.widthDp, it.heightDp)
                                },
                                movingIndex = index,
                                candidateX = candidateX,
                                candidateY = candidateY,
                                candidateW = element.widthDp,
                                candidateH = element.heightDp,
                            )
                            onGuidesChanged(result.guides)
                        },
                        onDragEnd = {
                            val candidateX = (element.xDp + dragX).roundToInt()
                            val candidateY = (element.yDp + dragY).roundToInt()
                            val result = SceneAlignmentGuides.findGuides(
                                canvasWidthDp = WidgetDisplayCanvasMetrics.WIDTH_DP,
                                canvasHeightDp = WidgetDisplayCanvasMetrics.HEIGHT_DP,
                                elements = state.elements.map {
                                    CanvasElementBounds(it.xDp, it.yDp, it.widthDp, it.heightDp)
                                },
                                movingIndex = index,
                                candidateX = candidateX,
                                candidateY = candidateY,
                                candidateW = element.widthDp,
                                candidateH = element.heightDp,
                            )
                            dragX = 0f
                            dragY = 0f
                            onGuidesChanged(emptyList())
                            onMove(result.snappedX, result.snappedY)
                        },
                        onDragCancel = {
                            dragX = 0f
                            dragY = 0f
                            onGuidesChanged(emptyList())
                        },
                    )
                },
            shape = RoundedCornerShape(8.dp),
            color = when (element.type) {
                WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT -> accent.copy(alpha = 0.24f)
                WidgetDisplayElementType.IMAGE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
                else -> Color(state.backgroundColor).copy(alpha = 0.55f)
            },
            border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        ) {
            WidgetDisplayCanvasItemContent(element, foreground, accent)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                    .pointerInput(state.id, element.id, element.widthDp, element.heightDp, scale, density.density) {
                        detectDragGestures(
                            onDrag = { change, amount ->
                                change.consume()
                                resizeX += amount.x / density.density / scale
                                resizeY += amount.y / density.density / scale
                            },
                            onDragEnd = {
                                val width = (element.widthDp + resizeX.roundToInt())
                                    .coerceIn(32, WidgetDisplayCanvasMetrics.WIDTH_DP - element.xDp)
                                val height = (element.heightDp + resizeY.roundToInt())
                                    .coerceIn(24, WidgetDisplayCanvasMetrics.HEIGHT_DP - element.yDp)
                                resizeX = 0f
                                resizeY = 0f
                                onResize(width, height)
                            },
                            onDragCancel = {
                                resizeX = 0f
                                resizeY = 0f
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("↘", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WidgetDisplayCanvasItemContent(
    element: WidgetDisplayElement,
    foreground: Color,
    accent: Color,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (element.type) {
            WidgetDisplayElementType.TEXT -> Text(
                element.text,
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            WidgetDisplayElementType.IMAGE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("IMAGE", color = accent, style = MaterialTheme.typography.labelSmall)
                if (element.text.isNotBlank()) {
                    Text(element.text, color = foreground, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT, WidgetDisplayElementType.ICON -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WidgetIconImage(
                    iconKey = element.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    element.text.ifBlank {
                        when (element.type) {
                            WidgetDisplayElementType.BUTTON -> "Action"
                            WidgetDisplayElementType.SHORTCUT -> "Widget shortcut"
                            WidgetDisplayElementType.ICON -> "Icon"
                            else -> "Content"
                        }
                    },
                    color = foreground,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WidgetDisplayElementType.PROGRESS -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${element.text} · ${element.progress.coerceIn(0, 100)}%",
                    color = foreground,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().size(height = 4.dp, width = 1.dp),
                    color = foreground.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(element.progress.coerceIn(0, 100) / 100f)
                            .background(accent, RoundedCornerShape(8.dp)),
                    )
                }
            }
            WidgetDisplayElementType.DIVIDER -> Box(
                Modifier.fillMaxWidth().size(height = 1.dp, width = 1.dp).background(foreground.copy(alpha = 0.6f)),
            )
            WidgetDisplayElementType.SPACER -> Box(Modifier.fillMaxSize())
        }
    }
}
