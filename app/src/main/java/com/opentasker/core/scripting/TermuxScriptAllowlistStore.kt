package com.opentasker.core.scripting

import android.content.Context

internal data class ApprovedTermuxScript(
    val executable: String,
    val sha256: String,
)

internal enum class TermuxAllowlistSaveResult {
    SAVED,
    INVALID_PATH,
    INVALID_HASH,
    FULL,
}

/**
 * Small, bounded, local-only allowlist. Each approval pins a script's normalized path and SHA-256.
 * Editing a script invalidates the old approval until the user explicitly approves its new hash.
 */
internal class TermuxScriptAllowlistStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun entries(): List<ApprovedTermuxScript> = synchronized(preferences) {
        preferences.all.entries.mapNotNull { (key, rawHash) ->
            if (!key.startsWith(SCRIPT_PREFIX)) return@mapNotNull null
            val executable = key.removePrefix(SCRIPT_PREFIX)
            val safePath = TermuxScriptPolicy.normalizeExecutable(executable) ?: return@mapNotNull null
            val safeHash = (rawHash as? String)?.let(TermuxScriptPolicy::normalizeHash) ?: return@mapNotNull null
            ApprovedTermuxScript(executable = safePath, sha256 = safeHash)
        }.sortedBy(ApprovedTermuxScript::executable)
    }

    fun approve(executable: String, sha256: String): TermuxAllowlistSaveResult = synchronized(preferences) {
        val safePath = TermuxScriptPolicy.normalizeExecutable(executable)
            ?: return@synchronized TermuxAllowlistSaveResult.INVALID_PATH
        val safeHash = TermuxScriptPolicy.normalizeHash(sha256)
            ?: return@synchronized TermuxAllowlistSaveResult.INVALID_HASH
        val key = SCRIPT_PREFIX + safePath
        if (!preferences.contains(key) && entries().size >= MAX_ENTRIES) {
            return@synchronized TermuxAllowlistSaveResult.FULL
        }
        preferences.edit().putString(key, safeHash).apply()
        TermuxAllowlistSaveResult.SAVED
    }

    fun revoke(executable: String) = synchronized(preferences) {
        val safePath = TermuxScriptPolicy.normalizeExecutable(executable) ?: return@synchronized
        preferences.edit().remove(SCRIPT_PREFIX + safePath).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "termux_script_allowlist"
        const val SCRIPT_PREFIX = "approved:"
        const val MAX_ENTRIES = 64
    }
}
