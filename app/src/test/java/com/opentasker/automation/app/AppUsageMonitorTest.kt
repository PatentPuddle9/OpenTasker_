package com.opentasker.automation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageMonitorTest {
    @Test
    fun selectLatestForegroundPackageReturnsNewestNonBlankForegroundPackage() {
        val selected = AppUsageMonitor.selectLatestForegroundPackage(
            listOf(
                ForegroundUsageEvent(packageName = "com.old", timestamp = 100L),
                ForegroundUsageEvent(packageName = "com.background", timestamp = 300L, isForeground = false),
                ForegroundUsageEvent(packageName = " ", timestamp = 400L),
                ForegroundUsageEvent(packageName = "com.new", timestamp = 200L),
            ),
        )

        assertEquals("com.new", selected)
    }

    @Test
    fun selectLatestForegroundPackageReturnsNullForNoUsableForegroundEvents() {
        val selected = AppUsageMonitor.selectLatestForegroundPackage(
            listOf(
                ForegroundUsageEvent(packageName = "", timestamp = 100L),
                ForegroundUsageEvent(packageName = "com.background", timestamp = 200L, isForeground = false),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun selectLatestForegroundEventPreservesObservedComponent() {
        val selected = AppUsageMonitor.selectLatestForegroundEvent(
            listOf(
                ForegroundUsageEvent("com.example", "com.example.OldActivity", 100L),
                ForegroundUsageEvent("com.example", "com.example.NewActivity", 200L),
            ),
        )

        assertEquals("com.example.NewActivity", selected?.className)
    }

    @Test
    fun trackerReconstructsAnAppThatWasAlreadyInTheForegroundBeforePollingStarted() {
        val tracker = ForegroundAppTracker()

        val current = tracker.apply(
            listOf(ForegroundUsageEvent("com.already.open", "com.already.open.MainActivity", 100L)),
        )

        assertEquals("com.already.open", current?.packageName)
    }

    @Test
    fun trackerClearsForegroundWhenTheCurrentActivityIsPaused() {
        val tracker = ForegroundAppTracker()
        val current = tracker.apply(
            listOf(
                ForegroundUsageEvent("com.example", "com.example.MainActivity", 100L),
                ForegroundUsageEvent("com.example", "com.example.MainActivity", 200L, isForeground = false),
            ),
        )

        assertNull(current)
    }

    @Test
    fun trackerSelectsTheMostRecentlyResumedAppAndRemovesItOnPause() {
        val tracker = ForegroundAppTracker()
        val current = tracker.apply(
            listOf(
                ForegroundUsageEvent("com.first", "com.first.MainActivity", 100L),
                ForegroundUsageEvent("com.second", "com.second.MainActivity", 200L),
            ),
        )
        assertEquals("com.second", current?.packageName)

        val afterSecondPauses = tracker.apply(
            listOf(ForegroundUsageEvent("com.second", "com.second.MainActivity", 300L, isForeground = false)),
        )
        // The state machine retains the first Activity if Android has not reported its pause yet.
        assertEquals("com.first", afterSecondPauses?.packageName)
    }

    @Test
    fun trackerPreservesOpenAndCloseTransitionsThatOccurWithinOnePoll() {
        val tracker = ForegroundAppTracker()
        tracker.apply(
            listOf(ForegroundUsageEvent("com.example", "com.example.MainActivity", 100L)),
        )

        val transitions = tracker.applyWithTransitions(
            listOf(
                ForegroundUsageEvent("com.example", "com.example.MainActivity", 200L, isForeground = false),
                ForegroundUsageEvent("com.example", "com.example.MainActivity", 300L),
            ),
        )

        assertEquals(2, transitions.size)
        assertNull(transitions[0])
        assertEquals("com.example", transitions[1]?.packageName)
    }

    @Test
    fun trackerPackageBackgroundEventClearsEveryActivityForThatPackage() {
        val tracker = ForegroundAppTracker()
        tracker.apply(
            listOf(
                ForegroundUsageEvent("com.example", "com.example.FirstActivity", 100L),
                ForegroundUsageEvent("com.example", "com.example.SecondActivity", 200L),
            ),
        )

        val current = tracker.apply(
            listOf(ForegroundUsageEvent("com.example", timestamp = 300L, isForeground = false)),
        )

        assertNull(current)
    }

    @Test
    fun missingUsageAccessPausesPollingAndRestoresItWhenPermissionReturns() {
        assertSame(AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS, appUsagePollAction(hasUsageAccess = false))
        assertSame(AppUsagePollAction.QUERY_FOREGROUND, appUsagePollAction(hasUsageAccess = true))
    }

    @Test
    fun connectedAccessibilityListenerTakesExclusivePrecedenceOverUsageStats() {
        assertSame(
            AppUsagePollAction.WAIT_FOR_ACCESSIBILITY_EVENTS,
            appUsagePollAction(hasUsageAccess = true, accessibilityServiceConnected = true),
        )
        assertSame(
            AppUsagePollAction.WAIT_FOR_ACCESSIBILITY_EVENTS,
            appUsagePollAction(hasUsageAccess = false, accessibilityServiceConnected = true),
        )
    }

    @Test
    fun usageStatsRemainsFallbackUntilAccessibilityListenerActuallyConnects() {
        assertSame(
            AppUsagePollAction.QUERY_FOREGROUND,
            appUsagePollAction(hasUsageAccess = true, accessibilityServiceConnected = false),
        )
        assertSame(
            AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS,
            appUsagePollAction(hasUsageAccess = false, accessibilityServiceConnected = false),
        )
    }

    @Test
    fun usageStatsPollingRemainsOnlyTheFallbackForEventDrivenDetection() {
        assertTrue(
            "The callback-based accessibility listener handles instant transitions; polling stays lightweight",
            AppUsageMonitor.POLL_INTERVAL_MS >= 1_000L,
        )
        assertTrue(
            "The query overlap should cover at least one full fallback poll interval",
            AppUsageMonitor.QUERY_OVERLAP_MS >= AppUsageMonitor.POLL_INTERVAL_MS,
        )
    }

}
