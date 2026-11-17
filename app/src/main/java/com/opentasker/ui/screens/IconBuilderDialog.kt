package com.opentasker.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opentasker.widget.WidgetIconCatalog
import com.opentasker.widget.WidgetShapeCatalog
import kotlin.math.roundToInt

private enum class StudioTool { DRAW, MOVE }
private data class StudioStroke(val id: Int, val points: List<Offset>, val color: Int, val width: Float)
private data class StudioLayer(
    val id: Int,
    val key: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val color: Int,
)

private val studioInkColors = listOf(
    0xFFFFFFFF.toInt(), 0xFF17151B.toInt(), 0xFFB9A2FF.toInt(), 0xFFFF7597.toInt(),
    0xFFFFC15A.toInt(), 0xFF70D6C3.toInt(), 0xFF79B9FF.toInt(), 0xFFB6E36A.toInt(),
)
private val studioBackgroundColors = listOf(
    0xFF6542A0.toInt(), 0xFF263D8E.toInt(), 0xFF00695C.toInt(), 0xFF9C3D67.toInt(),
    0xFFB45A16.toInt(), 0xFF263238.toInt(), 0xFF34313F.toInt(), 0xFF111111.toInt(),
)

@Composable
internal fun IconBuilderDialog(
    onDismiss: () -> Unit,
    saving: Boolean,
    error: String?,
    onSave: (Bitmap, String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    var name by rememberSaveable { mutableStateOf("") }
    var shapeKey by rememberSaveable { mutableStateOf(WidgetShapeCatalog.default.key) }
    var background by rememberSaveable { mutableIntStateOf(0xFF6542A0.toInt()) }
    var gradientEnd by rememberSaveable { mutableIntStateOf(0xFF34204F.toInt()) }
    var gradient by rememberSaveable { mutableStateOf(true) }
    var ink by rememberSaveable { mutableIntStateOf(AndroidColor.WHITE) }
    var brushSize by rememberSaveable { mutableFloatStateOf(0.035f) }
    var toolName by rememberSaveable { mutableStateOf(StudioTool.DRAW.name) }
    var showSymbolPicker by rememberSaveable { mutableStateOf(false) }
    var selectedLayerId by rememberSaveable { mutableIntStateOf(-1) }
    var nextLayerId by rememberSaveable { mutableIntStateOf(1) }
    var nextStrokeId by remember { mutableIntStateOf(1) }
    var renderError by remember { mutableStateOf<String?>(null) }
    val strokes = remember { mutableStateListOf<StudioStroke>() }
    val layers = remember { mutableStateListOf<StudioLayer>() }
    val tool = runCatching { StudioTool.valueOf(toolName) }.getOrDefault(StudioTool.DRAW)
    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }
    val fillBrush = if (gradient) Brush.linearGradient(listOf(Color(background), Color(gradientEnd)))
        else SolidColor(Color(background))
    val canvasShape = studioShape(shapeKey)

    if (showSymbolPicker) {
        ActionIconPickerDialog(
            selectedIcon = WidgetIconCatalog.default.key,
            onIconSelected = { key ->
                val placements = listOf(
                    0.50f to 0.50f, 0.34f to 0.50f, 0.66f to 0.50f,
                    0.50f to 0.34f, 0.50f to 0.66f, 0.37f to 0.37f, 0.63f to 0.63f,
                )
                val at = placements[layers.size % placements.size]
                val id = nextLayerId++
                layers.add(StudioLayer(id, key, at.first, at.second, 0.25f, ink))
                selectedLayerId = id
                toolName = StudioTool.MOVE.name
                showSymbolPicker = false
            },
            onDismiss = { showSymbolPicker = false },
            includeCustomIcons = false,
        )
        return
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.96f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Icon studio", style = MaterialTheme.typography.titleLarge)
                        Text("Draw freely or build with symbols", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Close") }
                }

                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(48) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Name this icon") }, singleLine = true,
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tool == StudioTool.DRAW,
                        onClick = { toolName = StudioTool.DRAW.name; selectedLayerId = -1 },
                        label = { Text("Draw") },
                    )
                    FilterChip(
                        selected = tool == StudioTool.MOVE,
                        onClick = { toolName = StudioTool.MOVE.name },
                        label = { Text("Move / select") },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                        else if (selectedLayer != null) {
                            layers.removeAll { it.id == selectedLayer.id }
                            selectedLayerId = -1
                        } else if (layers.isNotEmpty()) layers.removeAt(layers.lastIndex)
                    }) { Text("Undo") }
                }

                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, 380.dp)
                    BoxWithConstraints(
                        Modifier.size(side).clip(canvasShape).background(fillBrush)
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)), canvasShape),
                    ) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val widthPx = with(density) { maxWidth.toPx() }
                        val heightPx = with(density) { maxHeight.toPx() }

                        Canvas(
                            Modifier.fillMaxSize().pointerInput(tool, ink, brushSize) {
                                if (tool == StudioTool.DRAW) {
                                    var activeId: Int? = null
                                    detectDragGestures(
                                        onDragStart = { p ->
                                            val id = nextStrokeId++
                                            activeId = id
                                            strokes.add(
                                                StudioStroke(
                                                    id,
                                                    listOf(Offset((p.x / size.width).coerceIn(0f, 1f),
                                                        (p.y / size.height).coerceIn(0f, 1f))),
                                                    ink,
                                                    brushSize,
                                                ),
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val id = activeId ?: return@detectDragGestures
                                            val last = strokes.lastOrNull()
                                            if (last?.id == id && last.points.size < 4000) {
                                                val p = Offset((change.position.x / size.width).coerceIn(0f, 1f),
                                                    (change.position.y / size.height).coerceIn(0f, 1f))
                                                strokes[strokes.lastIndex] = last.copy(points = last.points + p)
                                            }
                                        },
                                        onDragEnd = { activeId = null },
                                        onDragCancel = { activeId = null },
                                    )
                                }
                            },
                        ) {
                            drawStudioStrokes(strokes, size.width, size.height)
                        }

                        layers.forEach { layer ->
                            key(layer.id) {
                                var dragX by remember(layer.id) { mutableFloatStateOf(layer.x) }
                                var dragY by remember(layer.id) { mutableFloatStateOf(layer.y) }
                                val iconPx = widthPx * layer.scale
                                val iconDp = with(density) { iconPx.toDp() }
                                Box(
                                    Modifier.offset {
                                        IntOffset((dragX * widthPx - iconPx / 2f).roundToInt(),
                                            (dragY * heightPx - iconPx / 2f).roundToInt())
                                    }.size(iconDp)
                                        .then(if (layer.id == selectedLayerId) Modifier.border(
                                            2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
                                        ) else Modifier)
                                        .pointerInput(layer.id, widthPx, heightPx) {
                                            detectDragGestures(
                                                onDragStart = { selectedLayerId = layer.id },
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    dragX = (dragX + delta.x / widthPx).coerceIn(
                                                        layer.scale / 2f, 1f - layer.scale / 2f
                                                    )
                                                    dragY = (dragY + delta.y / heightPx).coerceIn(
                                                        layer.scale / 2f, 1f - layer.scale / 2f
                                                    )
                                                    val index = layers.indexOfFirst { it.id == layer.id }
                                                    if (index >= 0) layers[index] = layers[index].copy(x = dragX, y = dragY)
                                                }
                                            )
                                        }.clickable {
                                            selectedLayerId = layer.id
                                            toolName = StudioTool.MOVE.name
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(WidgetIconCatalog.resolve(layer.key).drawableRes),
                                        contentDescription = WidgetIconCatalog.labelFor(layer.key),
                                        tint = Color(layer.color),
                                        modifier = Modifier.fillMaxSize(0.82f),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Widget shape", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WidgetShapeCatalog.all.forEach { option ->
                            FilterChip(
                                selected = shapeKey == option.key,
                                onClick = { shapeKey = option.key },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text("Gradient", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = gradient, onCheckedChange = { gradient = it })
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    studioBackgroundColors.forEach { color ->
                        Surface(
                            Modifier.size(30.dp).clickable { background = color },
                            shape = CircleShape, color = Color(color),
                            border = if (background == color) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {}
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (tool == StudioTool.DRAW) "Brush size" else "Symbol tint", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showSymbolPicker = true }) { Text("Add symbol") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    studioInkColors.forEach { color ->
                        Surface(
                            Modifier.size(30.dp).clickable {
                                ink = color
                                if (tool == StudioTool.MOVE && selectedLayer != null) {
                                    val index = layers.indexOfFirst { it.id == selectedLayer.id }
                                    if (index >= 0) layers[index] = layers[index].copy(color = color)
                                }
                            },
                            shape = CircleShape, color = Color(color),
                            border = if (ink == color) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {}
                    }
                }
                if (tool == StudioTool.DRAW) {
                    Slider(value = brushSize, onValueChange = { brushSize = it }, valueRange = 0.012f..0.11f)
                }

                if (selectedLayer != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected symbol", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            val copy = selectedLayer.copy(
                                id = nextLayerId++,
                                x = (selectedLayer.x + 0.12f).coerceAtMost(0.86f),
                                y = (selectedLayer.y + 0.12f).coerceAtMost(0.86f),
                            )
                            layers.add(copy)
                            selectedLayerId = copy.id
                        }) { Text("Duplicate") }
                        TextButton(onClick = {
                            layers.removeAll { it.id == selectedLayer.id }
                            selectedLayerId = -1
                        }) { Text("Delete") }
                    }
                    Slider(
                        value = selectedLayer.scale,
                        onValueChange = { value ->
                            val index = layers.indexOfFirst { it.id == selectedLayer.id }
                            if (index >= 0) layers[index] = layers[index].copy(scale = value)
                        },
                        valueRange = 0.12f..0.48f,
                    )
                }

                (error ?: renderError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        strokes.clear()
                        layers.clear()
                        selectedLayerId = -1
                    }) { Text("Clear canvas") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = name.isNotBlank() && !saving && (strokes.isNotEmpty() || layers.isNotEmpty()),
                        onClick = {
                            runCatching {
                                renderStudioIcon(context, shapeKey, background, gradientEnd, gradient, strokes.toList(), layers.toList())
                            }.onSuccess {
                                renderError = null
                                onSave(it, name.trim())
                            }.onFailure {
                                renderError = it.message ?: "Could not render this icon."
                            }
                        },
                    ) { Text(if (saving) "Saving…" else "Save icon") }
                }
            }
        }
    }
}

