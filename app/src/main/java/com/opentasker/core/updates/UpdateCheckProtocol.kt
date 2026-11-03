package com.opentasker.core.updates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pure protocol and release-version rules for the optional public update check. */
object UpdateCheckProtocol {
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/PatentPuddle9/OpenTasker_/releases/latest"
    const val RELEASES_URL_PREFIX =
        "https://github.com/PatentPuddle9/OpenTasker_/releases/tag/"
    const val RELEASE_DOWNLOAD_URL_PREFIX =
        "https://github.com/PatentPuddle9/OpenTasker_/releases/download/"
    const val RESPONSE_VARIABLE = "update_check_body"
    const val MAX_RESPONSE_BYTES = 65_536
    const val REQUEST_TIMEOUT_SECONDS = 15
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    const val APK_ASSET_PREFIX = "OpenTasker-"
    const val APK_ASSET_SUFFIX = "-debug.apk"
    const val MAX_APK_BYTES = 160L * 1024L * 1024L

    private const val ACCEPT_HEADER = "Accept: application/vnd.github+json"
    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    /** Arguments passed to [com.opentasker.core.actions.HttpRequestAction]. */
    fun requestArguments(): Map<String, String> = mapOf(
        "method" to "GET",
        "url" to LATEST_RELEASE_URL,
        "headers" to ACCEPT_HEADER,
        "response_var" to RESPONSE_VARIABLE,
        "max_response_bytes" to MAX_RESPONSE_BYTES.toString(),
        "timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "call_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "connect_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "read_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "write_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "redirects" to "none",
    )

    fun parseLatestRelease(payload: String, currentVersion: String): UpdateCheckResult {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
            return UpdateCheckResult.Invalid("release response exceeds the size limit")
        }
        return runCatching {
            parseLatestReleaseObject(payload, currentVersion)
        }.getOrElse {
            UpdateCheckResult.Invalid("release response is malformed")
        }
    }

    private fun parseLatestReleaseObject(payload: String, currentVersion: String): UpdateCheckResult {
        val release = json.parseToJsonElement(payload) as? JsonObject
            ?: return UpdateCheckResult.Invalid("release response is not an object")
        val tag = release["tag_name"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("release tag is missing")
        val latest = SemanticVersion.parse(tag)
            ?: return UpdateCheckResult.Invalid("release tag is not a stable semantic version")
        val current = SemanticVersion.parse(currentVersion)
            ?: return UpdateCheckResult.Invalid("installed version is not a stable semantic version")

        if (release.booleanValue("draft") || release.booleanValue("prerelease")) {
            return UpdateCheckResult.NoUpdate
        }
        if (latest <= current) return UpdateCheckResult.NoUpdate

        val releaseUrl = release["html_url"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("release URL is missing")
        if (releaseUrl != RELEASES_URL_PREFIX + tag) {
            return UpdateCheckResult.Invalid("release URL is outside the OpenTasker release page")
        }

        val expectedAssetName = "$APK_ASSET_PREFIX$tag$APK_ASSET_SUFFIX"
        val assets = release["assets"]?.jsonArray
            ?: return UpdateCheckResult.Invalid("release assets are missing")
        val matches = assets.filter { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.content == expectedAssetName
        }
        if (matches.size != 1) {
            return UpdateCheckResult.Invalid("release does not contain exactly one compatible APK")
        }

        val asset = matches.single().jsonObject
        if (asset["content_type"]?.jsonPrimitive?.content != APK_MIME_TYPE) {
            return UpdateCheckResult.Invalid("release APK has an unexpected content type")
        }
        val apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("release APK download URL is missing")
        val expectedApkUrl = "$RELEASE_DOWNLOAD_URL_PREFIX$tag/$expectedAssetName"
        if (apkUrl != expectedApkUrl) {
            return UpdateCheckResult.Invalid("release APK URL is outside the OpenTasker release asset")
        }

        val size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return UpdateCheckResult.Invalid("release APK size is missing")
        if (size <= 0L || size > MAX_APK_BYTES) {
            return UpdateCheckResult.Invalid("release APK size is outside the allowed limit")
        }

        val digest = asset["digest"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("release APK SHA-256 digest is missing")
        val sha256 = digest.removePrefix("sha256:")
        if (!digest.startsWith("sha256:") || !sha256Pattern.matches(sha256)) {
            return UpdateCheckResult.Invalid("release APK SHA-256 digest is invalid")
        }

        return UpdateCheckResult.Available(
            version = latest.display,
            url = releaseUrl,
            apkUrl = apkUrl,
            apkSizeBytes = size,
            apkSha256 = sha256.lowercase(),
        )
    }

    private fun JsonObject.booleanValue(name: String): Boolean =
        this[name]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true

    private data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val display: String,
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int = compareValuesBy(
            this,
            other,
            SemanticVersion::major,
            SemanticVersion::minor,
            SemanticVersion::patch,
        )

        companion object {
            private val PATTERN = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")

            fun parse(raw: String): SemanticVersion? {
                val match = PATTERN.matchEntire(raw) ?: return null
                val major = match.groupValues[1].toIntOrNull() ?: return null
                val minor = match.groupValues[2].toIntOrNull() ?: return null
                val patch = match.groupValues[3].toIntOrNull() ?: return null
                return SemanticVersion(major, minor, patch, "$major.$minor.$patch")
            }
        }
    }
}

sealed interface UpdateCheckResult {
    data object NoUpdate : UpdateCheckResult

    data class Available(
        val version: String,
        val url: String,
        val apkUrl: String,
        val apkSizeBytes: Long,
        val apkSha256: String,
    ) : UpdateCheckResult

    data class Invalid(val reason: String) : UpdateCheckResult
}
