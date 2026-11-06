package com.opentasker.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class WidgetDisplayElementType {
    TEXT, BUTTON, SHORTCUT, ICON, PROGRESS, DIVIDER, SPACER,
}

@Serializable
data class WidgetDisplayElement(
    val id: String,
    val type: WidgetDisplayElementType,
    val text: String = "",
    val actionProfileId: Long? = null,
    val icon: String = "bolt",
    val progress: Int = 50,
    val textSizeSp: Int = 14,
)

@Serializable
data class WidgetDisplayConfig(
    val title: String = "Widget Display",
    val subtitle: String = "Your home-screen dashboard",
    val backgroundColor: Int = 0xFF30243F.toInt(),
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val accentColor: Int = 0xFFB58BEF.toInt(),
    val gradient: Boolean = true,
    val horizontal: Boolean = false,
    val elements: List<WidgetDisplayElement> = emptyList(),
)

object WidgetDisplayStore {
    private const val PREFS = "opentasker_widget_display_prefs"
    fun key(widgetId: Int) = "widget_display_${widgetId}_config"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(context: Context, widgetId: Int): WidgetDisplayConfig {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)
            ?: return WidgetDisplayConfig()
        return runCatching { json.decodeFromString<WidgetDisplayConfig>(raw) }
            .getOrElse { WidgetDisplayConfig() }
    }

    fun save(context: Context, widgetId: Int, config: WidgetDisplayConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(widgetId), json.encodeToString(config))
            .apply()
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(widgetId)).apply()
    }
}

/**
 * Renders a configurable home-screen dashboard using RemoteViews-safe layouts.
 * Each button/shortcut points at an existing top-level Action (Profile ID), never at an
 * individual Action block or its private backing execution record.
 */
object WidgetDisplayRenderer {
    private const val MAX_ELEMENTS = 18

    fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val config = WidgetDisplayStore.load(context, widgetId)
        val views = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display)
        val foreground = config.textColor
        views.setImageViewBitmap(
            com.opentasker.app.R.id.widget_display_background,
            WidgetBackgroundRenderer.render(
                backgroundColor = config.backgroundColor,
                gradient = config.gradient,
                width = 720,
                height = 520,
                shape = "rounded_square",
            ),
        )
        views.setTextViewText(com.opentasker.app.R.id.widget_display_title, config.title)
        views.setTextViewText(com.opentasker.app.R.id.widget_display_subtitle, config.subtitle)
        views.setTextColor(com.opentasker.app.R.id.widget_display_title, foreground)
        views.setTextColor(com.opentasker.app.R.id.widget_display_subtitle, foreground)
        if (config.title.isBlank()) {
            views.setViewVisibility(com.opentasker.app.R.id.widget_display_title, View.GONE)
        }
        if (config.subtitle.isBlank()) {
            views.setViewVisibility(com.opentasker.app.R.id.widget_display_subtitle, View.GONE)
        }
        views.setInt(
            com.opentasker.app.R.id.widget_display_content,
            "setOrientation",
            if (config.horizontal) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL,
        )
        views.removeAllViews(com.opentasker.app.R.id.widget_display_content)

        val elements = config.elements.take(MAX_ELEMENTS)
        if (elements.isEmpty()) {
            val empty = RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_text_item)
            empty.setTextViewText(com.opentasker.app.R.id.widget_display_text, "Edit this widget to add content and Action shortcuts.")
            empty.setTextColor(com.opentasker.app.R.id.widget_display_text, foreground)
            empty.setTextViewTextSize(com.opentasker.app.R.id.widget_display_text, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            views.addView(com.opentasker.app.R.id.widget_display_content, empty)
        } else {
            elements.forEachIndexed { index, element ->
                val child = when (element.type) {
                    WidgetDisplayElementType.TEXT -> {
                        RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_text_item).apply {
                            setTextViewText(com.opentasker.app.R.id.widget_display_text, element.text)
                            setTextColor(com.opentasker.app.R.id.widget_display_text, foreground)
                            setTextViewTextSize(
                                com.opentasker.app.R.id.widget_display_text,
                                android.util.TypedValue.COMPLEX_UNIT_SP,
                                element.textSizeSp.coerceIn(10, 32).toFloat(),
                            )
                        }
                    }
                    WidgetDisplayElementType.BUTTON, WidgetDisplayElementType.SHORTCUT -> {
                        val layout = if (element.type == WidgetDisplayElementType.SHORTCUT) {
                            com.opentasker.app.R.layout.widget_display_shortcut_item
                        } else {
                            com.opentasker.app.R.layout.widget_display_button_item
                        }
                        RemoteViews(context.packageName, layout).apply {
                            val icon = WidgetIconCatalog.resolve(element.icon)
                            setImageViewResource(com.opentasker.app.R.id.widget_display_action_icon, icon.drawableRes)
                            setInt(com.opentasker.app.R.id.widget_display_action_icon, "setColorFilter", config.textColor)
                            setTextViewText(com.opentasker.app.R.id.widget_display_action_label, element.text)
                            setTextColor(com.opentasker.app.R.id.widget_display_action_label, foreground)
                            if (element.actionProfileId != null && element.actionProfileId > 0L) {
                                val run = Intent(context, TaskRunActivity::class.java).apply {
                                    putExtra(TaskRunActivity.EXTRA_PROFILE_ID, element.actionProfileId)
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
                    WidgetDisplayElementType.ICON -> {
                        RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_icon_item).apply {
                            val icon = WidgetIconCatalog.resolve(element.icon)
                            setImageViewResource(com.opentasker.app.R.id.widget_display_large_icon, icon.drawableRes)
                            setInt(com.opentasker.app.R.id.widget_display_large_icon, "setColorFilter", config.accentColor)
                            setTextViewText(com.opentasker.app.R.id.widget_display_icon_label, element.text)
                            setTextColor(com.opentasker.app.R.id.widget_display_icon_label, foreground)
                        }
                    }
                    WidgetDisplayElementType.PROGRESS -> {
                        RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_progress_item).apply {
                            setTextViewText(com.opentasker.app.R.id.widget_display_progress_label, element.text)
                            setTextColor(com.opentasker.app.R.id.widget_display_progress_label, foreground)
                            setTextViewText(com.opentasker.app.R.id.widget_display_progress_value, "${element.progress.coerceIn(0, 100)}%")
                            setTextColor(com.opentasker.app.R.id.widget_display_progress_value, foreground)
                            setProgressBar(
                                com.opentasker.app.R.id.widget_display_progress_bar,
                                100,
                                element.progress.coerceIn(0, 100),
                                false,
                            )
                        }
                    }
                    WidgetDisplayElementType.DIVIDER ->
                        RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_divider_item)
                    WidgetDisplayElementType.SPACER ->
                        RemoteViews(context.packageName, com.opentasker.app.R.layout.widget_display_spacer_item)
                }
                views.addView(com.opentasker.app.R.id.widget_display_content, child)
            }
        }
        manager.updateAppWidget(widgetId, views)
    }
}
