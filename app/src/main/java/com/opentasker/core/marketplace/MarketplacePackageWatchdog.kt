package com.opentasker.core.marketplace

import android.content.Context
import android.util.Base64
import org.json.JSONArray

internal data class MarketplaceWatchdogSettings(
    val enabled: Boolean,
    val maxExecutions: Int,
    val windowSeconds: Int,
    val disabledPackageIds: List<String>,
)

internal sealed class MarketplaceWatchdogResult {
    object NotMonitored : MarketplaceWatchdogResult()
    object AlreadyDisabled : MarketplaceWatchdogResult()
    data class Counted(val executions: Int, val threshold: Int) : MarketplaceWatchdogResult()
    data class Disabled(val executions: Int, val threshold: Int, val windowSeconds: Int) : MarketplaceWatchdogResult()
}

/** Local, persistent package-wide rapid-execution guard. */
internal object MarketplacePackageWatchdog {
    private const val PREFS = "opentasker_marketplace_watchdog"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_THRESHOLD = "max_executions"
    private const val KEY_WINDOW = "window_seconds"
    private const val KEY_DISABLED = "disabled_package_ids"
    private val lock = Any()

    fun settings(context: Context): MarketplaceWatchdogSettings {
        val prefs = prefs(context)
        return MarketplaceWatchdogSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            maxExecutions = prefs.getInt(KEY_THRESHOLD, 10).coerceIn(2, 1000),
            windowSeconds = prefs.getInt(KEY_WINDOW, 60).coerceIn(5, 3600),
            disabledPackageIds = disabledIds(prefs).sorted(),
        )
    }

    fun updateSettings(context: Context, enabled: Boolean, maxExecutions: Int, windowSeconds: Int) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_THRESHOLD, maxExecutions.coerceIn(2, 1000))
            .putInt(KEY_WINDOW, windowSeconds.coerceIn(5, 3600))
            .apply()
    }

    fun packageProtectionEnabled(context: Context, packageId: String): Boolean =
        prefs(context).getBoolean(packageProtectionKey(packageId), true)

    fun setPackageProtectionEnabled(context: Context, packageId: String, enabled: Boolean) {
        prefs(context).edit().putBoolean(packageProtectionKey(packageId), enabled).apply()
    }

    fun isDisabled(context: Context, packageId: String): Boolean =
        synchronized(lock) { packageId in disabledIds(prefs(context)) }

    fun clearDisabled(context: Context, packageId: String) {
        synchronized(lock) {
            val preferences = prefs(context)
            val updated = disabledIds(preferences).toMutableSet().apply { remove(packageId) }
            preferences.edit()
                .putStringSet(KEY_DISABLED, updated)
                .remove(eventKey(packageId))
                .apply()
        }
    }

    fun disabledPackageIds(context: Context): List<String> =
        synchronized(lock) { disabledIds(prefs(context)).sorted() }

    fun recordExecution(context: Context, packageId: String, nowMs: Long = System.currentTimeMillis()): MarketplaceWatchdogResult =
        synchronized(lock) {
            val preferences = prefs(context)
            if (packageId in disabledIds(preferences)) return@synchronized MarketplaceWatchdogResult.AlreadyDisabled
            if (!preferences.getBoolean(KEY_ENABLED, true)) return@synchronized MarketplaceWatchdogResult.NotMonitored
            if (!preferences.getBoolean(packageProtectionKey(packageId), true)) {
                return@synchronized MarketplaceWatchdogResult.NotMonitored
            }

            val threshold = preferences.getInt(KEY_THRESHOLD, 10).coerceIn(2, 1000)
            val windowSeconds = preferences.getInt(KEY_WINDOW, 60).coerceIn(5, 3600)
            val cutoff = nowMs - windowSeconds * 1000L
            val key = eventKey(packageId)
            val previous = runCatching {
                val array = JSONArray(preferences.getString(key, "[]") ?: "[]")
                buildList {
                    for (index in 0 until array.length()) {
                        val timestamp = array.optLong(index, -1L)
                        if (timestamp >= cutoff && timestamp <= nowMs) add(timestamp)
                    }
                }
            }.getOrDefault(emptyList())
            val updated = previous + nowMs
            preferences.edit().putString(key, JSONArray(updated).toString()).apply()

            if (updated.size >= threshold) {
                val disabled = disabledIds(preferences).toMutableSet().apply { add(packageId) }
                preferences.edit().putStringSet(KEY_DISABLED, disabled).apply()
                MarketplaceWatchdogResult.Disabled(updated.size, threshold, windowSeconds)
            } else {
                MarketplaceWatchdogResult.Counted(updated.size, threshold)
            }
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun disabledIds(preferences: android.content.SharedPreferences): Set<String> =
        preferences.getStringSet(KEY_DISABLED, emptySet())?.toSet().orEmpty()

    private fun packageProtectionKey(packageId: String) = "package_protection_${encode(packageId)}"
    private fun eventKey(packageId: String) = "events_${encode(packageId)}"

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
