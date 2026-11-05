package com.opentasker.automation.app

internal enum class AppUsagePollAction {
    PAUSE_FOR_MISSING_ACCESS,
    QUERY_FOREGROUND,
}

internal fun appUsagePollAction(hasUsageAccess: Boolean): AppUsagePollAction =
    if (hasUsageAccess) AppUsagePollAction.QUERY_FOREGROUND else AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS

/**
 * Replays Activity resume/pause events into the set of Activities that are currently resumed.
 * Keeping the state (rather than just the latest resume event) lets the monitor answer an
 * "already in foreground" condition even when the resume event happened before polling began.
 */
internal class ForegroundAppTracker {
    private val activeActivities = linkedMapOf<String, ForegroundUsageEvent>()

    fun apply(events: List<ForegroundUsageEvent>): ForegroundUsageEvent? {
        events.asSequence()
            .filter { it.packageName.isNotBlank() }
            .sortedBy { it.timestamp }
            .forEach { event ->
                val packageName = event.packageName.trim()
                val className = event.className.trim()
                if (event.isForeground) {
                    activeActivities[key(packageName, className)] = event
                } else if (className.isBlank()) {
                    // Legacy MOVE_TO_BACKGROUND events are package-level and clear all Activities.
                    activeActivities.keys
                        .filter { it.substringBefore(SEPARATOR) == packageName }
                        .forEach { activeActivities.remove(it) }
                } else {
                    activeActivities.remove(key(packageName, className))
                }
            }

        return activeActivities.values.maxByOrNull { it.timestamp }
    }

    fun clear() {
        activeActivities.clear()
    }

    private fun key(packageName: String, className: String): String =
        "$packageName$SEPARATOR$className"

    private companion object {
        const val SEPARATOR = '\u0000'
    }
}
