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
)

/** Creates a remote-view-safe rounded background bitmap; RemoteViews cannot host Compose brushes. */
object WidgetBackgroundRenderer {
    fun render(
        backgroundColor: Int,
        gradient: Boolean,
        width: Int = 360,
        height: Int = 260,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(2f, 2f, width - 2f, height - 2f)
        val radius = 64f

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
        canvas.drawRoundRect(bounds, radius, radius, fill)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(backgroundColor, Color.WHITE, 0.72f),
                92,
            )
        }
        canvas.drawRoundRect(
            RectF(4f, 4f, width - 4f, height - 4f),
            radius,
            radius,
            border,
        )
        fill.shader = null
        fill.color = Color.TRANSPARENT
        return bitmap
    }

    fun contrastingTextColor(color: Int): Int {
        val luminance = ColorUtils.calculateLuminance(color)
        return if (luminance > 0.48) Color.rgb(30, 24, 38) else Color.WHITE
    }
}
