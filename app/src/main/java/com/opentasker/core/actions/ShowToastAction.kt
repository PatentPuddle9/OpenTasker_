package com.opentasker.core.actions

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.res.Configuration
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
 * Shows a toast with an optional selected icon.
 *
 * Android 11+ does not display app-provided custom Toast views from the background. When the app
 * is backgrounded and Display over other apps is already granted, use a short-lived, non-touchable
 * TYPE_APPLICATION_OVERLAY window that looks like the same toast. Otherwise fall back to Android's
 * standard text toast; Android supplies the app icon in that case.
 */
class ShowToastAction : DeclaredAction(ActionCatalog.require("toast.show")) {

    override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
        val message = args["text"].orEmpty()
        if (message.isBlank()) return ActionResult.Failure("Toast message is required")

        return try {
            val toastContext = createDeviceToastContext(ctx.app)
            val iconKey = args["icon_key"]?.trim().orEmpty()
            val icon = if (iconKey.isBlank()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching { resolveToastIcon(toastContext, iconKey) }.getOrNull()
                }
            }
            val duration = toastDurationForKey(args["duration"])

            withContext(Dispatchers.Main.immediate) {
                val processInfo = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(processInfo)
                val isForeground = canShowCustomIconToast(processInfo.importance)
                var customIconShown = false

                if (icon != null && isForeground) {
                    customIconShown = showCustomIconToast(toastContext, message, duration, icon)
                } else if (icon != null && canUseBackgroundToastOverlay(toastContext, isForeground)) {
                    customIconShown = showCustomIconToastOverlay(toastContext, message, duration, icon)
                    if (!customIconShown) {
                        ctx.logger("Background toast overlay could not be shown; using Android's standard toast")
                    }
                } else if (icon != null && !isForeground) {
                    ctx.logger("Selected toast icon needs Display over other apps access when OpenTasker is in the background")
                }

                if (!customIconShown) {
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

internal fun toastDurationMillisForKey(value: String?): Long =
    if (value.equals("long", ignoreCase = true)) 3_500L else 2_000L

internal fun canShowCustomIconToast(processImportance: Int): Boolean =
    processImportance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

internal fun shouldUseBackgroundToastOverlay(
    isForeground: Boolean,
    canDrawOverlays: Boolean,
): Boolean = !isForeground && canDrawOverlays

internal fun canUseBackgroundToastOverlay(
    context: Context,
    isForeground: Boolean,
): Boolean = shouldUseBackgroundToastOverlay(
    isForeground = isForeground,
    canDrawOverlays = !isForeground && runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false),
)

private data class ResolvedToastIcon(
    val bitmap: Bitmap? = null,
    @DrawableRes val drawableRes: Int? = null,
)

/**
 * Use Android's device-default day/night palette for a toast, independent of OpenTasker's
 * Compose-only theme selection. API 29+ has a native DayNight device theme; older supported
 * versions select its light or dark counterpart from the current system UI mode.
 */
internal fun createDeviceToastContext(context: Context): Context {
    val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val themeRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.R.style.Theme_DeviceDefault_DayNight
    } else if (isNight) {
        android.R.style.Theme_DeviceDefault
    } else {
        android.R.style.Theme_DeviceDefault_Light
    }
    return ContextThemeWrapper(context, themeRes)
}

internal fun toastOverlayAnimationStyle(): Int = android.R.style.Animation_Toast

internal fun resolveToastThemeColor(
    context: Context,
    attribute: Int,
    fallback: Int,
): Int {
    val value = TypedValue()
    if (!context.theme.resolveAttribute(attribute, value, true)) return fallback
    return when {
        value.resourceId != 0 -> runCatching {
            ContextCompat.getColor(context, value.resourceId)
        }.getOrDefault(fallback)
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
        else -> fallback
    }
}

private fun resolveToastIcon(context: Context, key: String): ResolvedToastIcon? {
    val tint = resolveToastThemeColor(
        context,
        android.R.attr.colorAccent,
        ContextCompat.getColor(context, R.color.notification_accent),
    )
    val customIcon = CustomWidgetIconStore.find(context, key)
    if (customIcon != null) {
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

private fun resolveToastIconDrawable(context: Context, icon: ResolvedToastIcon): Drawable? {
    val tint = resolveToastThemeColor(
        context,
        android.R.attr.colorAccent,
        ContextCompat.getColor(context, R.color.notification_accent),
    )
    return icon.bitmap?.let { BitmapDrawable(context.resources, it) }
        ?: icon.drawableRes?.let { resource ->
            ContextCompat.getDrawable(context, resource)?.mutate()?.apply { setTint(tint) }
        }
}

private fun buildCustomToastView(
    context: Context,
    message: String,
    icon: ResolvedToastIcon,
): View? {
    val iconDrawable = resolveToastIconDrawable(context, icon) ?: return null
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val fallbackSurface = ContextCompat.getColor(
        context,
        if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            android.R.color.background_dark
        } else {
            android.R.color.background_light
        },
    )
    val surfaceColor = resolveToastThemeColor(
        context,
        android.R.attr.colorBackgroundFloating,
        fallbackSurface,
    )
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(surfaceColor)
            cornerRadius = dp(28).toFloat()
        }
        elevation = dp(8).toFloat()
        setPadding(dp(16), dp(12), dp(18), dp(12))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = message
    }
    val iconView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageDrawable(iconDrawable)
        contentDescription = null
    }
    view.addView(
        iconView,
        LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) },
    )

    val textColor = resolveToastThemeColor(
        context,
        android.R.attr.textColorPrimary,
        if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            Color.WHITE
        } else {
            Color.BLACK
        },
    )
    val messageView = TextView(context).apply {
        text = message
        textSize = 14f
        setTextColor(textColor)
        maxLines = 2
        maxWidth = dp(280)
        ellipsize = TextUtils.TruncateAt.END
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    view.addView(
        messageView,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )
    return view
}

