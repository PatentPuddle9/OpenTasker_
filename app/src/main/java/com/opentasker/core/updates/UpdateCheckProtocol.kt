package com.opentasker.core.updates

import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Public GitHub branch/release protocol used by the optional update checker. */
object UpdateCheckProtocol {
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/PatentPuddle9/OpenTasker_/releases/latest"
    const val BRANCHES_URL =
        "https://api.github.com/repos/PatentPuddle9/OpenTasker_/branches"
    const val RELEASES_URL_PREFIX =
        "https://github.com/PatentPuddle9/OpenTasker_/releases/tag/"
    const val RELEASE_DOWNLOAD_URL_PREFIX =
        "https://github.com/PatentPuddle9/OpenTasker_/releases/download/"
    const val DEFAULT_SOURCE_BRANCH = "Experimental"
    const val BETA_SOURCE_BRANCH = "BETA"
    const val LEGACY_BETA_SOURCE_BRANCH = "UI-overhaul-experiment"

    /** Maps the former beta branch name to the current BETA branch. */
    fun normalizeBuildSourceBranch(branch: String): String = when {
        branch.equals(BETA_SOURCE_BRANCH, ignoreCase = true) -> BETA_SOURCE_BRANCH
        branch.equals(LEGACY_BETA_SOURCE_BRANCH, ignoreCase = true) -> BETA_SOURCE_BRANCH
        else -> branch
    }

    fun isBetaBuildBranch(branch: String): Boolean =
        normalizeBuildSourceBranch(branch) == BETA_SOURCE_BRANCH
    const val RESPONSE_VARIABLE = "update_check_body"
    const val STATUS_VARIABLE = "update_check_http_status"
    const val BRANCHES_RESPONSE_VARIABLE = "update_check_branches"
    const val MAX_RESPONSE_BYTES = 65_536
    const val MAX_BRANCHES_RESPONSE_BYTES = 262_144
    const val REQUEST_TIMEOUT_SECONDS = 15
    const val BRANCH_PAGE_SIZE = 100
    const val MAX_BRANCH_PAGES = 100
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    const val APK_ASSET_PREFIX = "OpenTasker-"
    const val APK_ASSET_SUFFIX = "-debug.apk"
    const val MAX_APK_BYTES = 160L * 1024L * 1024L
    private const val CHANNEL_TAG_PREFIX = "opentasker-branch-"
    private const val BRANCH_ASSET_PREFIX = "OpenTasker-branch-"
    private const val BRANCH_ASSET_SUFFIX = "-debug.apk"
    private const val ACCEPT_HEADER = "Accept: application/vnd.github+json"
    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun isSelectableBranch(branch: String): Boolean =
        branch.isNotBlank() &&
            branch == branch.trim() &&
            branch.toByteArray(Charsets.UTF_8).size <= 1024 &&
            branch.none { it == '\n' || it == '\r' || it == '\u0000' } &&
            !branch.equals("master", ignoreCase = true)

    /** True when the selected source is a feature branch instead of the main Experimental branch. */
    fun requiresNonDefaultBranchWarning(branch: String): Boolean =
        branch != DEFAULT_SOURCE_BRANCH

    /** Stable, collision-resistant release-channel tag for the exact branch name. */
    fun branchChannelTag(branch: String): String {
        require(isSelectableBranch(branch)) { "The master branch cannot be used as an update source." }
        val digest = MessageDigest.getInstance("SHA-256").digest(branch.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return CHANNEL_TAG_PREFIX + hex
    }

    fun branchAssetName(branch: String): String =
        BRANCH_ASSET_PREFIX + branchChannelTag(branch).removePrefix(CHANNEL_TAG_PREFIX) + BRANCH_ASSET_SUFFIX

    fun branchReleaseTitlePrefix(branch: String): String {
        require(isSelectableBranch(branch)) { "The master branch cannot be used as an update source." }
        return "OpenTasker branch update [$branch] v"
    }

    fun branchReleaseApiUrl(branch: String): String =
        "https://api.github.com/repos/PatentPuddle9/OpenTasker_/releases/tags/" + branchChannelTag(branch)

    fun branchApkUrl(branch: String): String =
        RELEASE_DOWNLOAD_URL_PREFIX + branchChannelTag(branch) + "/" + branchAssetName(branch)

    fun requestArguments(branch: String = DEFAULT_SOURCE_BRANCH): Map<String, String> = mapOf(
        "method" to "GET",
        "url" to branchReleaseApiUrl(branch),
        "headers" to ACCEPT_HEADER,
        "response_var" to RESPONSE_VARIABLE,
        "status_var" to STATUS_VARIABLE,
        "max_response_bytes" to MAX_RESPONSE_BYTES.toString(),
        "timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "call_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "connect_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "read_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "write_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
        "redirects" to "none",
    )

    fun branchesRequestArguments(page: Int): Map<String, String> {
        require(page in 1..MAX_BRANCH_PAGES) { "Branch page is outside the allowed range." }
        return mapOf(
            "method" to "GET",
            "url" to "$BRANCHES_URL?per_page=$BRANCH_PAGE_SIZE&page=$page",
            "headers" to ACCEPT_HEADER,
            "response_var" to BRANCHES_RESPONSE_VARIABLE,
            "max_response_bytes" to MAX_BRANCHES_RESPONSE_BYTES.toString(),
            "timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "call_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "connect_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "read_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "write_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "redirects" to "none",
        )
    }

    fun sortBranches(branches: Collection<String>): List<String> =
        branches.asSequence()
            .filter(::isSelectableBranch)
            .distinct()
            .sortedWith(
                compareBy<String> { !it.equals(DEFAULT_SOURCE_BRANCH, ignoreCase = true) }
                    .thenBy { it.lowercase(Locale.ROOT) },
            )
            .toList()

    data class BranchPage(
        val branches: List<String>,
        /** Based on raw GitHub entries, before filtering master or duplicate names. */
        val hasMore: Boolean,
    )

    /** Parses a GitHub branches API page while retaining its raw pagination boundary. */
    fun parseBranchPage(payload: String): BranchPage? {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_BRANCHES_RESPONSE_BYTES) return null
        return runCatching {
            val entries = json.parseToJsonElement(payload) as JsonArray
            val branches = entries
                .mapNotNull { item ->
                    val name = item.jsonObject["name"]?.jsonPrimitive?.content?.trim()
                    name?.takeIf(::isSelectableBranch)
                }
                .let(::sortBranches)
            BranchPage(
                branches = branches,
                hasMore = entries.size >= BRANCH_PAGE_SIZE,
            )
        }.getOrNull()
    }

