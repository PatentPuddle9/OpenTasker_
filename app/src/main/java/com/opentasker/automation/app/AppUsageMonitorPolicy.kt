package com.opentasker.automation.app

internal enum class AppUsagePollAction {
    WAIT_FOR_ACCESSIBILITY_EVENTS,
    PAUSE_FOR_MISSING_ACCESS,
    QUERY_FOREGROUND,
}

/**
 * There must be exactly one foreground-app authority at a time. Only a live, system-connected
 * AccessibilityService takes precedence over UsageStats. A setting that merely says "enabled"
 * does not prove Android has actually bound the service.
 */
internal fun appUsagePollAction(
    hasUsageAccess: Boolean,
    accessibilityServiceConnected: Boolean = false,
): AppUsagePollAction = when {
    accessibilityServiceConnected -> AppUsagePollAction.WAIT_FOR_ACCESSIBILITY_EVENTS
    hasUsageAccess -> AppUsagePollAction.QUERY_FOREGROUND
    else -> AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS
}

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
     * Returns the foreground state after each event that changes the current Activity.
     * A null entry represents a real transition to no resumed application Activity.
     */
    fun applyWithTransitions(events: List<ForegroundUsageEvent>): List<ForegroundUsageEvent?> {
        val transitions = mutableListOf<ForegroundUsageEvent?>()
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
                if (identity(previous) != identity(next)) transitions += next
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
