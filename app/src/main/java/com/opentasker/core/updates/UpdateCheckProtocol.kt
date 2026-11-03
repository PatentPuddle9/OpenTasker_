package com.opentasker.core.updates

import com.opentasker.app.BuildConfig
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
    const val VERSION_PAGE_SIZE = 30
    const val MAX_VERSION_PAGES = 20
    const val MAX_VERSION_RESPONSE_BYTES = 262_144
    const val MAX_ACTION_CATALOG_BYTES = 65_536
    const val FIRST_IN_APP_UPDATE_VERSION = "0.2.118"
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

    fun requestArguments(branch: String = BuildConfig.UPDATE_SOURCE_BRANCH): Map<String, String> = mapOf(
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


    /** Builds a bounded request for the selected branch's immutable GitHub release history. */
    fun versionsRequestArguments(page: Int, branch: String): Map<String, String> {
        require(page in 1..MAX_VERSION_PAGES) { "Version page is outside the allowed range." }
        require(isSelectableBranch(branch)) { "The master branch cannot be used as an update source." }
        return mapOf(
            "method" to "GET",
            "url" to "https://api.github.com/repos/PatentPuddle9/OpenTasker_/releases?per_page=$VERSION_PAGE_SIZE&page=$page",
            "headers" to ACCEPT_HEADER,
            "response_var" to RESPONSE_VARIABLE,
            "status_var" to STATUS_VARIABLE,
            "max_response_bytes" to MAX_VERSION_RESPONSE_BYTES.toString(),
            "timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "call_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "connect_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "read_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "write_timeout_sec" to REQUEST_TIMEOUT_SECONDS.toString(),
            "redirects" to "none",
        )
    }

    /** Raw source for the action catalogue at the exact release commit. */
    fun actionCatalogUrl(commit: String): String {
        require(commit.matches(Regex("^[0-9a-fA-F]{40}$"))) { "Release commit is invalid." }
        return "https://raw.githubusercontent.com/PatentPuddle9/OpenTasker_/$commit/app/src/main/java/com/opentasker/core/actions/ActionCatalog.kt"
    }

    /** Extracts runtime and engine-handled action IDs from a historical ActionCatalog source file. */
    fun parseActionCatalog(payload: String): Set<String>? {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_ACTION_CATALOG_BYTES) return null
        val ids = Regex("""define(?:Engine)?\("([^"]+)""")
            .findAll(payload)
            .map { it.groupValues[1] }
            .toSet()
        return ids.takeIf { it.isNotEmpty() }
    }

    /**
     * Parses all published APK releases that belong to [branch]. Both immutable version tags and
     * the older rolling branch channel are accepted. When a rolling channel duplicates an
     * immutable version release, the immutable release wins so the selected APK never changes.
     */
    fun parseBranchVersions(payload: String, branch: String): List<BranchVersion>? {
        if (!isSelectableBranch(branch)) return null
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_VERSION_RESPONSE_BYTES) return null
        return runCatching {
            val releases = json.parseToJsonElement(payload) as JsonArray
            val parsed = releases.mapNotNull { element ->
                val release = element as? JsonObject ?: return@mapNotNull null
                if (release.booleanValue("draft")) return@mapNotNull null
                val tag = release["tag_name"]?.jsonPrimitive?.content?.trim()
                    ?: return@mapNotNull null
                val title = release["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                val version = releaseVersionForBranch(tag, title, branch) ?: return@mapNotNull null
                val releaseUrl = release["html_url"]?.jsonPrimitive?.content?.trim()
                    ?: return@mapNotNull null
                if (releaseUrl != RELEASES_URL_PREFIX + tag) return@mapNotNull null

                val body = release["body"]?.jsonPrimitive?.content.orEmpty()
                val assets = release["assets"]?.jsonArray.orEmpty()
                val apkAssets = assets.mapNotNull { asset ->
                    val objectValue = asset as? JsonObject ?: return@mapNotNull null
                    if (objectValue["content_type"]?.jsonPrimitive?.content != APK_MIME_TYPE) return@mapNotNull null
                    val name = objectValue["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val url = objectValue["browser_download_url"]?.jsonPrimitive?.content?.trim()
                        ?: return@mapNotNull null
                    val expectedUrl = RELEASE_DOWNLOAD_URL_PREFIX + "$tag/$name"
                    if (url != expectedUrl || !name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                    val size = objectValue["size"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?.takeIf { it in 1..MAX_APK_BYTES } ?: return@mapNotNull null
                    val digestText = objectValue["digest"]?.jsonPrimitive?.content?.trim()
                        ?: return@mapNotNull null
                    val digest = digestText.removePrefix("sha256:")
                    if (!digestText.startsWith("sha256:") || !sha256Pattern.matches(digest)) return@mapNotNull null
                    Triple(url, size, digest.lowercase(Locale.ROOT))
                }
                val apk = apkAssets.singleOrNull()
                val targetCommit = release["target_commitish"]?.jsonPrimitive?.content?.trim()
                // Release target_commitish can be a branch head unrelated to the APK if an old
                // workflow updated the branch between packaging and publishing. Prefer the exact
                // commit recorded by the successful build in the release notes.
                val bodyCommit = Regex("""(?:Source commit:|from commit )`([0-9a-fA-F]{40})`""")
                    .find(body)?.groupValues?.get(1)
                val commit = bodyCommit
                    ?: targetCommit?.takeIf { it.matches(Regex("^[0-9a-fA-F]{40}$")) }
                val versionCode = Regex("""(?:Version code|Android versionCode):?\s*\*\*(\d+)\*\*""")
                    .find(body)?.groupValues?.get(1)?.toLongOrNull()
                val supportedActionIds = Regex("""Action catalog: `([^`]+)`""")
                    .find(body)?.groupValues?.get(1)
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    ?.takeIf(Set<String>::isNotEmpty)
                val publishedAt = release["published_at"]?.jsonPrimitive?.content
                    ?: release["created_at"]?.jsonPrimitive?.content
                val inAppUpdates = when {
                    body.contains("In-app updates: **not included**", ignoreCase = true) -> false
                    body.contains("In-app updates: **available**", ignoreCase = true) -> true
                    else -> isAtLeastVersion(version, FIRST_IN_APP_UPDATE_VERSION)
                }

                BranchVersion(
                    tag = tag,
                    version = version,
                    versionCode = versionCode,
                    releaseUrl = releaseUrl,
                    apkUrl = apk?.first,
                    apkSizeBytes = apk?.second,
                    apkSha256 = apk?.third,
                    sourceCommit = commit,
                    publishedAt = publishedAt,
                    supportsInAppUpdates = inAppUpdates,
                    supportedActionIds = supportedActionIds,
                )
            }
            parsed
                .groupBy(BranchVersion::version)
                .map { (_, duplicates) ->
                    duplicates.minByOrNull { versionReleasePreference(it.tag, branch) } ?: duplicates.first()
                }
                .sortedWith(
                    compareByDescending<BranchVersion> { it.versionCode ?: -1L }
                        .thenByDescending { it.publishedAt.orEmpty() },
                )
                .mapIndexed { index, version -> version.copy(isLatest = index == 0) }
        }.getOrNull()
    }

    private fun versionReleasePreference(tag: String, branch: String): Int = when {
        tag == branchChannelTag(branch) -> 2
        branch.equals(DEFAULT_SOURCE_BRANCH, ignoreCase = true) &&
            tag.matches(Regex("^v\\d+\\.\\d+\\.\\d+$")) -> 0
        isBetaBuildBranch(branch) &&
            tag.matches(Regex("^beta-v\\d+\\.\\d+\\.\\d+$")) -> 0
        tag.startsWith("opentasker-branch-build-") -> 1
        else -> 3
    }

    private fun releaseVersionForBranch(tag: String, title: String, branch: String): String? {
        val branchHash = branchChannelTag(branch).removePrefix(CHANNEL_TAG_PREFIX)
        val version = when {
            tag == branchChannelTag(branch) ->
                Regex("""\bv(\d+\.\d+\.\d+)$""").find(title)?.groupValues?.get(1)
            branch.equals(DEFAULT_SOURCE_BRANCH, ignoreCase = true) && tag.matches(Regex("^v\\d+\\.\\d+\\.\\d+$")) ->
                tag.removePrefix("v")
            isBetaBuildBranch(branch) && tag.matches(Regex("^beta-v\\d+\\.\\d+\\.\\d+$")) ->
                tag.removePrefix("beta-v")
            tag.startsWith("opentasker-branch-build-$branchHash-v") ->
                tag.removePrefix("opentasker-branch-build-$branchHash-v")
            else -> null
        } ?: return null
        return version.takeIf { SemanticVersion.parse(it) != null }
    }

    private fun isAtLeastVersion(version: String, minimum: String): Boolean {
        val current = SemanticVersion.parse(version) ?: return false
        val required = SemanticVersion.parse(minimum) ?: return false
        return current >= required
    }

    fun releasePageHasMore(payload: String): Boolean? {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_VERSION_RESPONSE_BYTES) return null
        return runCatching {
            (json.parseToJsonElement(payload) as JsonArray).size >= VERSION_PAGE_SIZE
        }.getOrNull()
    }

    fun orderBranchVersions(versions: Collection<BranchVersion>, branch: String): List<BranchVersion> =
        versions
            .groupBy(BranchVersion::version)
            .map { (_, duplicates) ->
                duplicates.minByOrNull { versionReleasePreference(it.tag, branch) } ?: duplicates.first()
            }
            .sortedWith(
                compareByDescending<BranchVersion> { it.versionCode ?: -1L }
                    .thenByDescending { it.publishedAt.orEmpty() },
            )
            .mapIndexed { index, version -> version.copy(isLatest = index == 0) }

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
