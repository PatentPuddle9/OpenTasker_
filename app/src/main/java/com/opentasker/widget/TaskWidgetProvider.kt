package com.opentasker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
        // Re-read the selected Action name and migrate older widgets away from task IDs and
        // widget-owned shadow profiles. goAsync keeps the receiver alive for the database work.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshCachedNames(appContext, appWidgetIds)
                for (widgetId in appWidgetIds) {
                    updateWidget(appContext, appWidgetManager, widgetId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Re-select the compact or expanded RemoteViews layout whenever the launcher resizes it.
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for (widgetId in appWidgetIds) {
                    runCatching { WidgetProfileLinks.deleteProfile(appContext, widgetId) }
                }
            } finally {
                pending.finish()
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove(keyTaskId(id))
            editor.remove(keyTaskName(id))
            editor.remove(keyIcon(id))
            editor.remove(keyIconColor(id))
            editor.remove(keyBackgroundColor(id))
            editor.remove(keyGradient(id))
            editor.remove(keyShape(id))
        }
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "opentasker_widget_prefs"
        private const val EXPANDED_LAYOUT_MIN_SIZE_DP = 110

        /** Legacy-only key used to migrate widgets saved by versions that stored Task IDs. */
        fun keyTaskId(widgetId: Int) = "widget_${widgetId}_task_id"
        fun keyTaskName(widgetId: Int) = "widget_${widgetId}_task_name"
        fun keyActionProfileId(widgetId: Int) = "widget_${widgetId}_action_profile_id"
        fun keyIcon(widgetId: Int) = "widget_${widgetId}_icon"
        fun keyIconColor(widgetId: Int) = "widget_${widgetId}_icon_color"
        fun keyBackgroundColor(widgetId: Int) = "widget_${widgetId}_background_color"
        fun keyGradient(widgetId: Int) = "widget_${widgetId}_gradient"
        fun keyShape(widgetId: Int) = "widget_${widgetId}_shape"
        fun keyProfileId(widgetId: Int) = "widget_${widgetId}_profile_id"

        /**
         * Asks every Action widget to re-read its label after an Action rename or deletion.
         */
        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TaskWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }

        private suspend fun refreshCachedNames(context: Context, appWidgetIds: IntArray) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            val db = runCatching { OpenTaskerApp_NoHilt.awaitDb() }.getOrNull() ?: return
            val decodedProfiles = db.profileDao().getAll().mapNotNull { entity ->
                val decoded = entity.toDomainDecodeResult()
                decoded.value.takeIf { decoded.issue == null }
            }

            for (widgetId in appWidgetIds) {
                // Remove the shadow Profile created by older widget builds. A widget now points
                // straight at the user's original top-level Action instead of owning a duplicate.
                runCatching { WidgetProfileLinks.deleteProfile(context, widgetId) }

                val storedProfileId = prefs.getLong(keyActionProfileId(widgetId), -1L)
                val selectedAction = decodedProfiles.firstOrNull {
                    it.id == storedProfileId && it.widgetId == null && it.enterTaskId > 0L
                }

                if (selectedAction != null) {
                    editor.putString(keyTaskName(widgetId), selectedAction.name)
                    editor.remove(keyTaskId(widgetId))
                    continue
                }

                // One-time migration for widgets created before they stored an Action/Profile ID.
                // Only migrate when the old execution ID maps unambiguously to a visible Action.
                val legacyTaskId = prefs.getLong(keyTaskId(widgetId), -1L)
                val legacyName = prefs.getString(keyTaskName(widgetId), null)
                val candidates = decodedProfiles.filter {
                    it.widgetId == null && it.enterTaskId == legacyTaskId && it.enterTaskId > 0L
                }
                val migratedAction = candidates.firstOrNull { it.name == legacyName }
                    ?: candidates.singleOrNull()

                if (migratedAction != null) {
                    editor.putLong(keyActionProfileId(widgetId), migratedAction.id)
                    editor.putString(keyTaskName(widgetId), migratedAction.name)
                } else {
                    editor.remove(keyActionProfileId(widgetId))
                    editor.putString(keyTaskName(widgetId), context.getString(R.string.widget_task_missing))
                }
                editor.remove(keyTaskId(widgetId))
            }
            editor.apply()
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val actionProfileId = prefs.getLong(keyActionProfileId(widgetId), -1L)
            val defaultActionName = context.getString(R.string.app_name)
            val actionName = prefs.getString(keyTaskName(widgetId), defaultActionName) ?: defaultActionName

            val icon = WidgetIconCatalog.resolve(
                prefs.getString(keyIcon(widgetId), WidgetIconCatalog.default.key),
            )
            val iconColor = prefs.getInt(keyIconColor(widgetId), WidgetStyle().iconColor)
            val backgroundColor = prefs.getInt(keyBackgroundColor(widgetId), WidgetStyle().backgroundColor)
            val gradient = prefs.getBoolean(keyGradient(widgetId), WidgetStyle().gradient)
            val shape = prefs.getString(keyShape(widgetId), WidgetShapeCatalog.default.key) ?: WidgetShapeCatalog.default.key

            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val isCompact = minWidth < EXPANDED_LAYOUT_MIN_SIZE_DP ||
                minHeight < EXPANDED_LAYOUT_MIN_SIZE_DP
            val layoutRes = if (isCompact) R.layout.widget_task_compact else R.layout.widget_task
            val views = RemoteViews(context.packageName, layoutRes)
            views.setImageViewBitmap(
                R.id.widget_background,
                WidgetBackgroundRenderer.render(backgroundColor, gradient, shape = shape),
            )
            views.setImageViewResource(R.id.widget_icon, icon.drawableRes)
            views.setInt(R.id.widget_icon, "setColorFilter", iconColor)
            views.setTextViewText(R.id.widget_task_name, actionName)
            views.setTextColor(R.id.widget_task_name, WidgetBackgroundRenderer.contrastingTextColor(backgroundColor))
            views.setTextColor(R.id.widget_tap_to_run, WidgetBackgroundRenderer.contrastingTextColor(backgroundColor))

            val target = if (actionProfileId > 0L) {
                Intent(context, TaskRunActivity::class.java).apply {
                    putExtra(TaskRunActivity.EXTRA_PROFILE_ID, actionProfileId)
                    putExtra(TaskRunActivity.EXTRA_SOURCE, TaskRunActivity.SOURCE_WIDGET)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            } else {
                // No Action bound: send the tap to configuration rather than leaving a dead widget.
                Intent(context, TaskWidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
