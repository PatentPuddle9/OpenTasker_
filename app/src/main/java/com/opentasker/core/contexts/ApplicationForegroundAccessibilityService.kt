package com.opentasker.core.contexts

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.opentasker.core.logging.AppLogger

/**
 * Event-driven foreground-app observation for Application contexts.
 *
 * Unlike UsageStatsManager this receives the window transition from Android as it happens.
 * The service requests window-state, window-content-change, and view-focus notifications while
 * explicitly disabling window-content retrieval. It records only the package and Activity class,
 * never screen text, view labels, or window contents.
 */
class ApplicationForegroundAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnected = true
        AppLogger.info(TAG, "Instant app detection AccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceConnected = false
        AppLogger.warn(TAG, "Instant app detection AccessibilityService disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceConnected = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = ApplicationForegroundAccessibilityEventPolicy.packageName(
            eventType = event.eventType,
            packageName = event.packageName,
        ) ?: return

        // Record every valid callback, including same-package UI events. UsageStats uses this
        // timestamp to discard timeline entries already covered by the live listener while still
        // recovering transitions that Android failed to send to Accessibility.
        lastAccessibilityEventAtMs = System.currentTimeMillis()

        val previousPackage = ApplicationContextEvents.latestObservedPackage()
        // Content/focus notifications can be numerous. The app trigger is package-scoped, so
        // publishing only package changes avoids flooding every matcher with same-app UI updates.
        if (previousPackage == packageName) return

        ApplicationContextEvents.publishForeground(
            packageName = packageName,
            className = event.className?.toString(),
        )
        AppLogger.debug(
            TAG,
            "Accessibility app transition: " + (previousPackage ?: "(none)") + " -> " + packageName +
                " (eventType=${event.eventType})",
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "AppDetection"

        @Volatile
        private var serviceConnected = false

        @Volatile
        private var lastAccessibilityEventAtMs = 0L

        /** True only after Android has actually bound and connected this service instance. */
        fun isConnected(): Boolean = serviceConnected

        /** Wall-clock time of the most recent accepted callback, or zero if none has arrived. */
        fun lastEventAtMillis(): Long = lastAccessibilityEventAtMs

        /** Reads the explicit user grant shown in Android's Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, ApplicationForegroundAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry)?.let { it == component } == true
            }
        }
    }
}

/** Small policy function kept independent of the Android service lifecycle for unit testing. */
internal object ApplicationForegroundAccessibilityEventPolicy {
    fun packageName(eventType: Int, packageName: CharSequence?): String? {
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) return null

        return packageName
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
