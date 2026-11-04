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
        // The cached label is only ever written when the widget is configured, so a renamed task
        // showed its old name forever and a deleted one still looked runnable. Re-read the names
        // the widget claims to display; goAsync keeps the receiver alive for the query.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshCachedNames(appContext, appWidgetIds)
                runCatching { WidgetProfileLinks.syncExistingProfiles(appContext, OpenTaskerApp_NoHilt.db) }
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

        fun keyTaskId(widgetId: Int) = "widget_${widgetId}_task_id"
        fun keyTaskName(widgetId: Int) = "widget_${widgetId}_task_name"
        fun keyIcon(widgetId: Int) = "widget_${widgetId}_icon"
        fun keyIconColor(widgetId: Int) = "widget_${widgetId}_icon_color"
        fun keyBackgroundColor(widgetId: Int) = "widget_${widgetId}_background_color"
        fun keyGradient(widgetId: Int) = "widget_${widgetId}_gradient"
        fun keyShape(widgetId: Int) = "widget_${widgetId}_shape"
        fun keyProfileId(widgetId: Int) = "widget_${widgetId}_profile_id"

        /**
         * Asks every task widget to re-read its label. Call after a task rename or delete: nothing
         * else notices, because widgets render from a cache written at configuration time.
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
            val dao = runCatching { OpenTaskerApp_NoHilt.db.taskDao() }.getOrNull() ?: return
            for (widgetId in appWidgetIds) {
                val taskId = prefs.getLong(keyTaskId(widgetId), -1L)
                if (taskId < 0) continue
                val actionName = runCatching {
                    OpenTaskerApp_NoHilt.db.profileDao().getAll()
                        .asSequence()
                        .mapNotNull { entity ->
                            val decoded = entity.toDomainDecodeResult()
                            decoded.value.takeIf { decoded.issue == null }
                        }
                        .firstOrNull { it.widgetId == null && it.enterTaskId == taskId }
                        ?.name
                }.getOrNull()
                val executionName = runCatching { dao.getById(taskId) }.getOrNull()
                    ?.toDomainDecodeResult()
                    ?.value
                    ?.name
                val name = actionName ?: executionName
                if (name == null) {
                    // The task is gone. Drop the id so the widget stops offering a run that can
                    // only fail, and label it so the tap target explains itself.
                    editor.remove(keyTaskId(widgetId))
                    editor.putString(keyTaskName(widgetId), context.getString(R.string.widget_task_missing))
                } else {
                    editor.putString(keyTaskName(widgetId), name)
                }
            }
            editor.apply()
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val taskId = prefs.getLong(keyTaskId(widgetId), -1L)
            val defaultTaskName = context.getString(R.string.app_name)
            val taskName = prefs.getString(keyTaskName(widgetId), defaultTaskName) ?: defaultTaskName

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
            views.setTextViewText(R.id.widget_task_name, taskName)
            views.setTextColor(R.id.widget_task_name, WidgetBackgroundRenderer.contrastingTextColor(backgroundColor))
            views.setTextColor(R.id.widget_tap_to_run, WidgetBackgroundRenderer.contrastingTextColor(backgroundColor))

            val target = if (taskId >= 0) {
                Intent(context, TaskRunActivity::class.java).apply {
                    putExtra(TaskRunActivity.EXTRA_TASK_ID, taskId)
                    putExtra(TaskRunActivity.EXTRA_SOURCE, TaskRunActivity.SOURCE_WIDGET)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            } else {
                // No task bound: send the tap to configuration rather than leaving a dead widget.
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
