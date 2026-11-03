package com.opentasker.widget

import android.content.Context
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.core.storage.AppDatabase
import com.opentasker.core.storage.toDomainDecodeResult
import com.opentasker.core.storage.toEntity

/**
 * A home-screen widget owns a lightweight, disabled profile so it can be organized alongside
 * automations in Actions. The profile points at the widget's task but is never a trigger-driven
 * automation; the widget remains the only owner allowed to remove it.
 */
object WidgetProfileLinks {

    suspend fun syncProfile(
        context: Context,
        widgetId: Int,
        task: Task,
        style: WidgetStyle,
    ): Long {
        val db = OpenTaskerApp_NoHilt.awaitDb()
        val dao = db.profileDao()
        val existing = dao.getByWidgetId(widgetId)
        val profile = if (existing != null) {
            val decoded = existing.toDomainDecodeResult()
            check(decoded.issue == null) {
                "The widget's linked profile could not be read safely."
            }
            decoded.value.copy(
                name = task.name,
                enabled = false,
                enterTaskId = task.id,
                contexts = emptyList(),
                contextExpression = null,
                projectId = task.projectId,
                icon = style.icon,
                widgetId = widgetId,
                group = "Shortcuts",
            )
        } else {
            Profile(
                name = task.name,
                enabled = false,
                enterTaskId = task.id,
                contexts = emptyList(),
                projectId = task.projectId,
                icon = style.icon,
                widgetId = widgetId,
                group = "Shortcuts",
            )
        }
        val profileId = if (existing == null) {
            dao.insert(profile.toEntity())
        } else {
            dao.update(profile.copy(id = existing.id).toEntity())
            existing.id
        }

        context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(TaskWidgetProvider.keyProfileId(widgetId), profileId)
            .apply()
        return profileId
    }

    /** Creates links for widgets installed before widget-owned profiles were introduced. */
    suspend fun syncExistingProfiles(context: Context, db: AppDatabase) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            android.content.ComponentName(context, TaskWidgetProvider::class.java),
        )
        val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        for (widgetId in ids) {
            val taskId = prefs.getLong(TaskWidgetProvider.keyTaskId(widgetId), -1L)
            if (taskId <= 0L) continue
            val taskEntity = db.taskDao().getById(taskId) ?: continue
            val decodedTask = taskEntity.toDomainDecodeResult()
            if (decodedTask.issue != null) continue
            val style = WidgetStyle(
                icon = prefs.getString(TaskWidgetProvider.keyIcon(widgetId), WidgetIconCatalog.default.symbol)
                    ?: WidgetIconCatalog.default.symbol,
                iconColor = prefs.getInt(TaskWidgetProvider.keyIconColor(widgetId), WidgetStyle().iconColor),
                backgroundColor = prefs.getInt(TaskWidgetProvider.keyBackgroundColor(widgetId), WidgetStyle().backgroundColor),
                gradient = prefs.getBoolean(TaskWidgetProvider.keyGradient(widgetId), WidgetStyle().gradient),
            )
            syncProfile(context, widgetId, decodedTask.value, style)
        }
    }

    suspend fun deleteProfile(context: Context, widgetId: Int) {
        val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val profileId = prefs.getLong(TaskWidgetProvider.keyProfileId(widgetId), -1L)
        val db = OpenTaskerApp_NoHilt.awaitDb()
        val dao = db.profileDao()
        val entity = if (profileId > 0L) dao.getById(profileId) else dao.getByWidgetId(widgetId)
        if (entity != null && entity.widgetId == widgetId) {
            dao.delete(entity)
        }
        prefs.edit().remove(TaskWidgetProvider.keyProfileId(widgetId)).apply()
    }
}
