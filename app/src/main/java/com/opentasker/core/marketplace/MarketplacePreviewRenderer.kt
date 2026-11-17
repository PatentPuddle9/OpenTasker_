package com.opentasker.core.marketplace

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.opentasker.widget.WidgetDisplayConfig
import com.opentasker.widget.WidgetDisplayElement
import com.opentasker.widget.WidgetDisplayElementType
import kotlin.math.max
import kotlin.math.min

internal object MarketplacePreviewRenderer {
    private const val WIDTH = 960
    private const val HEIGHT = 600

    fun render(
        kind: MarketplaceContributionKind,
        name: String,
        summary: String,
        display: WidgetDisplayConfig? = null,
        iconBitmap: Bitmap? = null,
        themeMode: String = "amoled",
        themeAccent: String = "#B69CFF",
        listingBackground: String = "#6750A4",
        listingForeground: String = "#FFFFFF",
        gradient: Boolean = true,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(16, 12, 23))

        paint.color = Color.rgb(35, 27, 48)
        canvas.drawRoundRect(RectF(28f, 28f, 932f, 572f), 34f, 34f, paint)

        paint.color = Color.WHITE
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textSize = 30f
        canvas.drawText("OPENTASKER  /  MARKETPLACE PREVIEW", 64f, 82f, paint)

        when (kind) {
            MarketplaceContributionKind.WIDGET_DISPLAY -> drawWidgetDisplay(canvas, display, name)
            MarketplaceContributionKind.THEME -> drawTheme(canvas, name, themeMode, themeAccent)
            MarketplaceContributionKind.CUSTOM_WIDGET_ICON -> drawCustomIcon(canvas, name, iconBitmap, listingBackground, listingForeground, gradient)
            else -> drawPackageCard(canvas, name, summary, kind, listingBackground, listingForeground, gradient)
        }

