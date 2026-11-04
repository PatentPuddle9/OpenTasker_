package com.opentasker.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWidgetConfigPolishSourceTest {
    private val sourceRoot: Path = listOf(
        Path.of("src/main/java"),
        Path.of("app/src/main/java"),
    ).first(Files::exists)
    private val resRoot: Path = listOf(
        Path.of("src/main/res"),
        Path.of("app/src/main/res"),
    ).first(Files::exists)
    private val modelSourceRoot: Path = listOf(
        Path.of("core/model/src/main/kotlin"),
        Path.of("../core/model/src/main/kotlin"),
    ).first(Files::exists)

    @Test
    fun widgetCreatesItsOwnProtectedActionWithoutAnActionPicker() {
        val config = sourceRoot.resolve("com/opentasker/widget/TaskWidgetConfigActivity.kt").readText()
        val ownership = sourceRoot.resolve("com/opentasker/widget/WidgetProfileLinks.kt").readText()
        val selectors = modelSourceRoot.resolve("com/opentasker/core/model/ActionExecutionBinding.kt").readText()
        val actionList = sourceRoot.resolve("com/opentasker/ui/screens/ActiveAutomationLists.kt").readText()
        val provider = sourceRoot.resolve("com/opentasker/widget/TaskWidgetProvider.kt").readText()

        assertTrue("Widget-owned Actions should not show the normal delete/duplicate menu", actionList.contains("if (widgetOwned)"))
        assertTrue("Widget-owned Actions should be labeled as widgets", actionList.contains("Text(\"Widget\""))
        assertTrue("Widget setup should explain that it creates an owned Action", config.contains("Every widget gets its own protected Action"))
        assertTrue("Widget setup should save an Action automatically", config.contains("WidgetProfileLinks.createOrUpdateAction"))
        assertTrue("The first provider update should create an Action without waiting for the config Save button", provider.contains("sourceExecutionId = legacyTaskId.takeIf { sourceAction == null && it > 0L }"))
        assertTrue("New widgets should receive a default Action name immediately", provider.contains("\"Widget $widgetId\""))
        assertTrue("Each widget Action should be tagged with its owning widget ID", ownership.contains("widgetId = widgetId"))
        assertTrue("The widget Action should be disabled from trigger-based activation", ownership.contains("enabled = false"))
        assertTrue("Widget cleanup should delete the widget-owned Profile", ownership.contains("deleteWidgetOwnedProfile(widgetId)"))
        assertFalse("Widget setup must not ask users to choose an existing Action", config.contains("Choose an Action"))
        assertFalse("Widget setup must not show a list of Actions to select", config.contains("actions.forEach"))
        assertTrue("Widget-owned Actions must stay out of cross-Action selectors", selectors.contains("it.widgetId == null"))
        assertTrue("Widget setup should expose the icon library", config.contains("WidgetIconCatalog.all"))
        assertTrue("Widget setup should expose a live preview", config.contains("WidgetLivePreview"))
    }

    @Test
    fun shortcutsSectionCanRemoveWidgetActionsAndExplainsLauncherCleanup() {
        val shortcuts = sourceRoot.resolve("com/opentasker/ui/screens/ShortcutsScreen.kt").readText()
        val links = sourceRoot.resolve("com/opentasker/widget/WidgetProfileLinks.kt").readText()
        val resources = resRoot.resolve("values/strings.xml").readText()

        assertFalse("A newly pinned widget must not be hidden while its Action is being created", shortcuts.contains("if (!hasSavedBinding"))
        assertTrue("Shortcuts should expose a delete control", shortcuts.contains("Text(\"Delete\""))
        assertTrue("Delete should confirm before removing a widget Action", shortcuts.contains("AlertDialog"))
        assertTrue("Delete should remove the Action and OpenTasker widget configuration", shortcuts.contains("onDeleteWidget"))
        assertTrue("Widget deletion should mark the launcher instance as removed", links.contains("keyRemoved(widgetId), true"))
        assertTrue("The cleared launcher widget should explain how to remove it", resources.contains("widget_removed_from_shortcuts_subtitle"))
    }

    @Test
    fun homeScreenWidgetUsesBrandedVisualTreatment() {
        val layout = resRoot.resolve("layout/widget_task.xml").readText()
        val background = resRoot.resolve("drawable/widget_task_background.xml").readText()
        val colors = resRoot.resolve("values/colors.xml").readText()

        assertTrue("Widget should use a transparent host so the selected silhouette defines its shape", layout.contains("@android:color/transparent"))
        assertTrue("Widget should render its generated app-icon background", layout.contains("@+id/widget_background"))
        assertTrue("Widget should include a visible secondary action cue", layout.contains("@string/widget_tap_to_run"))
        assertTrue("Widget shape choices should be generated by the renderer", sourceRoot.resolve("com/opentasker/widget/WidgetStyle.kt").readText().contains("WidgetShapeCatalog"))
        assertTrue("Widget colors should be centralized in resources", colors.contains("widget_primary"))
    }

    @Test
    fun widgetAndShortcutRunnerFinishesAfterExecutionErrors() {
        val source = sourceRoot.resolve("com/opentasker/widget/TaskRunActivity.kt").readText()

        assertTrue("Runner should route invalid or unavailable bindings through a finish helper", source.contains("finishWithMessage"))
        assertTrue("Runner should catch Action execution failures", source.contains("catch (e: Exception)"))
        assertTrue("Runner should log Action execution failures", source.contains("Action run failed"))
        assertTrue("Runner should resolve the private execution record from the widget Action ID", source.contains("EXTRA_PROFILE_ID"))
    }
}
