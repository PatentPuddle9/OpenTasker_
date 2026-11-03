package com.opentasker.core.updates

import android.content.Context
import androidx.core.content.edit

data class UpdateCheckState(
    val enabled: Boolean = false,
    val newerVersion: String? = null,
    val releaseUrl: String? = null,
    val apkUrl: String? = null,
    val apkSizeBytes: Long? = null,
    val apkSha256: String? = null,
    val lastCheckedAtMs: Long? = null,
) {
    val canInstallUpdate: Boolean
        get() = newerVersion != null &&
            apkUrl != null &&
            (apkSizeBytes ?: 0L) > 0L &&
            apkSha256 != null
}

/** Device-local preference and result storage for the opt-in release check. */
class UpdateCheckSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): UpdateCheckState {
        val url = preferences.getString(KEY_RELEASE_URL, null)?.takeIf(String::isNotBlank)
        val version = preferences.getString(KEY_NEWER_VERSION, null)?.takeIf(String::isNotBlank)
        val apkUrl = preferences.getString(KEY_APK_URL, null)?.takeIf(String::isNotBlank)
        val apkSize = preferences.getLong(KEY_APK_SIZE, 0L).takeIf { it > 0L }
        val apkSha256 = preferences.getString(KEY_APK_SHA256, null)?.takeIf(String::isNotBlank)
        val valid = version != null && url != null && apkUrl != null && apkSize != null && apkSha256 != null
        return UpdateCheckState(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            newerVersion = version.takeIf { valid },
            releaseUrl = url.takeIf { valid },
            apkUrl = apkUrl.takeIf { valid },
            apkSizeBytes = apkSize.takeIf { valid },
            apkSha256 = apkSha256.takeIf { valid },
            lastCheckedAtMs = preferences.getLong(KEY_LAST_CHECKED_AT, 0L)
                .takeIf { it > 0L && !(version != null && !valid) },
        )
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_ENABLED, enabled)
            if (!enabled) {
                remove(KEY_NEWER_VERSION)
                remove(KEY_RELEASE_URL)
                remove(KEY_APK_URL)
                remove(KEY_APK_SIZE)
                remove(KEY_APK_SHA256)
                remove(KEY_LAST_CHECKED_AT)
            }
        }
    }

    fun record(result: UpdateCheckResult, checkedAtMs: Long) {
        preferences.edit {
            putLong(KEY_LAST_CHECKED_AT, checkedAtMs)
            when (result) {
                UpdateCheckResult.NoUpdate -> {
                    remove(KEY_NEWER_VERSION)
                    remove(KEY_RELEASE_URL)
                    remove(KEY_APK_URL)
                    remove(KEY_APK_SIZE)
                    remove(KEY_APK_SHA256)
                }
                is UpdateCheckResult.Available -> {
                    putString(KEY_NEWER_VERSION, result.version)
                    putString(KEY_RELEASE_URL, result.url)
                    putString(KEY_APK_URL, result.apkUrl)
                    putLong(KEY_APK_SIZE, result.apkSizeBytes)
                    putString(KEY_APK_SHA256, result.apkSha256)
                }
                is UpdateCheckResult.Invalid -> Unit
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "update_checks"
        const val KEY_ENABLED = "enabled"
        const val KEY_NEWER_VERSION = "newer_version"
        const val KEY_RELEASE_URL = "release_url"
        const val KEY_APK_URL = "apk_url"
        const val KEY_APK_SIZE = "apk_size"
        const val KEY_APK_SHA256 = "apk_sha256"
        const val KEY_LAST_CHECKED_AT = "last_checked_at"
    }
}
