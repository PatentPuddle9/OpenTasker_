package com.opentasker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

data class WidgetStyle(
    val icon: String = WidgetIconCatalog.default.key,
    val iconColor: Int = Color.WHITE,
    val backgroundColor: Int = Color.rgb(91, 58, 145),
    val gradient: Boolean = true,
    val shape: String = WidgetShapeCatalog.default.key,
)

/** Creates a remote-view-safe rounded background bitmap; RemoteViews cannot host Compose brushes. */
object WidgetBackgroundRenderer {
    fun render(
        backgroundColor: Int,
        gradient: Boolean,
        width: Int = 360,
        height: Int = 360,
        shape: String = WidgetShapeCatalog.default.key,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(2f, 2f, width - 2f, height - 2f)
        val path = WidgetShapeCatalog.path(shape, bounds)
        canvas.save()
        canvas.clipPath(path)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = if (gradient) {
                val end = ColorUtils.blendARGB(backgroundColor, Color.BLACK, 0.24f)
                LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    backgroundColor,
                    end,
                    Shader.TileMode.CLAMP,
                )
            } else {
                null
            }
            color = backgroundColor
        }
        canvas.drawPath(path, fill)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(backgroundColor, Color.WHITE, 0.72f),
                92,
            )
        }
        canvas.drawPath(WidgetShapeCatalog.path(shape, RectF(4f, 4f, width - 4f, height - 4f)), border)
        canvas.restore()
        fill.shader = null
        fill.color = Color.TRANSPARENT
        return bitmap
    }

    fun contrastingTextColor(color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color)
        return if (luminance > 0.48) Color.rgb(30, 24, 38) else Color.WHITE
    }
}


/** Launcher-inspired silhouettes for the task shortcut's 1x1 app-icon presentation. */
object WidgetShapeCatalog {
    data class Option(val key: String, val label: String)
    val all = listOf(
        Option("squircle", "Squircle"),
        Option("rounded_square", "Rounded square"),
        Option("circle", "Circle"),
        Option("square", "Square"),
        Option("teardrop", "Teardrop"),
    )
    val default = all.first()

    fun path(key: String, bounds: RectF): android.graphics.Path {
        val path = android.graphics.Path()
        when (key) {
            "circle" -> path.addOval(bounds, android.graphics.Path.Direction.CW)
            "square" -> path.addRect(bounds, android.graphics.Path.Direction.CW)
            "rounded_square" -> path.addRoundRect(bounds, bounds.width() * 0.20f, bounds.width() * 0.20f, android.graphics.Path.Direction.CW)
            "teardrop" -> {
                val w = bounds.width()
                val h = bounds.height()
                path.moveTo(bounds.left + w * 0.5f, bounds.top)
                path.cubicTo(bounds.left + w * 0.92f, bounds.top, bounds.right, bounds.top + h * 0.30f, bounds.right, bounds.top + h * 0.5f)
                path.cubicTo(bounds.right, bounds.top + h * 0.78f, bounds.left + w * 0.78f, bounds.bottom, bounds.left + w * 0.5f, bounds.bottom)
                path.cubicTo(bounds.left + w * 0.22f, bounds.bottom, bounds.left, bounds.top + h * 0.78f, bounds.left, bounds.top + h * 0.5f)
                path.cubicTo(bounds.left, bounds.top + h * 0.30f, bounds.left + w * 0.08f, bounds.top, bounds.left + w * 0.5f, bounds.top)
                path.close()
            }
            else -> path.addRoundRect(bounds, bounds.width() * 0.28f, bounds.width() * 0.28f, android.graphics.Path.Direction.CW)
        }
        return path
    }
}
