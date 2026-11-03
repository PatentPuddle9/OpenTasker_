package com.opentasker.widget

import android.content.Context
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
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
