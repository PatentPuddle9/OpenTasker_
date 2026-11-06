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
    fun actionsIsAnIconGridAndOpensItsInlineActionEditor() {
        val lists = source("app/src/main/java/com/opentasker/ui/screens/ActiveAutomationLists.kt")
        val editor = source("app/src/main/java/com/opentasker/ui/screens/EditorDialogs.kt")
        assertTrue(lists.contains("LazyVerticalGrid("))
        assertTrue(lists.contains("Card(\n        onClick = onEdit"))
        assertTrue(lists.contains("WidgetIconCatalog.all.any"))
        assertTrue(lists.contains("profile.widgetId != null"))
        assertTrue(editor.contains("Text(\"Actions\""))
        assertTrue(editor.contains("ActionRow("))
        assertTrue(editor.contains("WidgetIconCatalog.all.forEach"))
        assertFalse(editor.contains("R.string.profile_enter_task"))
        assertFalse(editor.contains("R.string.profile_exit_task"))
        assertTrue(editor.contains("R.string.profile_fallback_task"))
        assertTrue(editor.contains("onCreateFallbackAction"))
    }

    @Test
    fun actionRowsCanTestFromTheirIconOrOverflowMenu() {
        val lists = source("app/src/main/java/com/opentasker/ui/screens/ActiveAutomationLists.kt")
        val strings = source("app/src/main/res/values/strings.xml")

        assertTrue(lists.contains("SingleActionRun.isRunnableAlone(action)"))
        assertTrue(lists.contains("onClick = onRun"))
        assertTrue(lists.contains("R.string.action_run_alone"))
        assertTrue(strings.contains("<string name=\"action_run_alone\">Test</string>"))
    }

    @Test
    fun actionCardsCanTestFromTheirIconAndOverflowMenu() {
        val lists = source("app/src/main/java/com/opentasker/ui/screens/ActiveAutomationLists.kt")

        assertTrue(lists.contains("onRun = { onRunProfile(profile) }"))
        assertTrue(lists.contains("onTest = onRun"))
        assertTrue(lists.contains("ContextualMenuAction(R.string.action_run_alone, enabled = testEnabled, onClick = onTest)"))
        assertTrue(lists.contains("contentDescription = testDescription"))
    }

    @Test
    fun shortcutsListCanCreateAndEditWidgetsAndWidgetProfilesAreOwned() {
        val shortcuts = source("app/src/main/java/com/opentasker/ui/screens/ShortcutsScreen.kt")
        val provider = source("app/src/main/java/com/opentasker/widget/TaskWidgetProvider.kt")
        val links = source("app/src/main/java/com/opentasker/widget/WidgetProfileLinks.kt")
        val config = source("app/src/main/java/com/opentasker/widget/TaskWidgetConfigActivity.kt")
        val viewModel = source("app/src/main/java/com/opentasker/ui/screens/ActiveAutomationViewModel.kt")

        assertTrue(shortcuts.contains("requestPinAppWidget"))
        assertTrue(shortcuts.contains("TaskWidgetConfigActivity::class.java"))
        assertTrue(config.contains("WidgetProfileLinks.syncProfile"))
        assertTrue(links.contains("widgetId = widgetId"))
        assertTrue(provider.contains("WidgetProfileLinks.deleteProfile"))
        assertTrue(viewModel.contains("R.string.widget_profile_owned"))
    }

    @Test
    fun widgetsDestinationUsesWidgetTerminology() {
        val strings = source("app/src/main/res/values/strings.xml")
        val widgets = source("app/src/main/java/com/opentasker/ui/screens/ShortcutsScreen.kt")

        assertTrue(strings.contains("<string name=\"nav_shortcuts\">Widgets</string>"))
        assertTrue(strings.contains("Delete the widget from Widgets to remove it."))
        assertTrue(widgets.contains("No widgets yet"))
        assertTrue(widgets.contains("Widgets on your home screen"))
        assertTrue(widgets.contains("OpenTasker's Widgets list"))
        assertFalse(widgets.contains("No shortcuts yet"))
        assertFalse(widgets.contains("Home-screen shortcuts"))
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
