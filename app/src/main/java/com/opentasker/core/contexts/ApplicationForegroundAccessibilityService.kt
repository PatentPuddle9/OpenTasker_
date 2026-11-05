package com.opentasker.core.contexts

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.opentasker.core.logging.AppLogger

/**
 * Event-driven foreground-app observation for Application contexts.
 *
 * Unlike UsageStatsManager this receives the window transition from Android as it happens.
 * The service requests only window-state/window-list events and explicitly disables window
 * content retrieval: it records the package and Activity class, never screen text or views.
 */
class ApplicationForegroundAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.info(TAG, "Instant app detection AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = ApplicationForegroundAccessibilityEventPolicy.packageName(
            eventType = event.eventType,
            packageName = event.packageName,
        ) ?: return

        val previousPackage = ApplicationContextEvents.latestObservedPackage()
        ApplicationContextEvents.publishForeground(
            packageName = packageName,
            className = event.className?.toString(),
        )
        if (previousPackage != packageName) {
            AppLogger.debug(
                TAG,
                "Accessibility window transition: " + (previousPackage ?: "(none)") + " -> " + packageName,
            )
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "AppDetection"

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
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return null

        return packageName
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }
}