        paint.shader = null
        paint.color = Color.rgb(177, 158, 207)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = 18f
        canvas.drawText("Preview generated from the submitted package", 64f, 536f, paint)
        return bitmap
    }

    private fun drawWidgetDisplay(canvas: Canvas, config: WidgetDisplayConfig?, packageName: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val state = config?.states?.firstOrNull()
        val left = 74f
        val top = 116f
        val right = 886f
        val bottom = 492f
        val background = state?.backgroundColor ?: Color.rgb(48, 36, 63)
        val accent = state?.accentColor ?: Color.rgb(181, 139, 239)
        paint.color = background
        if (state?.gradient == true) {
            paint.shader = LinearGradient(left, top, right, bottom, background, blend(background, Color.BLACK, 0.28f), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 30f, 30f, paint)
        paint.shader = null

        val scaleX = (right - left - 48f) / (config?.canvasWidthDp?.coerceAtLeast(1) ?: 320)
        val scaleY = (bottom - top - 48f) / (config?.canvasHeightDp?.coerceAtLeast(1) ?: 200)
        val scale = min(scaleX, scaleY)
        val offsetX = left + 24f
        val offsetY = top + 24f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = state?.textColor ?: Color.WHITE
            textSize = 24f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        if (!state?.title.isNullOrBlank()) canvas.drawText(state?.title.orEmpty().take(52), offsetX, top + 38f, titlePaint)
        val subtitlePaint = Paint(titlePaint).apply { textSize = 16f; alpha = 210 }
        if (!state?.subtitle.isNullOrBlank()) canvas.drawText(state?.subtitle.orEmpty().take(75), offsetX, top + 62f, subtitlePaint)

        state?.elements.orEmpty().forEach { element ->
            drawElement(canvas, element, offsetX, offsetY + 56f, scale, state?.textColor ?: Color.WHITE, accent)
        }
        if (state == null) {
            titlePaint.textSize = 30f
            canvas.drawText(packageName.take(44), left + 30f, top + 90f, titlePaint)
        }
    }

    private fun drawElement(
        canvas: Canvas,
        element: WidgetDisplayElement,
        originX: Float,
        originY: Float,
        scale: Float,
        textColor: Int,
        accent: Int,
    ) {
        val x = originX + element.xDp * scale
        val y = originY + element.yDp * scale
        val width = element.widthDp * scale
        val height = element.heightDp * scale
        if (width <= 0f || height <= 0f) return
        val bounds = RectF(x, y, x + width, y + height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (element.type) {
            WidgetDisplayElementType.TEXT -> {
                paint.color = textColor
                paint.textSize = (element.textSizeSp * scale).coerceIn(11f, 32f)
                paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                canvas.drawText(element.text.take(100), bounds.left, bounds.centerY() + paint.textSize * 0.32f, paint)
            }
            WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT -> {
                paint.color = accent
                canvas.drawRoundRect(bounds, min(height / 2f, 18f), min(height / 2f, 18f), paint)
                paint.color = if (Color.luminance(accent) > 0.45f) Color.BLACK else Color.WHITE
                paint.textSize = min(22f, max(12f, height * 0.42f))
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                val label = element.text.ifBlank { if (element.type == WidgetDisplayElementType.BUTTON) "Button" else "Shortcut" }.take(28)
                canvas.drawText(label, bounds.left + 14f, bounds.centerY() + paint.textSize * 0.34f, paint)
            }
            WidgetDisplayElementType.ICON -> {
                paint.color = accent
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), min(bounds.width(), bounds.height()) * 0.40f, paint)
                paint.color = Color.WHITE
                paint.textSize = min(20f, height * 0.42f)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(element.icon.take(2).uppercase(), bounds.centerX(), bounds.centerY() + paint.textSize * 0.35f, paint)
                paint.textAlign = Paint.Align.LEFT
            }
            WidgetDisplayElementType.PROGRESS -> {
                paint.color = Color.argb(72, 255, 255, 255)
                canvas.drawRoundRect(bounds, height / 2f, height / 2f, paint)
                paint.color = accent
                val progressBounds = RectF(bounds.left, bounds.top, bounds.left + bounds.width() * element.progress.coerceIn(0, 100) / 100f, bounds.bottom)
                canvas.drawRoundRect(progressBounds, height / 2f, height / 2f, paint)
            }
            WidgetDisplayElementType.DIVIDER -> {
                paint.color = Color.argb(120, textColor shr 16 and 0xFF, textColor shr 8 and 0xFF, textColor and 0xFF)
                paint.strokeWidth = max(1f, height * 0.08f)
                canvas.drawLine(bounds.left, bounds.centerY(), bounds.right, bounds.centerY(), paint)
            }
            WidgetDisplayElementType.IMAGE -> {
                paint.color = Color.argb(70, 255, 255, 255)
                canvas.drawRoundRect(bounds, 12f, 12f, paint)
                paint.color = textColor
                paint.textSize = min(16f, height * 0.38f)
                canvas.drawText("Image", bounds.left + 10f, bounds.centerY() + paint.textSize * 0.35f, paint)
            }
            WidgetDisplayElementType.SPACER -> Unit
        }
    }

    private fun drawTheme(canvas: Canvas, name: String, mode: String, accentHex: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dark = mode != "light"
        val background = if (dark) Color.rgb(14, 11, 19) else Color.rgb(247, 243, 250)
        val surface = if (dark) Color.rgb(34, 27, 45) else Color.WHITE
        val accent = parseColor(accentHex, Color.rgb(182, 156, 255))
        paint.color = background
        canvas.drawRoundRect(RectF(76f, 124f, 884f, 480f), 28f, 28f, paint)
        paint.color = surface
        canvas.drawRoundRect(RectF(108f, 166f, 852f, 452f), 24f, 24f, paint)
        paint.color = if (dark) Color.WHITE else Color.rgb(33, 26, 42)
        paint.textSize = 30f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText(name.take(36), 148f, 220f, paint)
        paint.textSize = 18f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Theme preset · ${mode.replace('_', ' ')}", 148f, 256f, paint)
        paint.color = accent
        canvas.drawRoundRect(RectF(148f, 292f, 390f, 362f), 24f, 24f, paint)
        paint.color = if (Color.luminance(accent) > 0.45f) Color.BLACK else Color.WHITE
        paint.textSize = 21f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText("Primary action", 180f, 336f, paint)
        val swatches = listOf(accent, blend(accent, Color.WHITE, 0.34f), blend(accent, Color.BLACK, 0.28f), surface, background)
        swatches.forEachIndexed { index, color ->
            paint.color = color
            val swatchX = 148f + index * 126f
            canvas.drawRoundRect(RectF(swatchX, 388f, swatchX + 96f, 424f), 12f, 12f, paint)
        }
    }

    private fun drawCustomIcon(canvas: Canvas, name: String, icon: Bitmap?, backgroundHex: String, foregroundHex: String, gradient: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val left = 310f
        val top = 126f
        val right = 650f
        val bottom = 466f
        val background = parseColor(backgroundHex, Color.rgb(103, 80, 164))
        paint.color = background
        if (gradient) {
            paint.shader = LinearGradient(left, top, right, bottom, background, blend(background, Color.BLACK, 0.25f), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 74f, 74f, paint)
        paint.shader = null
        if (icon != null) {
            val scale = min(250f / icon.width.coerceAtLeast(1), 250f / icon.height.coerceAtLeast(1))
            val w = icon.width * scale
            val h = icon.height * scale
            canvas.drawBitmap(icon, null, RectF((WIDTH - w) / 2f, (HEIGHT - h) / 2f - 14f, (WIDTH + w) / 2f, (HEIGHT + h) / 2f - 14f), paint)
        } else {
            paint.color = parseColor(foregroundHex, Color.WHITE)
            paint.textSize = 112f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(name.take(1).uppercase(), WIDTH / 2f, HEIGHT / 2f + 38f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        paint.color = Color.WHITE
        paint.textSize = 25f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText(name.take(38), 230f, 510f, paint)
    }

    private fun drawPackageCard(canvas: Canvas, name: String, summary: String, kind: MarketplaceContributionKind, backgroundHex: String, foregroundHex: String, gradient: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val background = parseColor(backgroundHex, Color.rgb(103, 80, 164))
        paint.color = background
        if (gradient) paint.shader = LinearGradient(160f, 150f, 360f, 380f, background, blend(background, Color.BLACK, 0.25f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(120f, 146f, 390f, 416f), 48f, 48f, paint)
        paint.shader = null
        paint.color = parseColor(foregroundHex, Color.WHITE)
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(name.take(1).uppercase(), 255f, 310f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        paint.textSize = 31f
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        canvas.drawText(name.take(35), 438f, 236f, paint)
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(kind.category, 438f, 274f, paint)
        paint.color = Color.rgb(197, 184, 215)
        drawWrappedText(canvas, summary, 438f, 326f, 400f, 30f, paint)
    }

    private fun drawWrappedText(canvas: Canvas, value: String, x: Float, y: Float, maxWidth: Float, lineHeight: Float, paint: Paint) {
        var line = ""
        var baseline = y
        value.split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, x, baseline, paint)
                baseline += lineHeight
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, x, baseline, paint)
    }

    private fun parseColor(value: String, fallback: Int): Int = runCatching {
        Color.parseColor(if (value.startsWith("#")) value else "#$value")
    }.getOrDefault(fallback)

    private fun blend(start: Int, end: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        fun channel(a: Int, b: Int, shift: Int) =
            (((a shr shift) and 0xFF) * (1f - t) + ((b shr shift) and 0xFF) * t).toInt()
        return Color.argb(
            channel(start, end, 24),
            channel(start, end, 16),
            channel(start, end, 8),
            channel(start, end, 0),
        )
    }
}
