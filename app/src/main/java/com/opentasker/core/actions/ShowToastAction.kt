package com.opentasker.core.actions

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.opentasker.app.R
import com.opentasker.core.engine.ActionContext
import com.opentasker.core.engine.ActionResult
import com.opentasker.widget.CustomWidgetIconStore
import com.opentasker.widget.WidgetIconCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows a short Android toast. A selected icon is rendered in a custom toast only while the app
 * has a foreground activity; Android blocks custom toast views from background apps on API 30+.
 * Background runs still show a normal system text toast rather than silently losing the message.
 */
class ShowToastAction : DeclaredAction(ActionCatalog.require("toast.show")) {

    override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
        val message = args["text"].orEmpty()
        if (message.isBlank()) return ActionResult.Failure("Toast message is required")

        val iconKey = args["icon_key"]?.trim().orEmpty()
        val icon = if (iconKey.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { resolveToastIcon(ctx.app, iconKey) }
        }
        val duration = toastDurationForKey(args["duration"])

        return try {
            withContext(Dispatchers.Main.immediate) {
                val processInfo = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(processInfo)
                if (icon != null && canShowCustomIconToast(processInfo.importance)) {
                    showCustomIconToast(ctx.app, message, duration, icon)
                } else {
                    Toast.makeText(ctx.app, message, duration).show()
                }
            }
            ctx.logger("Toast displayed")
            ActionResult.Success
        } catch (error: Exception) {
            ActionResult.Failure("Could not display toast", error)
        }
    }
}

internal fun toastDurationForKey(value: String?): Int =
    if (value.equals("long", ignoreCase = true)) Toast.LENGTH_LONG else Toast.LENGTH_SHORT

internal fun canShowCustomIconToast(processImportance: Int): Boolean =
    processImportance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

private data class ResolvedToastIcon(
    val bitmap: Bitmap? = null,
    @DrawableRes val drawableRes: Int? = null,
)

private fun resolveToastIcon(context: Context, key: String): ResolvedToastIcon? {
    val customIcon = CustomWidgetIconStore.find(context, key)
    if (customIcon != null) {
        val tint = ContextCompat.getColor(context, R.color.notification_accent)
        val bitmap = CustomWidgetIconStore.loadBitmapForDisplay(
            context = context,
            key = key,
            tintColor = tint,
            maxDimensionPx = 96,
        )
        return bitmap?.let { ResolvedToastIcon(bitmap = it) }
    }

    val builtIn = WidgetIconCatalog.findBuiltIn(key) ?: return null
    return ResolvedToastIcon(drawableRes = builtIn.drawableRes)
}

@Suppress("DEPRECATION")
private fun showCustomIconToast(
    context: Context,
    message: String,
    duration: Int,
    icon: ResolvedToastIcon,
) {
    val tint = ContextCompat.getColor(context, R.color.notification_accent)
    val iconDrawable: Drawable = icon.bitmap?.let { BitmapDrawable(context.resources, it) }
        ?: icon.drawableRes?.let { resource ->
            ContextCompat.getDrawable(context, resource)?.mutate()?.apply { setTint(tint) }
        }
        ?: return Toast.makeText(context, message, duration).show()

    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(android.R.drawable.toast_frame)
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }
    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(iconDrawable)
    }
    view.addView(
        iconView,
        LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) },
    )

    val textColor = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary)).let { attributes ->
        try {
            attributes.getColor(0, Color.WHITE)
        } finally {
            attributes.recycle()
        }
    }
    val messageView = TextView(context).apply {
        text = message
        textSize = 14f
        setTextColor(textColor)
        maxLines = 2
        maxWidth = dp(280)
        ellipsize = TextUtils.TruncateAt.END
    }
    view.addView(
        messageView,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    Toast(context).apply {
        setDuration(duration)
        setView(view)
        show()
    }
}
