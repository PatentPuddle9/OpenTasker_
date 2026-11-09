package com.opentasker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.opentasker.core.scenes.SceneImageLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class WidgetDisplayElementType {
    TEXT, IMAGE, BUTTON, SHORTCUT, ICON, PROGRESS, DIVIDER, SPACER,
}

@Serializable
data class WidgetDisplayElement(
    val id: String = UUID.randomUUID().toString(),
    val type: WidgetDisplayElementType,
    val text: String = "",
    val actionProfileId: Long? = null,
    val icon: String = "bolt",
    val imageUri: String = "",
    val progress: Int = 50,
    val textSizeSp: Int = 14,
)

@Serializable
data class WidgetDisplayState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "State",
    val title: String = "Widget Display",
    val subtitle: String = "",
    val backgroundColor: Int = 0xFF30243F.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFFB58BEF.toInt(),
    val gradient: Boolean = true,
    val horizontal: Boolean = false,
    val elements: List<WidgetDisplayElement> = emptyList(),
)

@Serializable
data class WidgetDisplayConfig(
    val states: List<WidgetDisplayState> = listOf(WidgetDisplayState(name = "State 1")),
)

object WidgetDisplayStore {
    const val MAX_STATES = 75
    private const val PREFS = "opentasker_widget_display_prefs"
    fun key(widgetId: Int) = "widget_display_${widgetId}_config"
    private fun activeStateKey(widgetId: Int) = "widget_display_${widgetId}_active_state"
    private fun removedKey(widgetId: Int) = "widget_display_${widgetId}_removed"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context, widgetId: Int): WidgetDisplayConfig {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)
            ?: return WidgetDisplayConfig()
        return runCatching {
            json.decodeFromString<WidgetDisplayConfig>(raw)
                .let { config ->
                    config.copy(states = config.states.take(MAX_STATES).ifEmpty { listOf(WidgetDisplayState(name = "State 1")) })
                }
        }.getOrElse { WidgetDisplayConfig() }
    }

    fun save(context: Context, widgetId: Int, config: WidgetDisplayConfig) {
        require(config.states.isNotEmpty()) { "A Widget Display needs at least one state." }
        require(config.states.size <= MAX_STATES) { "A Widget Display can contain at most $MAX_STATES states." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(widgetId), json.encodeToString(config))
            .putInt(activeStateKey(widgetId), activeIndex(context, widgetId, config.states.size))
            .putBoolean(removedKey(widgetId), false)
            .apply()
    }

    fun activeIndex(context: Context, widgetId: Int, stateCount: Int = load(context, widgetId).states.size): Int {
        val index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(activeStateKey(widgetId), 0)
        return index.coerceIn(0, (stateCount - 1).coerceAtLeast(0))
    }

    fun setActiveState(context: Context, widgetId: Int, index: Int): Boolean {
        val config = load(context, widgetId)
        if (index !in config.states.indices) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(activeStateKey(widgetId), index)
            .apply()
        return true
    }

    fun moveState(context: Context, widgetId: Int, delta: Int): Boolean {
        val config = load(context, widgetId)
        val current = activeIndex(context, widgetId, config.states.size)
        val next = (current + delta + config.states.size) % config.states.size
        if (next == current) return false
        return setActiveState(context, widgetId, next)
    }

    fun isRemoved(context: Context, widgetId: Int): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(removedKey(widgetId), false)

    /** Remove the configuration from OpenTasker while leaving a clear launcher placeholder. */
    fun markRemoved(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(widgetId))
            .remove(activeStateKey(widgetId))
            .putBoolean(removedKey(widgetId), true)
            .apply()
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(widgetId))
            .remove(activeStateKey(widgetId))
            .remove(removedKey(widgetId))
            .apply()
    }
}

object WidgetDisplayStateController {
    fun control(context: Context, widgetId: Int, operation: String, stateIndex: Int? = null): Boolean {
        if (!WidgetDisplayProvider.isWidgetId(context, widgetId)) return false
        val changed = when (operation.trim().lowercase()) {
            "next" -> WidgetDisplayStore.moveState(context, widgetId, 1)
            "previous", "prev" -> WidgetDisplayStore.moveState(context, widgetId, -1)
            "show", "select" -> stateIndex?.let { WidgetDisplayStore.setActiveState(context, widgetId, it - 1) } ?: false
            else -> false
        }
        if (changed || operation.equals("refresh", ignoreCase = true)) {
            WidgetDisplayRenderer.updateWidget(context, AppWidgetManager.getInstance(context), widgetId)
            return true
        }
        return false
    }
}

