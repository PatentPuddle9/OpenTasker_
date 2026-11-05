package com.opentasker.core.actions

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupDialogServiceThemeTest {

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
