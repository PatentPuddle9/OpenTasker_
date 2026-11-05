package com.opentasker.automation.app

internal enum class AppUsagePollAction {
    WAIT_FOR_ACCESSIBILITY_EVENTS,
    PAUSE_FOR_MISSING_ACCESS,
    QUERY_FOREGROUND,
}

/**
 * Accessibility events are the low-latency path, not a reason to turn off UsageStats.
 * Android/OEM builds can bind an AccessibilityService without reliably delivering every
 * app-window transition. When Usage Access is granted, keep the timeline monitor running as
 * a recovery path; use Accessibility by itself only when Usage Access is unavailable.
 */
internal fun appUsagePollAction(
    hasUsageAccess: Boolean,
    accessibilityServiceConnected: Boolean = false,
): AppUsagePollAction = when {
    hasUsageAccess -> AppUsagePollAction.QUERY_FOREGROUND
    accessibilityServiceConnected -> AppUsagePollAction.WAIT_FOR_ACCESSIBILITY_EVENTS
    else -> AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS
}

/**
 * A UsageStats transition at or before the latest live Accessibility callback is already
 * represented by the faster event source. Newer UsageStats events remain eligible as fallback.
 */
internal fun shouldPublishUsageTransition(
    usageEventTimestamp: Long,
    lastAccessibilityEventAtMillis: Long,
): Boolean = usageEventTimestamp > lastAccessibilityEventAtMillis

/**
 * Replays Activity resume/pause events into the set of Activities that are currently resumed.
 * Keeping the state (rather than just the latest resume event) lets the monitor answer an
 * "already in foreground" condition even when the resume event happened before polling began.
 */
internal class ForegroundAppTracker {
    private val activeActivities = linkedMapOf<String, ForegroundUsageEvent>()

    fun apply(events: List<ForegroundUsageEvent>): ForegroundUsageEvent? {
        applyWithTransitions(events)
        return current()
    }

    /**
     * Returns the foreground state after each event that changes the current Activity,
     * together with the timestamp of the event that caused the transition. A null foreground
     * represents a real transition to no resumed application Activity.
     */
    fun applyWithTransitions(events: List<ForegroundUsageEvent>): List<ForegroundAppTransition> {
        val transitions = mutableListOf<ForegroundAppTransition>()
        events.asSequence()
            .filter { it.packageName.isNotBlank() }
            .sortedBy { it.timestamp }
            .forEach { event ->
                val previous = current()
                val packageName = event.packageName.trim()
                val className = event.className.trim()
                if (event.isForeground) {
                    activeActivities[key(packageName, className)] = event
                } else if (className.isBlank()) {
                    // MOVE_TO_BACKGROUND is package-level and clears all Activities for that app.
                    activeActivities.keys
                        .filter { it.substringBefore(SEPARATOR) == packageName }
                        .forEach { activeActivities.remove(it) }
                } else {
                    activeActivities.remove(key(packageName, className))
                }

                val next = current()
                if (identity(previous) != identity(next)) {
                    transitions += ForegroundAppTransition(timestamp = event.timestamp, foreground = next)
                }
            }
        return transitions
    }

    fun current(): ForegroundUsageEvent? =
        activeActivities.values.maxByOrNull { it.timestamp }

    fun clear() {
        activeActivities.clear()
    }

    private fun identity(event: ForegroundUsageEvent?): String? =
        event?.let { key(it.packageName.trim(), it.className.trim()) }

    private fun key(packageName: String, className: String): String =
        "$packageName$SEPARATOR$className"

    private companion object {
        const val SEPARATOR = '\u0000'
    }
}

internal data class ForegroundAppTransition(
    val timestamp: Long,
    val foreground: ForegroundUsageEvent?,
)