/**
 * Renders one page of a configurable home-screen dashboard using RemoteViews-safe layouts.
 * Each button/shortcut targets a top-level Action (Profile ID), never an Action block.
 */
object WidgetDisplayRenderer {
    fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        if (WidgetDisplayStore.isRemoved(context, widgetId)) {
            val placeholder = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display)
            placeholder.setImageViewBitmap(
                com.opentasker.app.R.id.widget_display_background,
                WidgetBackgroundRenderer.render(
                    backgroundColor = 0xFF30243F.toInt(),
                    gradient = true,
                    width = 720,
                    height = 520,
                    shape = "rounded_square",
                ),
            )
            placeholder.setTextViewText(com.opentasker.app.R.id.widget_display_title, "Widget removed")
            placeholder.setTextViewText(
                com.opentasker.app.R.id.widget_display_subtitle,
                "Remove this widget from your home screen",
            )
            placeholder.setTextColor(com.opentasker.app.R.id.widget_display_title, android.graphics.Color.WHITE)
            placeholder.setTextColor(com.opentasker.app.R.id.widget_display_subtitle, android.graphics.Color.WHITE)
            placeholder.removeAllViews(com.opentasker.app.R.id.widget_display_content)
            manager.updateAppWidget(widgetId, placeholder)
            return
        }
        val config = WidgetDisplayStore.load(context, widgetId)
        val stateIndex = WidgetDisplayStore.activeIndex(context, widgetId, config.states.size)
        val state = config.states[stateIndex]
        val options = manager.getAppWidgetOptions(widgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val compactHeight = minHeight in 1..109
        val veryCompactHeight = minHeight in 1..69
        val narrowWidth = minWidth in 1..149
        val views = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display)
        val foreground = state.textColor
        val panelPaddingDp = if (compactHeight) 6 else 12
        val panelPadding = (panelPaddingDp * context.resources.displayMetrics.density).toInt()
        views.setViewPadding(
            com.opentasker.app.R.id.widget_display_panel,
            panelPadding,
            panelPadding,
            panelPadding,
            panelPadding,
        )
        views.setImageViewBitmap(
            com.opentasker.app.R.id.widget_display_background,
            WidgetBackgroundRenderer.render(
                backgroundColor = state.backgroundColor,
                gradient = state.gradient,
                width = 720,
                height = 520,
                shape = "rounded_square",
            ),
        )
        views.setTextViewText(com.opentasker.app.R.id.widget_display_title, state.title)
        views.setTextViewText(com.opentasker.app.R.id.widget_display_subtitle, state.subtitle)
        views.setTextColor(com.opentasker.app.R.id.widget_display_title, foreground)
        views.setTextColor(com.opentasker.app.R.id.widget_display_subtitle, foreground)
        views.setTextViewTextSize(
            com.opentasker.app.R.id.widget_display_title,
            android.util.TypedValue.COMPLEX_UNIT_SP,
            if (narrowWidth) 13f else 17f,
        )
        views.setViewVisibility(
            com.opentasker.app.R.id.widget_display_title,
            if (state.title.isBlank() || veryCompactHeight) View.GONE else View.VISIBLE,
        )
        views.setViewVisibility(
            com.opentasker.app.R.id.widget_display_subtitle,
            if (state.subtitle.isBlank() || compactHeight) View.GONE else View.VISIBLE,
        )
        // Keep compact widgets vertical by default. Forcing a horizontal row used to make
        // each match-parent child consume the entire widget width, hiding everything after it.
        // User-selected horizontal layouts use explicit child widths on API 31+.
        val renderHorizontally = (state.horizontal || veryCompactHeight) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            minWidth > 0
        views.setInt(
            com.opentasker.app.R.id.widget_display_content,
            "setOrientation",
            if (renderHorizontally) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL,
        )
        views.removeAllViews(com.opentasker.app.R.id.widget_display_content)

        // Spacer and divider elements are purely decorative; collapse them on the shortest
        // 2x1 layout so real content such as shortcuts and progress indicators remains visible.
        val elements = state.elements
        val renderedElements = if (veryCompactHeight) {
            elements.filterNot {
                it.type == WidgetDisplayElementType.SPACER || it.type == WidgetDisplayElementType.DIVIDER
            }
        } else {
            elements
        }
        if (renderedElements.isEmpty()) {
            val empty = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_text_item)
            empty.setTextViewText(com.opentasker.app.R.id.widget_display_text, "Add text, images, or Action shortcuts to this state.")
            empty.setTextColor(com.opentasker.app.R.id.widget_display_text, foreground)
            empty.setTextViewTextSize(com.opentasker.app.R.id.widget_display_text, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            views.addView(com.opentasker.app.R.id.widget_display_content, empty)
        } else {
            val spacerCount = renderedElements.count { it.type == WidgetDisplayElementType.SPACER }
            val dividerCount = renderedElements.count { it.type == WidgetDisplayElementType.DIVIDER }
            val contentCount = renderedElements.count {
                it.type != WidgetDisplayElementType.SPACER && it.type != WidgetDisplayElementType.DIVIDER
            }.coerceAtLeast(1)
            val reservedWidth = spacerCount * 6f + dividerCount * 1f
            val availableWidth = (minWidth - panelPaddingDp * 2f - reservedWidth).coerceAtLeast(contentCount * 28f)
            val slotWidth = availableWidth / contentCount

            renderedElements.forEach { element ->
                val child = when (element.type) {
                    WidgetDisplayElementType.TEXT -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_text_item).apply {
                        setTextViewText(com.opentasker.app.R.id.widget_display_text, element.text)
                        setTextColor(com.opentasker.app.R.id.widget_display_text, foreground)
                        setTextViewTextSize(
                            com.opentasker.app.R.id.widget_display_text,
                            android.util.TypedValue.COMPLEX_UNIT_SP,
                            element.textSizeSp.coerceIn(10, 36).toFloat(),
                        )
                    }
                    WidgetDisplayElementType.IMAGE -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_image_item).apply {
                        val bitmap = SceneImageLoader.load(
                            context = context,
                            source = element.imageUri,
                            targetWidthPx = 640,
                            targetHeightPx = 360,
                        )
                        if (bitmap != null) {
                            setImageViewBitmap(com.opentasker.app.R.id.widget_display_image, bitmap)
                            setViewVisibility(com.opentasker.app.R.id.widget_display_image, View.VISIBLE)
                        } else {
                            setViewVisibility(com.opentasker.app.R.id.widget_display_image, View.GONE)
                            setViewVisibility(com.opentasker.app.R.id.widget_display_image_fallback, View.VISIBLE)
                            setTextViewText(com.opentasker.app.R.id.widget_display_image_fallback, "Image unavailable")
                        }
                        setTextViewText(com.opentasker.app.R.id.widget_display_image_caption, element.text)
                        setTextColor(com.opentasker.app.R.id.widget_display_image_caption, foreground)
                    }
                    WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT -> {
                        val layout = if (element.type == WidgetDisplayElementType.SHORTCUT) {
                            com.opentasker.app.R.layout.widget_display_shortcut_item
                        } else {
                            com.opentasker.app.R.layout.widget_display_button_item
                        }
                        RemoteViews(context.packageName, layout).apply {
                            val customIcon = CustomWidgetIconStore.loadBitmapForWidget(context, element.icon, state.accentColor)
                            if (customIcon != null) {
                                setImageViewBitmap(com.opentasker.app.R.id.widget_display_action_icon, customIcon)
                            } else {
                                val icon = WidgetIconCatalog.resolve(element.icon)
                                setImageViewResource(com.opentasker.app.R.id.widget_display_action_icon, icon.drawableRes)
                                setInt(com.opentasker.app.R.id.widget_display_action_icon, "setColorFilter", state.accentColor)
                            }
                            setTextViewText(com.opentasker.app.R.id.widget_display_action_label, element.text)
                            setTextColor(com.opentasker.app.R.id.widget_display_action_label, foreground)
                            if (compactHeight) {
                                setTextViewTextSize(com.opentasker.app.R.id.widget_display_action_label, TypedValue.COMPLEX_UNIT_SP, 10f)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    setViewLayoutWidth(com.opentasker.app.R.id.widget_display_action_icon, 18f, TypedValue.COMPLEX_UNIT_DIP)
                                    setViewLayoutHeight(com.opentasker.app.R.id.widget_display_action_icon, 18f, TypedValue.COMPLEX_UNIT_DIP)
                                }
                            }
                            element.actionProfileId?.takeIf { it > 0L }?.let { profileId ->
                                val run = Intent(context, TaskRunActivity::class.java).apply {
                                    putExtra(TaskRunActivity.EXTRA_PROFILE_ID, profileId)
                                    putExtra(TaskRunActivity.EXTRA_SOURCE, TaskRunActivity.SOURCE_WIDGET_DISPLAY)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                val requestCode = ((widgetId.toLong() shl 20) xor element.id.hashCode().toLong()).toInt()
                                val pendingIntent = PendingIntent.getActivity(
                                    context,
                                    requestCode,
                                    run,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                )
                                val rootId = if (element.type == WidgetDisplayElementType.SHORTCUT) {
                                    com.opentasker.app.R.id.widget_display_shortcut_root
                                } else {
                                    com.opentasker.app.R.id.widget_display_action_root
                                }
                                setOnClickPendingIntent(rootId, pendingIntent)
                            }
                        }
                    }
                    WidgetDisplayElementType.ICON -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_icon_item).apply {
                        val customIcon = CustomWidgetIconStore.loadBitmapForWidget(context, element.icon, state.accentColor)
                        if (customIcon != null) {
                            setImageViewBitmap(com.opentasker.app.R.id.widget_display_large_icon, customIcon)
                        } else {
                            val icon = WidgetIconCatalog.resolve(element.icon)
                            setImageViewResource(com.opentasker.app.R.id.widget_display_large_icon, icon.drawableRes)
                            setInt(com.opentasker.app.R.id.widget_display_large_icon, "setColorFilter", state.accentColor)
                        }
                        setTextViewText(com.opentasker.app.R.id.widget_display_icon_label, element.text)
                        setTextColor(com.opentasker.app.R.id.widget_display_icon_label, foreground)
                    }
                    WidgetDisplayElementType.PROGRESS -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_progress_item).apply {
                        setTextViewText(com.opentasker.app.R.id.widget_display_progress_label, element.text)
                        setTextColor(com.opentasker.app.R.id.widget_display_progress_label, foreground)
                        setTextViewText(com.opentasker.app.R.id.widget_display_progress_value, "${element.progress.coerceIn(0, 100)}%")
                        setTextColor(com.opentasker.app.R.id.widget_display_progress_value, foreground)
                        setProgressBar(com.opentasker.app.R.id.widget_display_progress_bar, 100, element.progress.coerceIn(0, 100), false)
                        if (compactHeight) {
                            setTextViewTextSize(com.opentasker.app.R.id.widget_display_progress_label, TypedValue.COMPLEX_UNIT_SP, 10f)
                            setTextViewTextSize(com.opentasker.app.R.id.widget_display_progress_value, TypedValue.COMPLEX_UNIT_SP, 10f)
                        }
                    }
                    WidgetDisplayElementType.DIVIDER -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_divider_item)
                    WidgetDisplayElementType.SPACER -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_spacer_item)
                }
                if (renderHorizontally) {
                    val rootId = rootViewId(element.type)
                    val widthDp = when (element.type) {
                        WidgetDisplayElementType.SPACER -> 6f
                        WidgetDisplayElementType.DIVIDER -> 1f
                        else -> slotWidth
                    }
                    child.setViewLayoutWidth(rootId, widthDp.coerceAtLeast(1f), TypedValue.COMPLEX_UNIT_DIP)
                    if (element.type == WidgetDisplayElementType.DIVIDER) {
                        child.setViewLayoutHeight(rootId, 28f, TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
                views.addView(com.opentasker.app.R.id.widget_display_content, child)
            }
        }

        manager.updateAppWidget(widgetId, views)
    }

    private fun rootViewId(type: WidgetDisplayElementType): Int = when (type) {
        WidgetDisplayElementType.TEXT -> com.opentasker.app.R.id.widget_display_text
        WidgetDisplayElementType.IMAGE -> com.opentasker.app.R.id.widget_display_image_item_root
        WidgetDisplayElementType.BUTTON -> com.opentasker.app.R.id.widget_display_action_root
        WidgetDisplayElementType.SHORTCUT -> com.opentasker.app.R.id.widget_display_shortcut_root
        WidgetDisplayElementType.ICON -> com.opentasker.app.R.id.widget_display_icon_root
        WidgetDisplayElementType.PROGRESS -> com.opentasker.app.R.id.widget_display_progress_root
        WidgetDisplayElementType.DIVIDER -> com.opentasker.app.R.id.widget_display_divider
        WidgetDisplayElementType.SPACER -> com.opentasker.app.R.id.widget_display_spacer
    }
}