@Composable
private fun studioShape(key: String): androidx.compose.ui.graphics.Shape = when (key) {
    "circle" -> CircleShape
    "square" -> RoundedCornerShape(0.dp)
    "rounded_square" -> RoundedCornerShape(20)
    "teardrop" -> GenericShape { size, _ ->
        moveTo(size.width * 0.5f, 0f)
        cubicTo(size.width * 0.92f, 0f, size.width, size.height * 0.30f, size.width, size.height * 0.5f)
        cubicTo(size.width, size.height * 0.78f, size.width * 0.78f, size.height, size.width * 0.5f, size.height)
        cubicTo(size.width * 0.22f, size.height, 0f, size.height * 0.78f, 0f, size.height * 0.5f)
        cubicTo(0f, size.height * 0.30f, size.width * 0.08f, 0f, size.width * 0.5f, 0f)
        close()
    }
    else -> RoundedCornerShape(28)
}

private fun DrawScope.drawStudioStrokes(strokes: List<StudioStroke>, width: Float, height: Float) {
    strokes.forEach { stroke ->
        val px = stroke.width * minOf(width, height)
        if (stroke.points.size == 1) {
            drawCircle(Color(stroke.color), px / 2f,
                Offset(stroke.points[0].x * width, stroke.points[0].y * height))
        } else {
            stroke.points.zipWithNext().forEach { (a, b) ->
                drawLine(Color(stroke.color), Offset(a.x * width, a.y * height),
                    Offset(b.x * width, b.y * height), px, cap = StrokeCap.Round)
            }
        }
    }
}