    /** Compatibility helper for callers that only need the filtered names. */
    fun parseBranchesResponse(payload: String): List<String>? =
        parseBranchPage(payload)?.branches

    /** Parses the selected branch's rolling prerelease channel and its single pinned APK asset. */
    fun parseBranchRelease(
        payload: String,
        currentVersion: String,
        branch: String,
    ): UpdateCheckResult {
        if (!isSelectableBranch(branch)) {
            return UpdateCheckResult.Invalid("selected update branch is not allowed")
        }
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
            return UpdateCheckResult.Invalid("branch release response exceeds the size limit")
        }
        return runCatching {
            parseBranchReleaseObject(payload, currentVersion, branch)
        }.getOrElse {
            UpdateCheckResult.Invalid("branch release response is malformed")
        }
    }

    private fun parseBranchReleaseObject(
        payload: String,
        currentVersion: String,
        branch: String,
    ): UpdateCheckResult {
        val release = json.parseToJsonElement(payload) as? JsonObject
            ?: return UpdateCheckResult.Invalid("branch release response is not an object")
        val tag = release["tag_name"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("branch update channel tag is missing")
        val expectedTag = branchChannelTag(branch)
        if (tag != expectedTag) {
            return UpdateCheckResult.Invalid("release does not belong to the selected branch")
        }
        val releaseUrl = release["html_url"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("branch release URL is missing")
        if (releaseUrl != RELEASES_URL_PREFIX + expectedTag) {
            return UpdateCheckResult.Invalid("branch release URL is outside the OpenTasker release page")
        }
        if (release.booleanValue("draft") || !release.booleanValue("prerelease")) {
            return UpdateCheckResult.Invalid("branch update channel must be a published prerelease")
        }

        val title = release["name"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("branch release version is missing")
        val versionPrefix = branchReleaseTitlePrefix(branch)
        if (!title.startsWith(versionPrefix)) {
            return UpdateCheckResult.Invalid("branch release title does not match the selected branch")
        }
        val versionText = title.removePrefix(versionPrefix)
        val latest = SemanticVersion.parse(versionText)
            ?: return UpdateCheckResult.Invalid("branch release version is not stable semantic versioning")
        val current = SemanticVersion.parse(currentVersion)
            ?: return UpdateCheckResult.Invalid("installed version is not a stable semantic version")
        if (latest <= current) return UpdateCheckResult.NoUpdate

        val expectedAssetName = branchAssetName(branch)
        val assets = release["assets"]?.jsonArray
            ?: return UpdateCheckResult.Invalid("branch release assets are missing")
        val matches = assets.filter { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.content == expectedAssetName
        }
        if (matches.size != 1) {
            return UpdateCheckResult.Invalid("branch release does not contain exactly one compatible APK")
        }
        val asset = matches.single().jsonObject
        if (asset["content_type"]?.jsonPrimitive?.content != APK_MIME_TYPE) {
            return UpdateCheckResult.Invalid("branch APK has an unexpected content type")
        }
        val apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("branch APK download URL is missing")
        val expectedApkUrl = branchApkUrl(branch)
        if (apkUrl != expectedApkUrl) {
            return UpdateCheckResult.Invalid("branch APK URL is outside the selected branch asset")
        }
        val size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return UpdateCheckResult.Invalid("branch APK size is missing")
        if (size <= 0L || size > MAX_APK_BYTES) {
            return UpdateCheckResult.Invalid("branch APK size is outside the allowed limit")
        }
        val digest = asset["digest"]?.jsonPrimitive?.content?.trim()
            ?: return UpdateCheckResult.Invalid("branch APK SHA-256 digest is missing")
        val sha256 = digest.removePrefix("sha256:")
        if (!digest.startsWith("sha256:") || !sha256Pattern.matches(sha256)) {
            return UpdateCheckResult.Invalid("branch APK SHA-256 digest is invalid")
        }

        return UpdateCheckResult.Available(
            version = latest.display,
            url = releaseUrl,
            apkUrl = apkUrl,
            apkSizeBytes = size,
            apkSha256 = sha256.lowercase(Locale.ROOT),
        )
    }

    /** Legacy stable-release parser kept for compatibility with the existing release contract tests. */
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
        if (release.booleanValue("draft") || release.booleanValue("prerelease")) return UpdateCheckResult.NoUpdate
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
            apkSha256 = sha256.lowercase(Locale.ROOT),
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
