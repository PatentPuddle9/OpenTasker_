package com.opentasker.core.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckProtocolTest {
    @Test
    fun stableNewerReleaseIsReportedWithVerifiedApkAsset() {
        val result = UpdateCheckProtocol.parseLatestRelease(
            release(tag = "v0.2.85"),
            currentVersion = "0.2.84",
        )

        assertEquals(
            UpdateCheckResult.Available(
                version = "0.2.85",
                url = "https://github.com/PatentPuddle9/OpenTasker_/releases/tag/v0.2.85",
                apkUrl = "https://github.com/PatentPuddle9/OpenTasker_/releases/download/v0.2.85/OpenTasker-v0.2.85-debug.apk",
                apkSizeBytes = 4_096L,
                apkSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ),
            result,
        )
    }

    @Test
    fun sameOlderAndNumericallyLargerVersionsCompareCorrectly() {
        assertEquals(
            UpdateCheckResult.NoUpdate,
            UpdateCheckProtocol.parseLatestRelease(release("v0.2.84"), "0.2.84"),
        )
        assertEquals(
            UpdateCheckResult.NoUpdate,
            UpdateCheckProtocol.parseLatestRelease(release("v0.2.83"), "0.2.84"),
        )
        assertTrue(
            UpdateCheckProtocol.parseLatestRelease(release("v0.10.0"), "0.9.99") is
                UpdateCheckResult.Available,
        )
    }

    @Test
    fun prereleaseDraftAndMalformedResponsesNeverReportAnUpdate() {
        assertEquals(
            UpdateCheckResult.NoUpdate,
            UpdateCheckProtocol.parseLatestRelease(release("v0.2.85", prerelease = true), "0.2.84"),
        )
        assertEquals(
            UpdateCheckResult.NoUpdate,
            UpdateCheckProtocol.parseLatestRelease(release("v0.2.85", draft = true), "0.2.84"),
        )
        assertTrue(
            UpdateCheckProtocol.parseLatestRelease("{\"tag_name\":\"v0.2.85\"}", "0.2.84") is
                UpdateCheckResult.Invalid,
        )
        assertTrue(
            UpdateCheckProtocol.parseLatestRelease(release("v0.2.85", url = "https://example.com"), "0.2.84") is
                UpdateCheckResult.Invalid,
        )
        assertTrue(
            UpdateCheckProtocol.parseLatestRelease("{\"tag_name\":true}", "0.2.84") is
                UpdateCheckResult.Invalid,
        )
    }

    @Test
    fun missingOrUntrustedApkAssetNeverCreatesAnInstallableUpdate() {
        val withoutAssets = """
            {
              "tag_name": "v0.2.85",
              "html_url": "https://github.com/PatentPuddle9/OpenTasker_/releases/tag/v0.2.85",
              "draft": false,
              "prerelease": false,
              "assets": []
            }
        """.trimIndent()
        assertTrue(UpdateCheckProtocol.parseLatestRelease(withoutAssets, "0.2.84") is UpdateCheckResult.Invalid)

        val invalidDigest = release("v0.2.85").replace(
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "sha256:bad",
        )
        assertTrue(UpdateCheckProtocol.parseLatestRelease(invalidDigest, "0.2.84") is UpdateCheckResult.Invalid)

        val invalidAssetUrl = release("v0.2.85").replace(
            "https://github.com/PatentPuddle9/OpenTasker_/releases/download/v0.2.85/OpenTasker-v0.2.85-debug.apk",
            "https://example.com/OpenTasker.apk",
        )
        assertTrue(UpdateCheckProtocol.parseLatestRelease(invalidAssetUrl, "0.2.84") is UpdateCheckResult.Invalid)
    }

    @Test
    fun branchDiscoveryExcludesMasterAndDefaultsToExperimental() {
        val branches = UpdateCheckProtocol.parseBranchesResponse(
            """[{"name":"feature/new-ui"},{"name":"master"},{"name":"Experimental"},{"name":"release-candidate"}]""",
        )
        assertEquals(listOf("Experimental", "feature/new-ui", "release-candidate"), branches)
        assertTrue(!UpdateCheckProtocol.isSelectableBranch("master"))
        assertTrue(!UpdateCheckProtocol.isSelectableBranch("MASTER"))
        assertTrue(runCatching { UpdateCheckProtocol.branchChannelTag("master") }.isFailure)
    }

    @Test
    fun fullBranchPageContinuesEvenWhenMasterIsFilteredOut() {
        val names = (0 until UpdateCheckProtocol.BRANCH_PAGE_SIZE).map { "feature-$it" } + "master"
        val payload = names.joinToString(prefix = "[", postfix = "]") { """{"name":"$it"}""" }
        val page = UpdateCheckProtocol.parseBranchPage(payload)

        assertTrue(page != null)
        assertTrue(page!!.hasMore)
        assertEquals(UpdateCheckProtocol.BRANCH_PAGE_SIZE, page.branches.size)
        assertTrue(page.branches.none { it.equals("master", ignoreCase = true) })

        val shortPayload = """[{"name":"Experimental"},{"name":"master"}]"""
        val shortPage = UpdateCheckProtocol.parseBranchPage(shortPayload)
        assertTrue(shortPage != null)
        assertTrue(!shortPage!!.hasMore)
        assertEquals(listOf("Experimental"), shortPage.branches)
    }

    @Test
    fun selectedBranchGetsItsOwnVerifiedPrereleaseAndAsset() {
        val branch = "UI-overhaul-experiment"
        val payload = branchRelease(branch, "0.2.120")
        assertEquals(
            UpdateCheckResult.Available(
                version = "0.2.120",
                url = UpdateCheckProtocol.RELEASES_URL_PREFIX + UpdateCheckProtocol.branchChannelTag(branch),
                apkUrl = UpdateCheckProtocol.branchApkUrl(branch),
                apkSizeBytes = 4096L,
                apkSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ),
            UpdateCheckProtocol.parseBranchRelease(payload, "0.2.119", branch),
        )
        assertTrue(
            UpdateCheckProtocol.parseBranchRelease(payload, "0.2.120", branch) is UpdateCheckResult.NoUpdate,
        )
        assertTrue(
            UpdateCheckProtocol.parseBranchRelease(payload, "0.2.119", "Experimental") is UpdateCheckResult.Invalid,
        )
        assertTrue(
            UpdateCheckProtocol.parseBranchRelease(payload, "0.2.119", "master") is UpdateCheckResult.Invalid,
        )
    }

    @Test
    fun branchRequestUsesTheSelectedBranchChannelAndNoCredentials() {
        val request = UpdateCheckProtocol.requestArguments("Experimental")
        assertEquals(UpdateCheckProtocol.branchReleaseApiUrl("Experimental"), request["url"])
        assertEquals(UpdateCheckProtocol.STATUS_VARIABLE, request["status_var"])
        assertEquals(
            "https://github.com/PatentPuddle9/OpenTasker_/releases/tag/",
            UpdateCheckProtocol.RELEASES_URL_PREFIX,
        )
        val branchesRequest = UpdateCheckProtocol.branchesRequestArguments(1)
        assertEquals(
            "https://api.github.com/repos/PatentPuddle9/OpenTasker_/branches?per_page=100&page=1",
            branchesRequest["url"],
        )
    }

    @Test
    fun requestIsHttpsOnlyBoundedAndDoesNotCarryIdentityOrCredentials() {
        val request = UpdateCheckProtocol.requestArguments()
        assertEquals("GET", request["method"])
        assertTrue(request.getValue("url").startsWith("https://"))
        assertEquals("none", request["redirects"])
        assertEquals(UpdateCheckProtocol.MAX_RESPONSE_BYTES.toString(), request["max_response_bytes"])
        assertEquals(UpdateCheckProtocol.REQUEST_TIMEOUT_SECONDS.toString(), request["timeout_sec"])
        assertTrue(request.keys.none { it.equals("authorization", ignoreCase = true) })
        assertTrue(request.values.none { it.contains("User-Agent", ignoreCase = true) })
    }

    private fun branchRelease(branch: String, version: String): String {
        val tag = UpdateCheckProtocol.branchChannelTag(branch)
        val title = UpdateCheckProtocol.branchReleaseTitlePrefix(branch) + version
        val asset = UpdateCheckProtocol.branchAssetName(branch)
        return """
            {
              "tag_name": "$tag",
              "name": "$title",
              "html_url": "${UpdateCheckProtocol.RELEASES_URL_PREFIX}$tag",
              "draft": false,
              "prerelease": true,
              "assets": [
                {
                  "name": "$asset",
                  "content_type": "application/vnd.android.package-archive",
                  "browser_download_url": "${UpdateCheckProtocol.branchApkUrl(branch)}",
                  "size": 4096,
                  "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
              ]
            }
        """.trimIndent()
    }

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        url: String = UpdateCheckProtocol.RELEASES_URL_PREFIX + tag,
    ): String = """
        {
          "tag_name": "$tag",
          "html_url": "$url",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [
            {
              "name": "OpenTasker-$tag-debug.apk",
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "https://github.com/PatentPuddle9/OpenTasker_/releases/download/$tag/OpenTasker-$tag-debug.apk",
              "size": 4096,
              "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            }
          ]
        }
    """.trimIndent()
}
