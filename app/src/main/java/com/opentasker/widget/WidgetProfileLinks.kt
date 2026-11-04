package com.opentasker.widget

import android.content.Context
import androidx.room.withTransaction
import com.opentasker.app.OpenTaskerApp_NoHilt
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import com.opentasker.core.model.DEFAULT_PROJECT_ID
import com.opentasker.core.storage.toEntity

/**
 * Owns the Action/Profile created for each home-screen widget.
 *
 * A widget's backing Task is deliberately private execution storage. The Profile is the visible
 * Action and is protected by widgetId; deleting that widget removes the Action and its private
 * execution record together.
 */
object WidgetProfileLinks {

    suspend fun createOrUpdateAction(
        context: Context,
        widgetId: Int,
        name: String,
        style: WidgetStyle,
        existingProfileId: Long? = null,
    ): Long {
        val db = OpenTaskerApp_NoHilt.awaitDb()
        return db.withTransaction {
            val profileDao = db.profileDao()
            val existingEntity = existingProfileId?.takeIf { it > 0L }?.let { profileDao.getById(it) }
                ?.takeIf { it.widgetId == widgetId }
                ?: profileDao.getByWidgetId(widgetId)

            if (existingEntity != null) {
                val decoded = existingEntity.toDomainDecodeResult()
                check(decoded.issue == null) { "The widget's Action could not be read safely." }
                val existing = decoded.value
                check(existing.widgetId == widgetId) { "This Action is not owned by the selected widget." }

                val executionEntity = db.taskDao().getById(existing.enterTaskId)
                    ?: error("The widget's private Action data is missing.")
                val decodedExecution = executionEntity.toDomainDecodeResult()
                check(decodedExecution.issue == null) { "The widget's private Action data is corrupt." }
                val sharedExecution = profileDao.getAll().any { other ->
                    val otherDecoded = other.toDomainDecodeResult()
                    otherDecoded.issue == null &&
                        otherDecoded.value.id != existing.id &&
                        otherDecoded.value.enterTaskId == existing.enterTaskId
                }
                val executionId = if (sharedExecution) {
                    // Older widget-owned Profiles could point at an Action's shared execution
                    // record. Give this widget its own copy before it becomes independently owned.
                    db.taskDao().insert(decodedExecution.value.copy(id = 0L, name = name).toEntity())
                } else {
                    db.taskDao().update(decodedExecution.value.copy(name = name).toEntity())
                    existing.enterTaskId
                }

                profileDao.update(
                    existing.copy(
                        name = name,
                        enabled = false,
                        enterTaskId = executionId,
                        icon = WidgetIconCatalog.resolve(style.icon).key,
                        contexts = emptyList(),
                        contextExpression = null,
                    ).toEntity(),
                )
                existing.id
            } else {
                val execution = Task(name = name, projectId = DEFAULT_PROJECT_ID)
                val executionId = db.taskDao().insert(execution.toEntity())
                val action = Profile(
                    name = name,
                    enabled = false,
                    contexts = emptyList(),
                    enterTaskId = executionId,
                    projectId = DEFAULT_PROJECT_ID,
                    icon = WidgetIconCatalog.resolve(style.icon).key,
                    widgetId = widgetId,
                    group = "Shortcuts",
                )
                profileDao.insert(action.toEntity())
            }
        }
    }

    /**
     * Cleans up the temporary shadow Profile made by pre-migration builds without deleting the
     * original Action's shared execution data.
     */
    suspend fun deleteLegacyShadowProfile(context: Context, widgetId: Int) {
        val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val profileId = prefs.getLong(TaskWidgetProvider.keyProfileId(widgetId), -1L)
        val db = OpenTaskerApp_NoHilt.awaitDb()
        val dao = db.profileDao()
        val byId = if (profileId > 0L) dao.getById(profileId) else null
        val entity = byId?.takeIf { it.widgetId == widgetId } ?: dao.getByWidgetId(widgetId)
        if (entity != null && entity.widgetId == widgetId) {
            dao.deleteWidgetOwnedProfile(widgetId)
        }
        prefs.edit().remove(TaskWidgetProvider.keyProfileId(widgetId)).apply()
    }

    /** Removes the widget-owned Action and its private backing execution record. */
    suspend fun deleteProfile(context: Context, widgetId: Int) {
        val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val actionId = prefs.getLong(TaskWidgetProvider.keyActionProfileId(widgetId), -1L)
        val legacyProfileId = prefs.getLong(TaskWidgetProvider.keyProfileId(widgetId), -1L)
        val db = OpenTaskerApp_NoHilt.awaitDb()

        db.withTransaction {
            val dao = db.profileDao()
            val byActionId = if (actionId > 0L) dao.getById(actionId) else null
            val byLegacyId = if (legacyProfileId > 0L) dao.getById(legacyProfileId) else null
            val entity = byActionId?.takeIf { it.widgetId == widgetId }
                ?: byLegacyId?.takeIf { it.widgetId == widgetId }
                ?: dao.getByWidgetId(widgetId)

            if (entity != null && entity.widgetId == widgetId) {
                val decoded = entity.toDomainDecodeResult()
                val executionId = if (decoded.issue == null) {
                    decoded.value.enterTaskId.takeIf { it > 0L }
                } else {
                    null
                }

                dao.deleteWidgetOwnedProfile(widgetId)

                if (executionId != null) {
                    val sharedByAnotherProfile = dao.getAll().any { other ->
                        val otherDecoded = other.toDomainDecodeResult()
                        otherDecoded.issue == null &&
                            otherDecoded.value.id != decoded.value.id &&
                            otherDecoded.value.enterTaskId == executionId
                    }
                    if (!sharedByAnotherProfile) {
                        db.taskDao().getById(executionId)?.let { db.taskDao().delete(it) }
                    }
                }
            }
        }

        prefs.edit()
            .remove(TaskWidgetProvider.keyActionProfileId(widgetId))
            .remove(TaskWidgetProvider.keyProfileId(widgetId))
            .remove(TaskWidgetProvider.keyTaskId(widgetId))
            .remove(TaskWidgetProvider.keyActionName(widgetId))
            .remove(TaskWidgetProvider.keyIcon(widgetId))
            .remove(TaskWidgetProvider.keyIconColor(widgetId))
            .remove(TaskWidgetProvider.keyBackgroundColor(widgetId))
            .remove(TaskWidgetProvider.keyGradient(widgetId))
            .remove(TaskWidgetProvider.keyShape(widgetId))
            .apply()
    }

    /** Removes the Action/configuration from OpenTasker while leaving a clear launcher placeholder. */
    suspend fun removeFromShortcuts(context: Context, widgetId: Int) {
        deleteProfile(context, widgetId)
        context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(TaskWidgetProvider.keyRemoved(widgetId), true)
            .apply()
    }
}
