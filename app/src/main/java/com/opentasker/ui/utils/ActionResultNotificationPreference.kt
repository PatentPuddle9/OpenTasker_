package com.opentasker.ui.utils

import android.content.Context

/**
 * Stores whether completion feedback for action and automation runs should be shown.
 *
 * SharedPreferences keeps the value available to short-lived widget/shortcut activities as well
 * as the main UI without blocking on a suspend DataStore read.
 */
object ActionResultNotificationPreference {
    private const val PREFERENCES_NAME = "ui_notification_preferences"
    private const val KEY_ENABLED = "action_result_notifications_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
