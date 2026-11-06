package com.opentasker.core.actions

import android.app.ActivityManager
import android.widget.Toast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToastActionTest {

    @Test
    fun durationDefaultsToShortAndAcceptsLong() {
        assertEquals(Toast.LENGTH_SHORT, toastDurationForKey(null))
        assertEquals(Toast.LENGTH_SHORT, toastDurationForKey(""))
        assertEquals(Toast.LENGTH_SHORT, toastDurationForKey("short"))
        assertEquals(Toast.LENGTH_LONG, toastDurationForKey("long"))
        assertEquals(Toast.LENGTH_LONG, toastDurationForKey("LONG"))
    }

    @Test
    fun selectableIconToastIsUsedOnlyForAForegroundActivity() {
        assertTrue(
            canShowCustomIconToast(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND),
        )
        assertFalse(
            canShowCustomIconToast(ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE),
        )
        assertFalse(
            canShowCustomIconToast(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE),
        )
        assertFalse(
            canShowCustomIconToast(ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND),
        )
    }

    @Test
    fun backgroundCustomToastUsesAnOverlayOnlyWhenPermissionIsGranted() {
        assertTrue(shouldUseBackgroundToastOverlay(isForeground = false, canDrawOverlays = true))
        assertFalse(shouldUseBackgroundToastOverlay(isForeground = false, canDrawOverlays = false))
        assertFalse(shouldUseBackgroundToastOverlay(isForeground = true, canDrawOverlays = true))
    }

    @Test
    fun toastOverlayDurationMatchesTheSelectedToastDuration() {
        assertEquals(2_000L, toastDurationMillisForKey(null))
        assertEquals(2_000L, toastDurationMillisForKey("short"))
        assertEquals(3_500L, toastDurationMillisForKey("long"))
    }
}
