package com.opentasker.core.updates

import android.content.Context
import androidx.core.content.edit

data class UpdateCheckState(
    val enabled: Boolean = false,
    val selectedBranch: String = UpdateCheckProtocol.DEFAULT_SOURCE_BRANCH,
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

/** Device-local branch selection and update result storage. */
class UpdateCheckSettings(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): UpdateCheckState {
        val selectedBranch = preferences.getString(KEY_SELECTED_BRANCH, null)
            ?.takeIf(UpdateCheckProtocol::isSelectableBranch)
            ?: UpdateCheckProtocol.DEFAULT_SOURCE_BRANCH
        val checkedBranch = preferences.getString(KEY_CHECKED_BRANCH, null)
        val branchMatches = checkedBranch == selectedBranch
        val url = preferences.getString(KEY_RELEASE_URL, null)?.takeIf(String::isNotBlank)
        val version = preferences.getString(KEY_NEWER_VERSION, null)?.takeIf(String::isNotBlank)
        val apkUrl = preferences.getString(KEY_APK_URL, null)?.takeIf(String::isNotBlank)
        val apkSize = preferences.getLong(KEY_APK_SIZE, 0L).takeIf { it > 0L }
        val apkSha256 = preferences.getString(KEY_APK_SHA256, null)?.takeIf(String::isNotBlank)
        val metadataValid = version != null && url != null && apkUrl != null && apkSize != null && apkSha256 != null
        val valid = metadataValid && branchMatches
        return UpdateCheckState(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            selectedBranch = selectedBranch,
            newerVersion = version.takeIf { valid },
            releaseUrl = url.takeIf { valid },
            apkUrl = apkUrl.takeIf { valid },
            apkSizeBytes = apkSize.takeIf { valid },
            apkSha256 = apkSha256.takeIf { valid },
            lastCheckedAtMs = preferences.getLong(KEY_LAST_CHECKED_AT, 0L)
                .takeIf { it > 0L && branchMatches && !(version != null && !metadataValid) },
        )
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_ENABLED, enabled)
            if (!enabled) clearResult()
        }
    }

    fun setSelectedBranch(branch: String) {
        require(UpdateCheckProtocol.isSelectableBranch(branch)) {
            "The master branch cannot be used as an update source."
        }
        preferences.edit {
            val previous = getString(KEY_SELECTED_BRANCH, UpdateCheckProtocol.DEFAULT_SOURCE_BRANCH)
            putString(KEY_SELECTED_BRANCH, branch)
            if (previous != branch) clearResult()
        }
    }

    fun record(result: UpdateCheckResult, checkedAtMs: Long, branch: String) {
        require(UpdateCheckProtocol.isSelectableBranch(branch)) {
            "The master branch cannot be used as an update source."
        }
        preferences.edit {
            putString(KEY_CHECKED_BRANCH, branch)
            putLong(KEY_LAST_CHECKED_AT, checkedAtMs)
            when (result) {
                UpdateCheckResult.NoUpdate -> clearUpdateMetadata()
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

    private fun androidx.core.content.SharedPreferencesCompat.clearResult() {
        // Not used; kept out of call sites so the preference edits stay explicit.
    }

    private fun android.content.SharedPreferences.Editor.clearResult() {
        clearUpdateMetadata()
        remove(KEY_LAST_CHECKED_AT)
        remove(KEY_CHECKED_BRANCH)
    }

    private fun android.content.SharedPreferences.Editor.clearUpdateMetadata() {
        remove(KEY_NEWER_VERSION)
        remove(KEY_RELEASE_URL)
        remove(KEY_APK_URL)
        remove(KEY_APK_SIZE)
        remove(KEY_APK_SHA256)
    }

    private companion object {
        const val PREFERENCES_NAME = "update_checks"
        const val KEY_ENABLED = "enabled"
        const val KEY_SELECTED_BRANCH = "selected_branch"
        const val KEY_CHECKED_BRANCH = "checked_branch"
        const val KEY_NEWER_VERSION = "newer_version"
        const val KEY_RELEASE_URL = "release_url"
        const val KEY_APK_URL = "apk_url"
        const val KEY_APK_SIZE = "apk_size"
        const val KEY_APK_SHA256 = "apk_sha256"
        const val KEY_LAST_CHECKED_AT = "last_checked_at"
    }
}
