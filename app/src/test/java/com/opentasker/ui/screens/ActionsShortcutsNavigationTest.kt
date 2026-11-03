package com.opentasker.ui.screens

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionsShortcutsNavigationTest {
    private val repoRoot: Path = listOf(Path.of("."), Path.of(".."))
        .first { Files.exists(it.resolve("README.md")) && Files.exists(it.resolve("app/build.gradle.kts")) }
        .toAbsolutePath()
        .normalize()

    private fun source(path: String): String = repoRoot.resolve(path).readText()

    @Test
    fun primaryNavigationUsesActionsAndShortcutsInsteadOfSeparateTasks() {
        val shell = source("app/src/main/java/com/opentasker/ui/screens/AppShellChrome.kt")
        assertTrue(shell.contains("Actions(R.string.nav_actions)"))
        assertTrue(shell.contains("Shortcuts(R.string.nav_shortcuts)"))
        assertTrue(shell.contains("OpenTaskerScreen.Actions"))
        assertTrue(shell.contains("OpenTaskerScreen.Shortcuts"))
        assertFalse(shell.contains("Profiles(R.string.nav_profiles)"))
        assertFalse(shell.contains("Tasks(R.string.nav_tasks)"))
    }

    @Test
    fun actionsIsAnIconGridAndProfileOpensItsInlineTaskEditor() {
        val lists = source("src/main/java/com/opentasker/ui/screens/ActiveAutomationLists.kt")
        val editor = source("src/main/java/com/opentasker/ui/screens/EditorDialogs.kt")
        assertTrue(lists.contains("LazyVerticalGrid("))
        assertTrue(lists.contains("Card(\n        onClick = onEdit"))
        assertTrue(lists.contains("WidgetIconCatalog.all.any"))
        assertTrue(lists.contains("profile.widgetId != null"))
        assertTrue(editor.contains("When this profile is triggered"))
        assertTrue(editor.contains("ActionRow("))
        assertTrue(editor.contains("WidgetIconCatalog.all.forEach"))
    }

    @Test
    fun shortcutsListCanCreateAndEditWidgetsAndWidgetProfilesAreOwned() {
        val shortcuts = source("src/main/java/com/opentasker/ui/screens/ShortcutsScreen.kt")
        val provider = source("app/src/main/java/com/opentasker/widget/TaskWidgetProvider.kt")
        val links = source("src/main/java/com/opentasker/widget/WidgetProfileLinks.kt")
        val config = source("src/main/java/com/opentasker/widget/TaskWidgetConfigActivity.kt")
        val viewModel = source("src/main/java/com/opentasker/ui/screens/ActiveAutomationViewModel.kt")

        assertTrue(shortcuts.contains("requestPinAppWidget"))
        assertTrue(shortcuts.contains("TaskWidgetConfigActivity::class.java"))
        assertTrue(config.contains("WidgetProfileLinks.syncProfile"))
        assertTrue(links.contains("widgetId = widgetId"))
        assertTrue(provider.contains("WidgetProfileLinks.deleteProfile"))
        assertTrue(viewModel.contains("R.string.widget_profile_owned"))
    }

    @Test
    fun profileSchemaRegistersBothWidgetOwnershipMigrations() {
        val model = source("core/model/src/main/kotlin/com/opentasker/core/model/Profile.kt")
        val database = source("core/storage/src/main/kotlin/com/opentasker/core/storage/DatabaseMigrations.kt")
        assertTrue(model.contains("@Transient val widgetId: Int? = null"))
        assertTrue(database.contains("Migration(15, 16)"))
        assertTrue(database.contains("Migration(16, 17)"))
        assertTrue(database.contains("MIGRATION_16_17,"))
    }
}
