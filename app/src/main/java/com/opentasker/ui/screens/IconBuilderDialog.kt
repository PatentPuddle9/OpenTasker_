package com.opentasker.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.opentasker.widget.WidgetIconCatalog

private val iconBuilderSwatches = listOf(
    "6750A4", "8E78D8", "006C5B", "1565C0",
    "C45500", "263238", "DCE2FF", "F4D8E8",
)

@Composable
internal fun IconBuilderDialog(
    onDismiss: () -> Unit,
    saving: Boolean,
    error: String?,
    onSave: (Bitmap, String) -> Unit,
) {
    val context = LocalContext.current
    var showGlyphPicker by rememberSaveable { mutableStateOf(false) }
    var iconName by rememberSaveable { mutableStateOf("") }
    var glyphKey by rememberSaveable { mutableStateOf("bolt") }
    var shapeKey by rememberSaveable { mutableStateOf("Squircle") }
    var backgroundHex by rememberSaveable { mutableStateOf("6750A4") }
    var gradientHex by rememberSaveable { mutableStateOf("8E78D8") }
    var foregroundHex by rememberSaveable { mutableStateOf("FFFFFF") }
    var outlineHex by rememberSaveable { mutableStateOf("FFFFFF") }
    var useGradient by rememberSaveable { mutableStateOf(true) }
    var useOutline by rememberSaveable { mutableStateOf(false) }
    var glyphScale by rememberSaveable { mutableFloatStateOf(62f) }
    var renderError by rememberSaveable { mutableStateOf<String?>(null) }

    val backgroundColor = parseIconBuilderColor(backgroundHex)
    val gradientColor = parseIconBuilderColor(gradientHex)
    val foregroundColor = parseIconBuilderColor(foregroundHex)
    val outlineColor = parseIconBuilderColor(outlineHex)
    val colorsValid = backgroundColor != null && foregroundColor != null &&
        (!useGradient || gradientColor != null) && (!useOutline || outlineColor != null)
    val selectedGlyph = WidgetIconCatalog.resolve(glyphKey)

    if (showGlyphPicker) {
        ActionIconPickerDialog(
            selectedIcon = glyphKey,
            onIconSelected = {
                glyphKey = it
                renderError = null
            },
            onDismiss = { showGlyphPicker = false },
            includeCustomIcons = false,
        )
        return
    }

    val previewShape = when (shapeKey) {
        "Circle" -> CircleShape
        "Square" -> RoundedCornerShape(8.dp)
        "Rounded" -> RoundedCornerShape(22.dp)
        else -> RoundedCornerShape(32.dp)
    }
    val previewBrush = if (useGradient && backgroundColor != null && gradientColor != null) {
        Brush.linearGradient(listOf(backgroundColor, gradientColor))
    } else {
        SolidColor(backgroundColor ?: MaterialTheme.colorScheme.primary)
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Icon builder") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(116.dp),
                        shape = previewShape,
                        color = Color.Transparent,
                        border = if (useOutline && outlineColor != null) {
                            BorderStroke(3.dp, outlineColor)
                        } else {
                            null
                        },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(previewBrush, previewShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(selectedGlyph.drawableRes),
                                contentDescription = selectedGlyph.label,
                                tint = foregroundColor ?: MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size((116f * glyphScale / 100f).dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = iconName,
                    onValueChange = { iconName = it.take(48) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Icon name") },
                    placeholder = { Text("e.g. Smart Home") },
                    singleLine = true,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Symbol", style = MaterialTheme.typography.labelLarge)
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showGlyphPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(selectedGlyph.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(selectedGlyph.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Background shape", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("Circle", "Rounded", "Squircle", "Square").forEach { option ->
                            FilterChip(
                                selected = shapeKey == option,
                                onClick = { shapeKey = option },
                                label = { Text(option) },
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Background color", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        iconBuilderSwatches.forEach { swatch ->
                            val swatchColor = parseIconBuilderColor(swatch) ?: Color.Gray
                            Surface(
                                modifier = Modifier.size(30.dp).clickable { backgroundHex = swatch },
                                shape = CircleShape,
                                color = swatchColor,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {}
                        }
                    }
                    BuilderColorField(
                        label = "Background hex",
                        value = backgroundHex,
                        onValueChange = {
                            backgroundHex = it.filter { ch -> ch.isLetterOrDigit() || ch == '#' }.take(9)
                        },
                    )
                }

                BuilderColorField(
                    label = "Symbol color",
                    value = foregroundHex,
                    onValueChange = {
                        foregroundHex = it.filter { ch -> ch.isLetterOrDigit() || ch == '#' }.take(9)
                    },
                )

                BuilderSwitchRow(
                    title = "Gradient background",
                    checked = useGradient,
                    onCheckedChange = { useGradient = it },
                )
                if (useGradient) {
                    BuilderColorField(
                        label = "Gradient end color",
                        value = gradientHex,
                        onValueChange = {
                            gradientHex = it.filter { ch -> ch.isLetterOrDigit() || ch == '#' }.take(9)
                        },
                    )
                }

                BuilderSwitchRow(
                    title = "Outline",
                    checked = useOutline,
                    onCheckedChange = { useOutline = it },
                )
                if (useOutline) {
                    BuilderColorField(
                        label = "Outline color",
                        value = outlineHex,
                        onValueChange = {
                            outlineHex = it.filter { ch -> ch.isLetterOrDigit() || ch == '#' }.take(9)
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Symbol size · " + glyphScale.toInt() + "%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = glyphScale,
                        onValueChange = { glyphScale = it },
                        valueRange = 42f..76f,
                    )
                }

                (error ?: renderError)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!colorsValid) {
                    Text(
                        "Check the color values. Use #RRGGBB or #AARRGGBB.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = iconName.isNotBlank() && colorsValid && !saving,
                onClick = {
                    val bg = backgroundColor ?: return@Button
                    val fg = foregroundColor ?: return@Button
                    val end = if (useGradient) gradientColor ?: return@Button else bg
                    val stroke = if (useOutline) outlineColor ?: return@Button else null
                    runCatching {
                        renderBuiltIcon(
                            context = context,
                            glyphRes = selectedGlyph.drawableRes,
                            shapeKey = shapeKey,
                            backgroundColor = bg,
                            gradientColor = end,
                            foregroundColor = fg,
                            outlineColor = stroke,
                            glyphScale = glyphScale / 100f,
                            useGradient = useGradient,
                        )
                    }.onSuccess { bitmap ->
                        renderError = null
                        onSave(bitmap, iconName.trim())
                    }.onFailure {
                        renderError = it.message ?: "Could not build this icon."
                    }
                },
            ) {
                Text(if (saving) "Saving…" else "Save icon")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun BuilderColorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text("#RRGGBB or #AARRGGBB") },
        singleLine = true,
    )
}

@Composable
private fun BuilderSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun parseIconBuilderColor(value: String): Color? {
    val raw = value.trim().removePrefix("#")
    if (raw.length != 6 && raw.length != 8) return null
    val parsed = runCatching { AndroidColor.parseColor("#" + raw) }.getOrNull() ?: return null
    return Color(parsed)
}

private fun renderBuiltIcon(
    context: Context,
    glyphRes: Int,
    shapeKey: String,
    backgroundColor: Color,
    gradientColor: Color,
    foregroundColor: Color,
    outlineColor: Color?,
    glyphScale: Float,
    useGradient: Boolean,
): Bitmap {
    val size = 512
    val bounds = RectF(16f, 16f, size - 16f, size - 16f)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor.toArgb()
        style = Paint.Style.FILL
        if (useGradient) {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                backgroundColor.toArgb(),
                gradientColor.toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
    }

    fun drawShape(paint: Paint) {
        when (shapeKey) {
            "Circle" -> canvas.drawOval(bounds, paint)
            "Square" -> canvas.drawRoundRect(bounds, 8f, 8f, paint)
            "Rounded" -> canvas.drawRoundRect(bounds, 92f, 92f, paint)
            else -> canvas.drawRoundRect(bounds, 150f, 150f, paint)
        }
    }

    drawShape(backgroundPaint)
    outlineColor?.let { color ->
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        drawShape(outlinePaint)
    }

    val drawable = requireNotNull(context.getDrawable(glyphRes)) {
        "The selected symbol is unavailable."
    }.mutate()
    drawable.setTint(foregroundColor.toArgb())
    val glyphSize = (size * glyphScale).toInt().coerceIn(180, 420)
    val glyphInset = (size - glyphSize) / 2
    drawable.setBounds(glyphInset, glyphInset, glyphInset + glyphSize, glyphInset + glyphSize)
    drawable.draw(canvas)
    backgroundPaint.shader = null
    return bitmap
}
