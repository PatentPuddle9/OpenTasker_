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
        // Keep the visible label aligned with the widget-owned Action and migrate widgets made
        // before each widget had its own protected Action.
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
            editor.remove(keyActionProfileId(id))
            editor.remove(keyActionName(id))
            editor.remove(keyIcon(id))
            editor.remove(keyIconColor(id))
            editor.remove(keyBackgroundColor(id))
            editor.remove(keyGradient(id))
            editor.remove(keyShape(id))
            editor.remove(keyRemoved(id))
            editor.remove(keyProfileId(id))
        }
        editor.apply()
    }

    companion object {
        const val PREFS_NAME = "opentasker_widget_prefs"
        private const val EXPANDED_LAYOUT_MIN_SIZE_DP = 110

        /** Old-version key retained only so installed widgets can be migrated. */
        fun keyTaskId(widgetId: Int) = "widget_${widgetId}_task_id"
        /** Backing key name retained for existing installations. */
        fun keyActionName(widgetId: Int) = "widget_${widgetId}_task_name"
        fun keyTaskName(widgetId: Int) = keyActionName(widgetId)
        fun keyActionProfileId(widgetId: Int) = "widget_${widgetId}_action_profile_id"
        fun keyIcon(widgetId: Int) = "widget_${widgetId}_icon"
        fun keyIconColor(widgetId: Int) = "widget_${widgetId}_icon_color"
        fun keyBackgroundColor(widgetId: Int) = "widget_${widgetId}_background_color"
        fun keyGradient(widgetId: Int) = "widget_${widgetId}_gradient"
        fun keyShape(widgetId: Int) = "widget_${widgetId}_shape"
        fun keyProfileId(widgetId: Int) = "widget_${widgetId}_profile_id"
        fun keyRemoved(widgetId: Int) = "widget_${widgetId}_removed_from_shortcuts"

        fun requestRefresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
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
                if (prefs.getBoolean(keyRemoved(widgetId), false)) continue

                // Delete only the pre-migration shadow row. This deliberately does not delete
                // any backing sequence because older shadows could share a user's Action data.
                runCatching { WidgetProfileLinks.deleteLegacyShadowProfile(context, widgetId) }

                val storedProfileId = prefs.getLong(keyActionProfileId(widgetId), -1L)
                // Recover an already-created widget Action even if its preference ID was not
                // persisted (for example, if Android restarted the process during first setup).
                val widgetAction = decodedProfiles.firstOrNull {
                    it.id == storedProfileId && it.widgetId == widgetId && it.enterTaskId > 0L
                } ?: decodedProfiles.firstOrNull {
                    it.widgetId == widgetId && it.enterTaskId > 0L
                }
                if (widgetAction != null) {
                    editor.putLong(keyActionProfileId(widgetId), widgetAction.id)
                    editor.putString(keyActionName(widgetId), widgetAction.name)
                    editor.remove(keyTaskId(widgetId))
                    continue
                }

                // Upgrade widgets that previously pointed at a user's shared Action. The new
                // widget receives its own copy of the Action's steps and its own protected Profile.
                // For a brand-new widget, sourceAction is null: still create its empty private
                // Action now, during the provider's first update, rather than waiting for Save
                // in the configuration screen.
                val directAction = decodedProfiles.firstOrNull {
                    it.id == storedProfileId && it.widgetId == null && it.enterTaskId > 0L
                }
                val legacyTaskId = prefs.getLong(keyTaskId(widgetId), -1L)
                val legacyName = prefs.getString(keyActionName(widgetId), null)
                val legacyCandidates = decodedProfiles.filter {
                    it.widgetId == null && it.enterTaskId == legacyTaskId && legacyTaskId > 0L
                }
                val sourceAction = directAction
                    ?: legacyCandidates.firstOrNull { it.name == legacyName }
                    ?: legacyCandidates.singleOrNull()
                val style = WidgetStyle(
                    icon = prefs.getString(keyIcon(widgetId), WidgetIconCatalog.default.key)
                        ?: WidgetIconCatalog.default.key,
                    iconColor = prefs.getInt(keyIconColor(widgetId), WidgetStyle().iconColor),
                    backgroundColor = prefs.getInt(keyBackgroundColor(widgetId), WidgetStyle().backgroundColor),
                    gradient = prefs.getBoolean(keyGradient(widgetId), WidgetStyle().gradient),
                    shape = prefs.getString(keyShape(widgetId), WidgetShapeCatalog.default.key)
                        ?: WidgetShapeCatalog.default.key,
                )
                val actionName = sourceAction?.name
                    ?: legacyName?.takeIf { it.isNotBlank() && legacyTaskId > 0L }
                    ?: "Widget $widgetId"
                val ownedId = WidgetProfileLinks.createOrUpdateAction(
                    context = context,
                    widgetId = widgetId,
                    name = actionName,
                    style = style,
                    sourceProfileId = sourceAction?.id,
                    sourceExecutionId = legacyTaskId.takeIf { sourceAction == null && it > 0L },
                )
                val owned = db.profileDao().getById(ownedId)?.toDomainDecodeResult()?.value
                editor.putLong(keyActionProfileId(widgetId), ownedId)
                editor.putString(keyActionName(widgetId), owned?.name ?: actionName)
                editor.remove(keyTaskId(widgetId))
            }
            editor.apply()
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val removedFromShortcuts = prefs.getBoolean(keyRemoved(widgetId), false)
            val actionProfileId = prefs.getLong(keyActionProfileId(widgetId), -1L)
            val defaultActionName = if (removedFromShortcuts) {
                context.getString(R.string.widget_removed_from_shortcuts)
            } else {
                context.getString(R.string.app_name)
            }
            val actionName = prefs.getString(keyActionName(widgetId), defaultActionName) ?: defaultActionName

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
            val isCompact = minWidth < EXPANDED_LAYOUT_MIN_SIZE_DP || minHeight < EXPANDED_LAYOUT_MIN_SIZE_DP
            val layoutRes = if (isCompact) R.layout.widget_task_compact else R.layout.widget_task
            val views = RemoteViews(context.packageName, layoutRes)
            views.setImageViewBitmap(
                R.id.widget_background,
                WidgetBackgroundRenderer.render(backgroundColor, gradient, shape = shape),
            )
            views.setImageViewResource(R.id.widget_icon, icon.drawableRes)
            views.setInt(R.id.widget_icon, "setColorFilter", iconColor)
            views.setTextViewText(R.id.widget_task_name, actionName)
            if (removedFromShortcuts) {
                views.setTextViewText(
                    R.id.widget_tap_to_run,
                    context.getString(R.string.widget_removed_from_shortcuts_subtitle),
                )
            }
            views.setTextColor(R.id.widget_task_name, WidgetBackgroundRenderer.contrastingTextColor(backgroundColor))
            views.setTextColor(R.id.widget_tap_to_run, WidgetBackgroundRenderer.contrastingTextColor(backgroundColor))

            val target = when {
                removedFromShortcuts -> null
                actionProfileId > 0L -> Intent(context, TaskRunActivity::class.java).apply {
                    putExtra(TaskRunActivity.EXTRA_PROFILE_ID, actionProfileId)
                    putExtra(TaskRunActivity.EXTRA_SOURCE, TaskRunActivity.SOURCE_WIDGET)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                else -> Intent(context, TaskWidgetConfigActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            }
            val pendingIntent = target?.let {
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