private fun showCustomIconToast(
    context: Context,
    message: String,
    duration: Int,
    icon: ResolvedToastIcon,
): Boolean {
    val view = buildCustomToastView(context, message, icon) ?: return false
    @Suppress("DEPRECATION")
    Toast(context).apply {
        setDuration(duration)
        setView(view)
        show()
    }
    return true
}

private fun showCustomIconToastOverlay(
    context: Context,
    message: String,
    duration: Int,
    icon: ResolvedToastIcon,
): Boolean {
    val view = buildCustomToastView(context, message, icon) ?: return false
    return ToastOverlayWindow.show(context, view, toastDurationMillisForKey(if (duration == Toast.LENGTH_LONG) "long" else "short"))
}

/**
 * Background Toast replacement. It is deliberately non-touchable and is only used when the user
 * has already granted Android's explicit Display over other apps special access.
 */
private object ToastOverlayWindow {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentWindowManager: WindowManager? = null
    private var dismissRunnable: Runnable? = null

    fun show(context: Context, view: View, durationMillis: Long): Boolean {
        val appContext = context.applicationContext
        if (!runCatching { Settings.canDrawOverlays(appContext) }.getOrDefault(false)) return false
        val manager = appContext.getSystemService(WindowManager::class.java) ?: return false

        removeCurrent()
        val density = appContext.resources.displayMetrics.density
        val bottomOffset = (64 * density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomOffset
            // Reuse Android's own toast enter/exit window animation rather than approximating it.
            windowAnimations = toastOverlayAnimationStyle()
        }

        if (!runCatching { manager.addView(view, params) }.isSuccess) return false
        currentView = view
        currentWindowManager = manager
        val dismiss = Runnable {
            if (currentView === view) removeCurrent()
        }
        dismissRunnable = dismiss
        mainHandler.postDelayed(dismiss, durationMillis)
        return true
    }

    private fun removeCurrent() {
        dismissRunnable?.let(mainHandler::removeCallbacks)
        dismissRunnable = null
        val view = currentView
        val manager = currentWindowManager
        currentView = null
        currentWindowManager = null
        if (view != null && manager != null) {
            runCatching {
                if (view.isAttachedToWindow) manager.removeView(view)
            }
        }
    }
}
