package com.opentasker.automation.app

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.opentasker.core.contexts.ApplicationContextEvents
import com.opentasker.core.contexts.ApplicationForegroundAccessibilityService
import com.opentasker.core.logging.AppLogger
import com.opentasker.core.permissions.UsageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.LinkedHashSet

/**
 * Polls Android's UsageEvents timeline and reconstructs which Activity is currently resumed.
 *
 * A short "latest resumed event" lookback is not enough: if OpenTasker starts while another app
 * has already been open for more than a few seconds, that event is outside the window and the
 * application condition never becomes true. We replay a bounded startup window once, then consume
 * incremental events so the active foreground state remains known without scanning a full day
 * every poll.
 */
class AppUsageMonitor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var monitorJob: Job? = null
    private val foregroundTracker = ForegroundAppTracker()
    private val seenUsageEventKeys = LinkedHashSet<String>()
    private var lastQueriedAtMs = 0L
    private var lastForegroundIdentity: ForegroundIdentity? = null
    private var hasPublishedInitialState = false
    private var warnedMissingAccess = false
    private var lastDetectionAction: AppUsagePollAction? = null

    fun start(scope: CoroutineScope): Boolean {
        if (monitorJob?.isActive == true) return true
        resetTracking()
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val action = appUsagePollAction(
                    hasUsageAccess = UsageAccess.hasUsageStatsAccess(appContext),
                    accessibilityServiceConnected =
                        ApplicationForegroundAccessibilityService.isConnected(),
                )

                if (action != lastDetectionAction) {
                    // Never carry UsageStats' reconstructed Activity set across a switch to/from
                    // the live Accessibility callback source.
                    if (lastDetectionAction != null) clearPollingState()
                    lastDetectionAction = action
                    AppLogger.info(TAG, when (action) {
                        AppUsagePollAction.WAIT_FOR_ACCESSIBILITY_EVENTS ->
                            "Instant app detection service is connected; UsageStats polling is suspended"
                        AppUsagePollAction.QUERY_FOREGROUND ->
                            "Instant app detection service is not connected; using UsageStats as the fallback"
                        AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS ->
                            "Usage access is missing and instant app detection is not connected"
                    })
                }

                when (action) {
                    AppUsagePollAction.WAIT_FOR_ACCESSIBILITY_EVENTS -> {
                        warnedMissingAccess = false
                        delay(ACCESSIBILITY_MODE_CHECK_INTERVAL_MS)
                    }
                    AppUsagePollAction.PAUSE_FOR_MISSING_ACCESS -> {
                        if (!warnedMissingAccess) {
                            AppLogger.warn(TAG, "Usage access is not granted; app conditions are paused")
                            warnedMissingAccess = true
                        }
                        delay(MISSING_ACCESS_RETRY_MS)
                    }
                    AppUsagePollAction.QUERY_FOREGROUND -> {
                        warnedMissingAccess = false
                        pollForegroundComponent()
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        resetTracking()
    }

    private fun resetTracking() {
        clearPollingState()
        lastDetectionAction = null
        warnedMissingAccess = false
    }

    private fun clearPollingState() {
        foregroundTracker.clear()
        seenUsageEventKeys.clear()
        lastQueriedAtMs = 0L
        lastForegroundIdentity = null
        hasPublishedInitialState = false
    }

    private fun pollForegroundComponent() {
        // The Accessibility callback is authoritative from the moment Android connects it.
        // Guard both before and after the UsageStats query so a poll already in flight cannot
        // overwrite the live app-switch event while the monitor loop is changing providers.
        if (ApplicationForegroundAccessibilityService.isConnected()) return

        val now = System.currentTimeMillis()
        val initialSnapshot = !hasPublishedInitialState
        val queryStart = if (lastQueriedAtMs == 0L) {
            (now - INITIAL_LOOKBACK_MS).coerceAtLeast(0L)
        } else {
            (lastQueriedAtMs - QUERY_OVERLAP_MS).coerceAtLeast(0L)
        }
        val observedEvents = readForegroundEvents(queryStart, now) ?: return
        if (ApplicationForegroundAccessibilityService.isConnected()) return
        val newEvents = observedEvents.filter(::rememberUsageEvent)
        val transitions = foregroundTracker.applyWithTransitions(newEvents)
        val current = foregroundTracker.current()
        lastQueriedAtMs = now

        val identity = current?.let {
            ForegroundIdentity(it.packageName.trim(), it.className.trim().ifBlank { null })
        }
        if (initialSnapshot) {
            if (current == null) {
                ApplicationContextEvents.publishBackground(initial = true)
            } else {
                ApplicationContextEvents.publishForeground(
                    packageName = current.packageName,
                    className = current.className,
                    initial = true,
                )
            }
            hasPublishedInitialState = true
        } else {
            // Emit every observed transition, not just the final state for this polling interval.
            // Otherwise an app that opens and closes between polls can disappear without a pulse.
            transitions.forEach { foreground ->
                if (foreground == null) {
                    ApplicationContextEvents.publishBackground()
                } else {
                    ApplicationContextEvents.publishForeground(
                        packageName = foreground.packageName,
                        className = foreground.className,
                    )
                }
            }
        }
        if (identity != lastForegroundIdentity) {
            AppLogger.debug(
                TAG,
                "Foreground app changed: ${lastForegroundIdentity?.packageName ?: "(none)"} -> ${identity?.packageName ?: "(none)"}",
            )
            lastForegroundIdentity = identity
        }
    }

    private fun rememberUsageEvent(event: ForegroundUsageEvent): Boolean {
        val key = "${event.timestamp}|${event.packageName}|${event.className}|${event.isForeground}"
        if (!seenUsageEventKeys.add(key)) return false
        while (seenUsageEventKeys.size > MAX_SEEN_EVENTS) {
            val iterator = seenUsageEventKeys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            } else {
                break
            }
        }
        return true
    }

    private fun readForegroundEvents(startMillis: Long, endMillis: Long): List<ForegroundUsageEvent>? {
        return try {
            val usageEvents = usageStatsManager.queryEvents(startMillis, endMillis)
            val event = UsageEvents.Event()
            val foregroundEvents = mutableListOf<ForegroundUsageEvent>()

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val foreground = when {
                    isForegroundEvent(event.eventType) -> true
                    isBackgroundEvent(event.eventType) -> false
                    else -> continue
                }
                val packageName = event.packageName.orEmpty().trim()
                if (packageName.isBlank()) continue
                foregroundEvents += ForegroundUsageEvent(
                    packageName = packageName,
                    // MOVE_TO_BACKGROUND is a package-wide transition on older Android versions.
                    className = if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) "" else event.className.orEmpty(),
                    timestamp = event.timeStamp,
                    isForeground = foreground,
                )
            }

            foregroundEvents
        } catch (ex: SecurityException) {
            AppLogger.warn(TAG, "UsageStatsManager query denied; app conditions are paused", ex)
            null
        } catch (ex: RuntimeException) {
            AppLogger.error(TAG, "UsageStatsManager query failed", ex)
            null
        }
    }

    companion object {
        private const val TAG = "AppUsageMonitor"
        // Fallback only: the AccessibilityService receives the real window-transition callback.
        // Keep UsageStats polling at a lower frequency so it does not wake the engine four times a second.
        internal const val POLL_INTERVAL_MS = 1_000L
        // Recheck quickly so a service that connects after the monitor starts takes over promptly.
        private const val MISSING_ACCESS_RETRY_MS = 2_000L
        // Only checks whether the user switched detection providers; app transitions themselves
        // arrive directly from AccessibilityService and are not delayed by this interval.
        private const val ACCESSIBILITY_MODE_CHECK_INTERVAL_MS = 2_000L
        private const val INITIAL_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1_000
        // Re-read up to two poll intervals to cover UsageStats publication jitter; event keys dedupe repeats.
        internal const val QUERY_OVERLAP_MS = 2_000L
        private const val MAX_SEEN_EVENTS = 1_024

        @Suppress("DEPRECATION")
        @SuppressLint("InlinedApi")
        private fun isForegroundEvent(eventType: Int): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }

        @Suppress("DEPRECATION")
        @SuppressLint("InlinedApi")
        private fun isBackgroundEvent(eventType: Int): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                eventType == UsageEvents.Event.ACTIVITY_PAUSED
            } else {
                eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            }

        internal fun selectLatestForegroundPackage(events: List<ForegroundUsageEvent>): String? =
            selectLatestForegroundEvent(events)?.packageName

        internal fun selectLatestForegroundEvent(events: List<ForegroundUsageEvent>): ForegroundUsageEvent? =
            events
                .asSequence()
                .filter { it.isForeground && it.packageName.isNotBlank() }
                .maxByOrNull { it.timestamp }
    }
}

internal data class ForegroundUsageEvent(
    val packageName: String,
    val className: String = "",
    val timestamp: Long,
    val isForeground: Boolean = true,
)

private data class ForegroundIdentity(
    val packageName: String,
    val component: String?,
)
