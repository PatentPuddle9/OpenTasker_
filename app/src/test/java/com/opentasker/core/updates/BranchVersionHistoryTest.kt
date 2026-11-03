package com.opentasker.core.updates

import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.Profile
import com.opentasker.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchVersionHistoryTest {

    @Test
    fun experimentalVersionParsesVerifiedAssetAndVersionCode() {
        val version = UpdateCheckProtocol.parseBranchVersions(
            releaseJson(
                tag = "v0.2.125",
                title = "OpenTasker v0.2.125",
                assetName = "OpenTasker-v0.2.125-debug.apk",
                assetUrl = "https://github.com/PatentPuddle9/OpenTasker_/releases/download/v0.2.125/OpenTasker-v0.2.125-debug.apk",
                body = "Version code: **127**\nIn-app updates: **available**\nAction catalog: `wifi.toggle,notify.show,flow.if`",
            ),
            "Experimental",
        )!!.single()

        assertEquals("0.2.125", version.version)
        assertEquals(127L, version.versionCode ?: -1L)
        assertEquals("a".repeat(64), version.apkSha256.orEmpty())
        assertTrue(version.canInstallInPlace)
        assertTrue(version.supportsInAppUpdates)
        assertTrue(version.updaterSupportKnown)
        assertEquals(setOf("wifi.toggle", "notify.show", "flow.if"), version.supportedActionIds)
        assertTrue(version.isLatest)
    }

    @Test
    fun betaAndCustomBranchImmutableTagsBelongOnlyToTheirSelectedBranch() {
        val beta = UpdateCheckProtocol.parseBranchVersions(
            releaseJson(
                tag = "beta-v0.2.122",
                title = "OpenTasker Beta v0.2.122",
                assetName = "OpenTasker-Beta-v0.2.122-debug.apk",
                assetUrl = "https://github.com/PatentPuddle9/OpenTasker_/releases/download/beta-v0.2.122/OpenTasker-Beta-v0.2.122-debug.apk",
                body = "Version code: **124**",
            ),
            "BETA",
        )!!.single()
        assertEquals("0.2.122", beta.version)

        val branch = "feature/new-ui"
        val branchHash = UpdateCheckProtocol.branchChannelTag(branch).removePrefix("opentasker-branch-")
        val tag = "opentasker-branch-build-$branchHash-v0.2.130"
        val assetName = "OpenTasker-branch-$branchHash-v0.2.130-debug.apk"
        val custom = UpdateCheckProtocol.parseBranchVersions(
            releaseJson(
                tag = tag,
                title = "OpenTasker $branch v0.2.130",
                assetName = assetName,
                assetUrl = "https://github.com/PatentPuddle9/OpenTasker_/releases/download/$tag/$assetName",
                body = "Android versionCode: **132**\nIn-app updates: **not included**",
            ),
            branch,
        )!!.single()
        assertEquals("0.2.130", custom.version)
        assertFalse(custom.supportsInAppUpdates)
        assertTrue(custom.updaterSupportKnown)
        assertTrue(UpdateCheckProtocol.parseBranchVersions(
            releaseJson(
                tag = tag,
                title = "OpenTasker $branch v0.2.130",
                assetName = assetName,
                assetUrl = "https://github.com/PatentPuddle9/OpenTasker_/releases/download/$tag/$assetName",
                body = "Android versionCode: **132**",
            ),
            "Experimental",
        )!!.isEmpty())
    }

    @Test
    fun canonicalReleaseWinsOverRollingChannelDuplicateAndLatestIsMarked() {
        val branch = "Experimental"
        val stableTag = "v0.2.125"
        val stableAsset = "OpenTasker-v0.2.125-debug.apk"
        val rollingTag = UpdateCheckProtocol.branchChannelTag(branch)
        val rollingAsset = UpdateCheckProtocol.branchAssetName(branch)
        val payload = """[
            ${releaseJsonObject(
                stableTag,
                "OpenTasker v0.2.125",
                stableAsset,
                "https://github.com/PatentPuddle9/OpenTasker_/releases/download/$stableTag/$stableAsset",
                "Version code: **127**",
            )},
            ${releaseJsonObject(
                rollingTag,
                "OpenTasker branch update [Experimental] v0.2.125",
                rollingAsset,
                UpdateCheckProtocol.branchApkUrl(branch),
                "Android versionCode: **127**",
            )},
            ${releaseJsonObject(
                "v0.2.118",
                "OpenTasker v0.2.118",
                "OpenTasker-v0.2.118-debug.apk",
                "https://github.com/PatentPuddle9/OpenTasker_/releases/download/v0.2.118/OpenTasker-v0.2.118-debug.apk",
                "Version code: **120**",
            )}
        ]"""
        val versions = UpdateCheckProtocol.parseBranchVersions(payload, branch)!!
        assertEquals(listOf("0.2.125", "0.2.118"), versions.map { it.version })
        assertEquals(stableTag, versions.first().tag)
        assertTrue(versions.first().isLatest)
        assertFalse(versions.last().isLatest)
        assertTrue(versions.last().supportsInAppUpdates)
    }

    @Test
    fun versionsWithoutUpdaterSupportAreMarkedRedIncludingPreUpdaterReleases() {
        val old = UpdateCheckProtocol.parseBranchVersions(
            releaseJson(
                tag = "v0.2.94",
                title = "OpenTasker v0.2.94",
                assetName = "OpenTasker-v0.2.94-debug.apk",
                assetUrl = "https://github.com/PatentPuddle9/OpenTasker_/releases/download/v0.2.94/OpenTasker-v0.2.94-debug.apk",
                body = "",
            ),
            "Experimental",
        )!!.single()
        assertFalse(old.supportsInAppUpdates)

        val explicitlyUnsupported = old.copy(
            version = "0.2.125",
            supportsInAppUpdates = false,
        )
        assertFalse(explicitlyUnsupported.supportsInAppUpdates)
    }

    @Test
    fun historicalCustomBranchWithoutUpdaterMarkerIsExplicitlyUnverified() {
        val branch = "feature/old"
        val version = UpdateCheckProtocol.parseBranchVersions(
            releaseJson(
                tag = UpdateCheckProtocol.branchChannelTag(branch),
                title = "OpenTasker branch update [feature/old] v0.2.124",
                assetName = UpdateCheckProtocol.branchAssetName(branch),
                assetUrl = UpdateCheckProtocol.branchApkUrl(branch),
                body = "Android versionCode: **126**",
            ),
            branch,
        )!!.single()

        assertFalse(version.updaterSupportKnown)
        assertFalse(version.supportsInAppUpdates)
    }

    @Test
    fun historicalActionCatalogIncludesRuntimeAndEngineHandledActions() {
        val ids = UpdateCheckProtocol.parseActionCatalog(
            """
                private fun catalog() {
                    define("wifi.toggle", ActionCategory.SETTINGS)
                    define("notify.show", ActionCategory.NOTIFICATION)
                    defineEngine("flow.if", ActionCategory.FLOW)
                }
            """.trimIndent(),
        )
        assertEquals(setOf("wifi.toggle", "notify.show", "flow.if"), ids)
    }

    @Test
    fun compatibilityUsesActionsReachableFromEnabledProfilesAndNestedTasks() {
        val profiles = listOf(
            Profile(id = 1, name = "Enabled", enabled = true, enterTaskId = 10),
            Profile(id = 2, name = "Disabled", enabled = false, enterTaskId = 30),
        )
        val tasks = listOf(
            Task(
                id = 10,
                name = "Entry",
                actions = listOf(
                    ActionSpec(type = "wifi.toggle"),
                    ActionSpec(type = "task.run", args = mapOf("task" to "20")),
                ),
            ),
            Task(id = 20, name = "Nested", actions = listOf(ActionSpec(type = "notify.show"))),
            Task(id = 30, name = "Disabled task", actions = listOf(ActionSpec(type = "bluetooth.toggle"))),
            Task(id = 40, name = "Global fallback", actions = listOf(ActionSpec(type = "log"))),
        )

        assertEquals(
            setOf("wifi.toggle", "task.run", "notify.show", "log"),
            collectEnabledProfileActionIds(profiles, tasks, globalFallbackTaskId = 40),
        )
    }

    private fun releaseJson(
        tag: String,
        title: String,
        assetName: String,
        assetUrl: String,
        body: String,
    ): String = "[${releaseJsonObject(tag, title, assetName, assetUrl, body)}]"

    private fun releaseJsonObject(
        tag: String,
        title: String,
        assetName: String,
        assetUrl: String,
        body: String,
    ): String {
        val safeBody = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """
            {
              "tag_name": "$tag",
              "name": "$title",
              "html_url": "https://github.com/PatentPuddle9/OpenTasker_/releases/tag/$tag",
              "target_commitish": "0123456789abcdef0123456789abcdef01234567",
              "published_at": "2026-11-03T12:00:00Z",
              "draft": false,
              "prerelease": true,
              "body": "$safeBody",
              "assets": [{
                "name": "$assetName",
                "content_type": "application/vnd.android.package-archive",
                "browser_download_url": "$assetUrl",
                "size": 4096,
                "digest": "sha256:${"a".repeat(64)}"
              }]
            }
        """.trimIndent()
    }
}
