package com.opentasker.ui.screens

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupSpecialAccessContractTest {
    private val repoRoot: Path = listOf(Path.of("."), Path.of(".."))
        .first { Files.exists(it.resolve("README.md")) && Files.exists(it.resolve("app/build.gradle.kts")) }
        .toAbsolutePath()
        .normalize()

    @Test
    fun displayOverOtherAppsHasARealStatusCheckAndIsDiscoverableInSetup() {
        val rows = read("app/src/main/java/com/opentasker/ui/screens/SetupRows.kt")
        val screen = read("app/src/main/java/com/opentasker/ui/screens/PermissionOnboardingScreen.kt")
        val manifest = read("app/src/main/AndroidManifest.xml")
        val strings = read("app/src/main/res/values/strings.xml")

        assertTrue(rows.contains("Settings.canDrawOverlays(context)"))
        assertTrue(rows.contains("Settings.ACTION_MANAGE_OVERLAY_PERMISSION"))
        assertTrue(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue(strings.contains("""name="setup_overlay_access_title">Display over other apps"""))
        // The row is always discoverable under Optional, then becomes a required Setup item when
        // an enabled profile or onboarding template actually uses an overlay.
        assertTrue(screen.contains("SetupRequirement.OVERLAY in item.requirements"))
        assertTrue(screen.contains("section = if (required) SetupSection.NEEDED else SetupSection.OPTIONAL"))
        assertTrue(screen.contains("optional = !required"))
    }

    @Test
    fun unknownAppInstallAccessUsesAndroidsPerAppSettingsAndIsDeclared() {
        val rows = read("app/src/main/java/com/opentasker/ui/screens/SetupRows.kt")
        val manifest = read("app/src/main/AndroidManifest.xml")
        val strings = read("app/src/main/res/values/strings.xml")

        assertTrue(rows.contains("context.packageManager.canRequestPackageInstalls()"))
        assertTrue(rows.contains("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES"))
        assertTrue(rows.contains("Uri.parse(\"package:\\${context.packageName}\")"))
        assertTrue(rows.contains("section = SetupSection.OPTIONAL"))
        assertTrue(manifest.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertTrue(strings.contains("""name="setup_install_unknown_apps_title">Install unknown apps"""))
        assertTrue(strings.contains("setup_install_unknown_apps_body"))
    }

    private fun read(path: String): String = repoRoot.resolve(path).readText()
}
