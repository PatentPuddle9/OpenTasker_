package com.opentasker.widget

import androidx.annotation.DrawableRes
import com.opentasker.app.R

/** A stable key and a real vector drawable shared by Compose screens and RemoteViews widgets. */
data class WidgetIconOption(
    val key: String,
    val label: String,
    @DrawableRes val drawableRes: Int,
)

object WidgetIconCatalog {
    val all: List<WidgetIconOption> = listOf(
        WidgetIconOption("bolt", "Bolt", R.drawable.ic_widget_bolt),
        WidgetIconOption("play", "Play", R.drawable.ic_widget_play),
        WidgetIconOption("pause", "Pause", R.drawable.ic_widget_pause),
        WidgetIconOption("stop", "Stop", R.drawable.ic_widget_stop),
        WidgetIconOption("refresh", "Refresh", R.drawable.ic_widget_refresh),
        WidgetIconOption("timer", "Timer", R.drawable.ic_widget_timer),
        WidgetIconOption("home", "Home", R.drawable.ic_widget_home),
        WidgetIconOption("search", "Search", R.drawable.ic_widget_search),
        WidgetIconOption("settings", "Settings", R.drawable.ic_widget_settings),
        WidgetIconOption("star", "Star", R.drawable.ic_widget_star),
        WidgetIconOption("favorite", "Favorite", R.drawable.ic_widget_favorite),
        WidgetIconOption("check", "Check", R.drawable.ic_widget_check),
        WidgetIconOption("close", "Close", R.drawable.ic_widget_close),
        WidgetIconOption("add", "Add", R.drawable.ic_widget_add),
        WidgetIconOption("mail", "Mail", R.drawable.ic_widget_mail),
        WidgetIconOption("edit", "Edit", R.drawable.ic_widget_edit),
        WidgetIconOption("delete", "Delete", R.drawable.ic_widget_delete),
        WidgetIconOption("target", "Target", R.drawable.ic_widget_target),
        WidgetIconOption("music", "Music", R.drawable.ic_widget_music),
        WidgetIconOption("cloud", "Cloud", R.drawable.ic_widget_cloud),
        WidgetIconOption("wifi", "Wi-Fi", R.drawable.ic_widget_wifi),
        WidgetIconOption("calendar", "Calendar", R.drawable.ic_widget_calendar),
        WidgetIconOption("phone", "Phone", R.drawable.ic_widget_phone),
        WidgetIconOption("folder", "Folder", R.drawable.ic_widget_folder),
    )

    val default: WidgetIconOption = all.first { it.key == "bolt" }

    private val legacyKeys = mapOf(
        "⚡︎" to "bolt", "⚡" to "bolt", "▶︎" to "play", "▶" to "play",
        "Ⅱ" to "pause", "■" to "stop", "↻" to "refresh", "⏱" to "timer", "◷" to "timer",
        "⌂" to "home", "⌕" to "search", "⚙" to "settings", "⚙︎" to "settings",
        "★" to "star", "☆" to "star", "♥︎" to "favorite", "♡︎" to "favorite",
        "✓" to "check", "✕" to "close", "＋" to "add", "✉" to "mail", "✎" to "edit",
        "⌫" to "delete", "⌖" to "target", "♫" to "music", "♪" to "music", "♬" to "music",
        "☁︎" to "cloud", "☁" to "cloud", "⌚" to "timer", "⏳" to "timer", "⌛" to "timer",
    )

    /** Converts saved glyph values from older builds to stable vector icon keys. */
    fun resolve(value: String?): WidgetIconOption {
        val key = value?.let { legacyKeys[it] ?: it }
        return all.firstOrNull { it.key == key } ?: default
    }
}
