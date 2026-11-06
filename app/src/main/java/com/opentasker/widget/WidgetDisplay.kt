package com.opentasker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        val next = (current + delta).coerceIn(0, config.states.lastIndex)
        if (next == current) return false
        return setActiveState(context, widgetId, next)
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(widgetId))
            .remove(activeStateKey(widgetId))
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

/** Private receiver for the in-widget previous/next controls. */
class WidgetDisplayStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val operation = intent.getStringExtra(EXTRA_OPERATION).orEmpty()
        WidgetDisplayStateController.control(context, widgetId, operation)
    }

    companion object {
        const val EXTRA_OPERATION = "com.opentasker.widget.display.OPERATION"
        const val ACTION_STATE = "com.opentasker.widget.display.CHANGE_STATE"
    }
}

/**
 * Renders one page of a configurable home-screen dashboard using RemoteViews-safe layouts.
 * Each button/shortcut targets a top-level Action (Profile ID), never an Action block.
 */
object WidgetDisplayRenderer {
    private const val MAX_RENDERED_ELEMENTS = 40

    fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val config = WidgetDisplayStore.load(context, widgetId)
        val stateIndex = WidgetDisplayStore.activeIndex(context, widgetId, config.states.size)
        val state = config.states[stateIndex]
        val views = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display)
        val foreground = state.textColor
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
        views.setViewVisibility(com.opentasker.app.R.id.widget_display_title, if (state.title.isBlank()) View.GONE else View.VISIBLE)
        views.setViewVisibility(com.opentasker.app.R.id.widget_display_subtitle, if (state.subtitle.isBlank()) View.GONE else View.VISIBLE)
        views.setInt(
            com.opentasker.app.R.id.widget_display_content,
            "setOrientation",
            if (state.horizontal) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL,
        )
        views.removeAllViews(com.opentasker.app.R.id.widget_display_content)

        val elements = state.elements.take(MAX_RENDERED_ELEMENTS)
        if (elements.isEmpty()) {
            val empty = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_text_item)
            empty.setTextViewText(com.opentasker.app.R.id.widget_display_text, "Edit this state to add content and Action shortcuts.")
            empty.setTextColor(com.opentasker.app.R.id.widget_display_text, foreground)
            empty.setTextViewTextSize(com.opentasker.app.R.id.widget_display_text, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            views.addView(com.opentasker.app.R.id.widget_display_content, empty)
        } else {
            elements.forEach { element ->
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
                    }
                    WidgetDisplayElementType.DIVIDER -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_divider_item)
                    WidgetDisplayElementType.SPACER -> RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_spacer_item)
                }
                views.addView(com.opentasker.app.R.id.widget_display_content, child)
            }
        }

        val navigationVisible = config.states.size > 1
        views.setViewVisibility(com.opentasker.app.R.id.widget_display_navigation, if (navigationVisible) View.VISIBLE else View.GONE)
        if (navigationVisible) {
            views.setTextViewText(
                com.opentasker.app.R.id.widget_display_state_counter,
                "${stateIndex + 1} / ${config.states.size}",
            )
            views.setTextColor(com.opentasker.app.R.id.widget_display_state_counter, foreground)
            val canPrevious = stateIndex > 0
            val canNext = stateIndex < config.states.lastIndex
            val previous = stateChangePendingIntent(context, widgetId, "previous", 100_000 + widgetId * 2)
            val next = stateChangePendingIntent(context, widgetId, "next", 100_001 + widgetId * 2)
            views.setOnClickPendingIntent(com.opentasker.app.R.id.widget_display_previous, previous)
            views.setOnClickPendingIntent(com.opentasker.app.R.id.widget_display_next, next)
            views.setBoolean(com.opentasker.app.R.id.widget_display_previous, "setEnabled", canPrevious)
            views.setBoolean(com.opentasker.app.R.id.widget_display_next, "setEnabled", canNext)
            views.setTextColor(com.opentasker.app.R.id.widget_display_previous, foreground)
            views.setTextColor(com.opentasker.app.R.id.widget_display_next, foreground)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun stateChangePendingIntent(context: Context, widgetId: Int, operation: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetDisplayStateReceiver::class.java).apply {
            action = WidgetDisplayStateReceiver.ACTION_STATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetDisplayStateReceiver.EXTRA_OPERATION, operation)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
