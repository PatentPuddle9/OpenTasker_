package com.opentasker.widget

import android.content.Context
import com.opentasker.app.OpenTaskerApp_NoHilt

/**
 * Removes duplicate widget-owned Profiles created by older versions.
 *
 * New widgets reference an existing top-level Action/Profile directly and never create their own
 * Profile entry. This cleanup remains so upgrading or removing an old widget cannot leave an
 * orphaned shadow Profile behind.
 */
object WidgetProfileLinks {

    suspend fun deleteProfile(context: Context, widgetId: Int) {
        val prefs = context.getSharedPreferences(TaskWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        val profileId = prefs.getLong(TaskWidgetProvider.keyProfileId(widgetId), -1L)
        val db = OpenTaskerApp_NoHilt.awaitDb()
        val dao = db.profileDao()
        val entityById = if (profileId > 0L) dao.getById(profileId) else null
        val entity = entityById?.takeIf { it.widgetId == widgetId } ?: dao.getByWidgetId(widgetId)
        if (entity != null && entity.widgetId == widgetId) {
            dao.deleteWidgetOwnedProfile(widgetId)
        }
        prefs.edit().remove(TaskWidgetProvider.keyProfileId(widgetId)).apply()
    }
}
