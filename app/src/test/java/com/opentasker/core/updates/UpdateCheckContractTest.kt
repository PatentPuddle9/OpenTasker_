package com.opentasker.core.updates

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckContractTest {
    private val repoRoot: Path = listOf(Path.of("."), Path.of(".."))
        .first { Files.exists(it.resolve("README.md")) && Files.exists(it.resolve("app/build.gradle.kts")) }
        .toAbsolutePath()
        .normalize()

    @Test
    fun workerIsPeriodicAndStandardDistributionGated() {
        val worker = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckWorker.kt")
        val runner = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckRunner.kt")
        val settings = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckSettings.kt")
        assertTrue(worker.contains("class UpdateCheckWorker"))
        assertTrue(worker.contains("PeriodicWorkRequestBuilder<UpdateCheckWorker>"))
        assertTrue(worker.contains("UpdateCheckRunner.checkAndRecord(applicationContext, settings.selectedBranch)"))
        assertTrue(runner.contains("UpdateCheckProtocol.requestArguments(branch)"))
        assertTrue(runner.contains("fetchAvailableBranches"))
        assertTrue(runner.contains("parseBranchPage(payload)"))
        assertTrue(runner.contains("if (!page.hasMore)"))
        assertTrue(settings.contains("KEY_SELECTED_BRANCH"))
        assertTrue(settings.contains("setSelectedBranch(branch)"))
        assertTrue(worker.contains("UpdateCheckAvailability.isAvailable()"))
        assertTrue(worker.contains("STANDARD_DISTRIBUTION = \"standard\""))
        assertTrue(worker.contains("cancelUniqueWork"))
        assertFalse(worker.contains("DownloadManager"))
        assertFalse(worker.contains("PackageInstaller"))
        assertTrue(settings.contains("enabled: Boolean = false"))
        assertTrue(settings.contains("getBoolean(KEY_ENABLED, false)"))
    }

    @Test
    fun setupCardAndStartupSyncAreExplicitlyDistributionGated() {
        val setup = read("app/src/main/java/com/opentasker/ui/screens/PermissionOnboardingScreen.kt")
        val application = read("app/src/main/java/com/opentasker/app/OpenTaskerApp_NoHilt.kt")
        assertTrue(setup.contains("UpdateCheckAvailability.isAvailable()"))
        assertTrue(setup.contains("UpdateCheckSetupCard"))
        assertTrue(setup.contains("setup_update_branch_label"))
        assertTrue(setup.contains("DropdownMenuItem"))
        assertTrue(setup.contains("setUpdateSourceBranch"))
        assertTrue(setup.contains("availableUpdateBranches.filter(UpdateCheckProtocol::isSelectableBranch)"))
        assertTrue(setup.contains("fun checkForUpdatesNow()"))
        assertTrue(setup.contains("onCheckNow = setupViewModel::checkForUpdatesNow"))
        assertTrue(setup.contains("setup_update_check_now"))
        assertTrue(setup.contains("setup_update_status_failed"))
        assertTrue(application.contains("UpdateCheckWorker.enqueueIfEnabled(this)"))
        assertFalse(setup.contains("onOpenRelease"))
        assertTrue(setup.contains("FileProvider.getUriForFile"))
        assertTrue(setup.contains("Intent.ACTION_INSTALL_PACKAGE"))
        assertTrue(setup.contains("canRequestPackageInstalls()"))
    }

    @Test
    fun installedBuildIdentifiesItsBranchAndDefaultsUpdatesToThatChannel() {
        val gradle = read("app/build.gradle.kts")
        val workflow = read(".github/workflows/build.yml")
        val settings = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckSettings.kt")
        val runner = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckRunner.kt")
        val setup = read("app/src/main/java/com/opentasker/ui/screens/PermissionOnboardingScreen.kt")
        assertTrue(gradle.contains("openTaskerBuildBranch"))
        assertTrue(gradle.contains("UI-overhaul-experiment"))
        assertTrue(gradle.contains("buildConfigField(\"Boolean\", \"IS_BETA\", appIsBetaBuild.toString())"))
        assertTrue(gradle.contains("buildConfigField(\"String\", \"UPDATE_SOURCE_BRANCH\""))
        assertTrue(workflow.contains("-PopenTaskerBuildBranch=\"\$GITHUB_REF_NAME\""))
        assertTrue(settings.contains("BuildConfig.UPDATE_SOURCE_BRANCH"))
        assertTrue(settings.contains("UpdateCheckProtocol.normalizeBuildSourceBranch"))
        assertTrue(runner.contains("branch: String = BuildConfig.UPDATE_SOURCE_BRANCH"))
        val protocol = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckProtocol.kt")
        assertTrue(protocol.contains("branch: String = BuildConfig.UPDATE_SOURCE_BRANCH"))
        assertTrue(setup.contains("BuildConfig.IS_BETA"))
        assertTrue(setup.contains("setup_update_beta_badge"))
    }

    @Test
    fun releaseWorkflowPublishesBetaAsPrereleaseAndKeepsExperimentalStable() {
        val workflow = read(".github/workflows/release.yml")
        assertTrue(workflow.contains("branches:\\n      - Experimental\\n      - BETA"))
        assertTrue(workflow.contains("--branch"))
        assertTrue(workflow.contains("tag=" + "\"" + "beta-v" + "$" + "version_name" + "\""))
        assertTrue(workflow.contains("asset_name=" + "\"" + "OpenTasker-Beta-v" + "$" + "version_name-debug.apk" + "\""))
        assertTrue(workflow.contains("title=" + "\"" + "OpenTasker Beta v" + "$" + "VERSION" + "\""))
        assertTrue(workflow.contains("release_flags+=(--prerelease)"))
        assertTrue(workflow.contains("release_channel=" + "\"" + "Experimental" + "\""))
        assertTrue(workflow.contains("title=" + "\"" + "OpenTasker " + "$" + "TAG" + "\""))
    }

    @Test
    fun downloadedApkIsPinnedToReleaseDigestAndVerifiedBeforeInstallerLaunch() {
        val protocol = read("app/src/main/java/com/opentasker/core/updates/UpdateCheckProtocol.kt")
        val downloader = read("app/src/main/java/com/opentasker/core/updates/UpdateApkDownloader.kt")
        val manifest = read("app/src/main/AndroidManifest.xml")
        val paths = read("app/src/main/res/xml/update_file_paths.xml")

        assertTrue(protocol.contains("browser_download_url"))
        assertTrue(protocol.contains("asset[\"digest\"]"))
        assertTrue(protocol.contains("APK_ASSET_SUFFIX = \"-debug.apk\""))
        assertTrue(downloader.contains("MessageDigest.getInstance(\"SHA-256\")"))
        assertTrue(downloader.contains("UpdateCheckProtocol.branchApkUrl(sourceBranch)"))
        assertTrue(downloader.contains("getPackageArchiveInfo"))
        assertTrue(downloader.contains("archive.versionName != version"))
        assertTrue(downloader.contains("archiveVersionCode <= installed.versionCodeLongCompat()"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("\${applicationId}.fileprovider"))
        assertTrue(manifest.contains("@xml/update_file_paths"))
        assertTrue(paths.contains("update-apks/"))
    }

    @Test
    fun branchChannelPublisherOnlyPublishesNonMasterPrereleaseChannels() {
        val workflow = read(".github/workflows/publish-branch-updates.yml")
        assertTrue(workflow.contains("- '!master'"))
        assertTrue(workflow.contains("--prerelease"))
        assertTrue(workflow.contains("gh release upload"))
        assertTrue(workflow.contains("--clobber"))
        assertTrue(workflow.contains("sha256sum"))
        assertTrue(workflow.contains("OpenTasker-branch-\$" + "branch_hash-debug.apk"))
    }

    private fun read(path: String): String = repoRoot.resolve(path).readText()
}
