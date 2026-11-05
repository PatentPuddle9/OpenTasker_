package com.opentasker.core.contexts

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationForegroundAccessibilityEventPolicyTest {

    @Test
    fun acceptsWindowTransitionEventsAndReturnsOnlyThePackageName() {
        assertEquals(
            "com.example.target",
            ApplicationForegroundAccessibilityEventPolicy.packageName(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                " com.example.target ",
            ),
        )
        assertEquals(
            "com.example.target",
            ApplicationForegroundAccessibilityEventPolicy.packageName(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                "com.example.target",
            ),
        )
    }

    @Test
    fun ignoresUnrelatedAccessibilityEvents() {
        assertNull(
            ApplicationForegroundAccessibilityEventPolicy.packageName(
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                "com.example.target",
            ),
        )
        assertNull(
            ApplicationForegroundAccessibilityEventPolicy.packageName(
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                "com.example.target",
            ),
        )
    }

    @Test
    fun ignoresMissingOrBlankPackageNames() {
        assertNull(
            ApplicationForegroundAccessibilityEventPolicy.packageName(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                null,
            ),
        )
        assertNull(
            ApplicationForegroundAccessibilityEventPolicy.packageName(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                "  ",
            ),
        )
    }
}
