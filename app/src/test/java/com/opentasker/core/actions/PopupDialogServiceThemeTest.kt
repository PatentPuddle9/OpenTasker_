package com.opentasker.core.actions

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupDialogServiceThemeTest {

    @Test
    fun popupResolvesBuiltInIconKeysToBundledDrawables() {
        assertEquals(com.opentasker.app.R.drawable.ic_widget_bolt, popupBuiltInIconDrawableRes("bolt"))
        assertEquals(com.opentasker.app.R.drawable.ic_coolicon_alarm, popupBuiltInIconDrawableRes("alarm"))
    }

    @Test
    fun popupDoesNotTreatCustomKeysOrLegacyUrisAsBuiltInDrawables() {
        val customKey = com.opentasker.widget.CustomWidgetIconStore.keyForId(
            "123e4567-e89b-12d3-a456-426614174000",
        )
        assertEquals(null, popupBuiltInIconDrawableRes(customKey))
        assertEquals(null, popupBuiltInIconDrawableRes("content://media/external/images/media/42"))
    }

    @Test
    fun popupUsesTheDeviceLightDialogThemeWhenSystemIsInLightMode() {
        assertEquals(
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert,
            popupDialogThemeResource(Configuration.UI_MODE_NIGHT_NO),
        )
    }

    @Test
    fun popupUsesTheDeviceDarkDialogThemeWhenSystemIsInDarkMode() {
        assertEquals(
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            popupDialogThemeResource(Configuration.UI_MODE_NIGHT_YES),
        )
    }
}
