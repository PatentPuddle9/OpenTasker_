package com.opentasker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.opentasker.core.scenes.AlignmentGuide
import com.opentasker.core.scenes.AlignmentGuideKind
import com.opentasker.core.scenes.GuideOrientation

/**
 * Shared guide renderer used by the Scene and Widget Display canvases.
 * Full-length guides mark alignment; short rulers mark equal spacing between objects.
 */
@Composable
internal fun CanvasAlignmentGuideOverlay(
    guides: List<AlignmentGuide>,
    canvasWidthDp: Float,
    canvasHeightDp: Float,
    designWidthDp: Float,
    designHeightDp: Float,
    modifier: Modifier = Modifier,
) {
    if (guides.isEmpty()) return
    val alignmentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
    val spacingColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    Canvas(modifier.fillMaxSize()) {
        val scaleX = size.width / designWidthDp.coerceAtLeast(1f)
        val scaleY = size.height / designHeightDp.coerceAtLeast(1f)
        guides.forEach { guide ->
            val color = if (guide.kind == AlignmentGuideKind.SPACING) spacingColor else alignmentColor
            when (guide.orientation) {
                GuideOrientation.VERTICAL -> {
                    val x = guide.position * scaleX
                    val startY = guide.start?.times(scaleY) ?: 0f
                    val endY = guide.end?.times(scaleY) ?: size.height
                    drawLine(
                        color = color,
                        start = Offset(x, minOf(startY, endY)),
                        end = Offset(x, maxOf(startY, endY)),
                        strokeWidth = if (guide.kind == AlignmentGuideKind.SPACING) 2f else 1.5f,
                    )
                    if (guide.kind == AlignmentGuideKind.SPACING) {
                        drawCircle(color, radius = 2.5f, center = Offset(x, startY))
                        drawCircle(color, radius = 2.5f, center = Offset(x, endY))
                    }
                }
                GuideOrientation.HORIZONTAL -> {
                    val y = guide.position * scaleY
                    val startX = guide.start?.times(scaleX) ?: 0f
                    val endX = guide.end?.times(scaleX) ?: size.width
                    drawLine(
                        color = color,
                        start = Offset(minOf(startX, endX), y),
                        end = Offset(maxOf(startX, endX), y),
                        strokeWidth = if (guide.kind == AlignmentGuideKind.SPACING) 2f else 1.5f,
                    )
                    if (guide.kind == AlignmentGuideKind.SPACING) {
                        drawCircle(color, radius = 2.5f, center = Offset(startX, y))
                        drawCircle(color, radius = 2.5f, center = Offset(endX, y))
                    }
                }
            }
        }

        val labels = guides.filter { it.kind == AlignmentGuideKind.SPACING && !it.label.isNullOrBlank() }
        if (labels.isNotEmpty()) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10f * density
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = spacingColor.toArgb()
                    isFakeBoldText = true
                }
                labels.forEach { guide ->
                    val text = guide.label ?: return@forEach
                    val labelX = if (guide.orientation == GuideOrientation.HORIZONTAL) {
                        ((guide.start ?: 0f) + (guide.end ?: 0f)) * 0.5f * scaleX
                    } else {
                        guide.position * scaleX + 10f * density
                    }
                    val labelY = if (guide.orientation == GuideOrientation.HORIZONTAL) {
                        guide.position * scaleY - 4f * density
                    } else {
                        ((guide.start ?: 0f) + (guide.end ?: 0f)) * 0.5f * scaleY
                    }
                    canvas.nativeCanvas.drawText(text, labelX, labelY, paint)
                }
            }
        }
    }
}