private fun renderStudioIcon(
    context: Context,
    shapeKey: String,
    backgroundColor: Int,
    gradientEnd: Int,
    gradient: Boolean,
    strokes: List<StudioStroke>,
    layers: List<StudioLayer>,
): Bitmap {
    val size = 512
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val path = WidgetShapeCatalog.path(shapeKey, RectF(14f, 14f, size - 14f, size - 14f))
    canvas.save()
    canvas.clipPath(path)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
        shader = if (gradient) LinearGradient(0f, 0f, size.toFloat(), size.toFloat(),
            backgroundColor, gradientEnd, Shader.TileMode.CLAMP) else null
    }
    canvas.drawPath(path, fill)

    strokes.forEach { stroke ->
        val pathLine = AndroidPath()
        stroke.points.forEachIndexed { index, point ->
            val x = point.x * size
            val y = point.y * size
            if (index == 0) pathLine.moveTo(x, y) else pathLine.lineTo(x, y)
        }
        if (stroke.points.size == 1) {
            canvas.drawCircle(stroke.points[0].x * size, stroke.points[0].y * size, stroke.width * size / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stroke.color })
        } else {
            canvas.drawPath(pathLine, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color
                style = Paint.Style.STROKE
                strokeWidth = stroke.width * size
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
    }

    layers.forEach { layer ->
        val res = WidgetIconCatalog.resolve(layer.key).drawableRes
        val drawable = requireNotNull(context.getDrawable(res)) { "A selected symbol is unavailable." }.mutate()
        val side = (size * layer.scale).toInt().coerceIn(64, size)
        val left = (layer.x * size - side / 2f).roundToInt()
        val top = (layer.y * size - side / 2f).roundToInt()
        drawable.setTint(layer.color)
        drawable.setBounds(left, top, left + side, top + side)
        drawable.draw(canvas)
    }
    canvas.restore()

    canvas.drawPath(
        WidgetShapeCatalog.path(shapeKey, RectF(5f, 5f, size - 5f, size - 5f)),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(90, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        },
    )
    fill.shader = null
    return bitmap
}
