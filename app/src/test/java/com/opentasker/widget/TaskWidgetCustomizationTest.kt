package com.opentasker.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskWidgetCustomizationTest {
    private val sourceRoot: Path = listOf(
        Path.of("src/main/java"),
        Path.of("app/src/main/java"),
    ).first(Files::exists)
    private val resRoot: Path = listOf(
        Path.of("src/main/res"),
        Path.of("app/src/main/res"),
    ).first(Files::exists)

    @Test
    fun iconLibraryOffersUniqueDrawableBackedVectorIcons() {
        val keys = WidgetIconCatalog.all.map(WidgetIconOption::key)
        assertTrue("The icon picker should offer a useful icon collection", keys.size >= 20)
        assertEquals("Icon selection must not highlight duplicates", keys.size, keys.toSet().size)
        assertTrue("Every option must have a drawable resource", WidgetIconCatalog.all.all { it.drawableRes != 0 })
        assertTrue(WidgetIconCatalog.default in WidgetIconCatalog.all)
        assertEquals("bolt", WidgetIconCatalog.resolve("⚡︎").key)
        assertEquals("play", WidgetIconCatalog.resolve("▶︎").key)
    }

    @Test
    fun customizationScreenIncludesActionNameIconColorGradientAndPreviewControls() {
        val source = sourceRoot.resolve("com/opentasker/widget/TaskWidgetConfigActivity.kt").readText()
        assertTrue(source.contains("ActionIconPickerDialog"))
        assertTrue(source.contains("WidgetIconCatalog.resolveKey"))
        assertTrue(source.contains("WidgetColorEditor"))
        assertTrue(source.contains("HEX color"))
        assertTrue(source.contains("Switch(checked = useGradient"))
        assertTrue(source.contains("WidgetLivePreview"))
        assertTrue(source.contains("Widget Action name"))
        assertTrue(source.contains("Create widget and Action"))
        assertTrue(source.contains("Every widget gets its own protected Action"))
        assertTrue(source.contains("WidgetProfileLinks.createOrUpdateAction"))
        assertTrue(!source.contains("Choose an Action"))
    }

    @Test
    fun eachWidgetStoresItsOwnStyleAndRendersItThroughRemoteViews() {
        val provider = sourceRoot.resolve("com/opentasker/widget/TaskWidgetProvider.kt").readText()
        val config = sourceRoot.resolve("com/opentasker/widget/TaskWidgetConfigActivity.kt").readText()
        val style = sourceRoot.resolve("com/opentasker/widget/WidgetStyle.kt").readText()
        val layout = resRoot.resolve("layout/widget_task.xml").readText()
        val info = resRoot.resolve("xml/widget_task_info.xml").readText()

        assertTrue(config.contains("keyIconColor(widgetId)"))
        assertTrue(config.contains("keyBackgroundColor(widgetId)"))
        assertTrue(config.contains("keyGradient(widgetId)"))
        assertTrue(provider.contains("WidgetBackgroundRenderer.render"))
        assertTrue(provider.contains("views.setImageViewResource(R.id.widget_icon"))
        assertTrue(provider.contains("setColorFilter"))
        assertTrue(provider.contains("TaskRunActivity.EXTRA_PROFILE_ID"))
        assertTrue("The initial widget provider update should create a private Action", provider.contains("WidgetProfileLinks.createOrUpdateAction"))
        assertTrue("The default Action should be named for its new widget", provider.contains("\"Widget \$widgetId\""))
        assertTrue(!provider.contains("TaskRunActivity.EXTRA_TASK_ID"))
        assertTrue(style.contains("LinearGradient"))
        assertTrue(layout.contains("@+id/widget_background"))
        assertTrue(layout.contains("@+id/widget_icon"))
        assertTrue(info.contains("android:targetCellWidth=\"1\""))
        assertTrue(info.contains("android:targetCellHeight=\"1\""))
        assertTrue(info.contains("android:minResizeWidth=\"40dp\""))
        assertTrue(info.contains("android:minResizeHeight=\"40dp\""))
        assertTrue(config.contains("WidgetShapeCatalog.all"))
        assertTrue(config.contains("selectedShape"))
        assertTrue(config.contains("shape = selectedShape"))
        assertTrue(provider.contains("keyShape(widgetId)"))
        assertTrue(style.contains("WidgetShapeCatalog"))
        assertTrue(style.contains("\"circle\""))
        assertTrue(style.contains("\"teardrop\""))
        assertTrue(provider.contains("onAppWidgetOptionsChanged"))
        assertTrue(provider.contains("widget_task_compact"))
        val compactLayout = resRoot.resolve("layout/widget_task_compact.xml").readText()
        assertTrue(compactLayout.contains("@+id/widget_icon"))
        assertTrue(compactLayout.contains("android:layout_width=\"32dp\""))
        assertTrue(compactLayout.contains("android:visibility=\"gone\""))
    }
    @Test
    fun widgetDisplaySupportsSlideshowStatesImagesAndActionShortcuts() {
        val display = sourceRoot.resolve("com/opentasker/widget/WidgetDisplay.kt").readText()
        val editor = sourceRoot.resolve("com/opentasker/widget/WidgetDisplayConfigEditor.kt").readText()
        val action = sourceRoot.resolve("com/opentasker/core/actions/WidgetDisplayControlAction.kt").readText()
        val catalog = sourceRoot.resolve("com/opentasker/core/actions/ActionCatalog.kt").readText()
        val metadata = sourceRoot.resolve("com/opentasker/core/actions/ActionMetadata.kt").readText()

        assertTrue(display.contains("data class WidgetDisplayState"))
        assertTrue(display.contains("MAX_STATES = 75"))
        assertFalse("Widget Displays should not render previous/next arrows", display.contains("widget_display_navigation"))
        assertTrue(display.contains("WidgetDisplayStateController"))
        assertTrue(display.contains("WidgetDisplayElementType.IMAGE"))
        assertTrue(display.contains("WidgetDisplayElementType.PROGRESS"))
        assertTrue(display.contains("TaskRunActivity.EXTRA_PROFILE_ID"))
        assertTrue(editor.contains("Text(\"New state\")"))
        assertTrue(editor.contains("Text(\"Duplicate\")"))
        assertTrue(editor.contains("Text(\"Delete\")"))
        assertTrue(editor.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(editor.contains("ActionIconPickerDialog"))
        assertTrue("Widget Displays can select Actions owned by Widget Shortcuts", editor.contains("decoded.issue == null && it.enterTaskId > 0L"))
        assertFalse("Widget shortcut-owned Actions must remain selectable here", editor.contains("it.widgetId == null && it.enterTaskId > 0L"))
        assertTrue(action.contains("WidgetDisplayStateController.control"))
        assertTrue(catalog.contains("define(\"widget.display.control\""))
        assertTrue(metadata.contains("id = \"widget.display.control\""))
    }

    @Test
    fun widgetDisplayUsesFixedRemoteViewsSlotsAndNeverBuildsDynamicChildTrees() {
        val display = sourceRoot.resolve("com/opentasker/widget/WidgetDisplay.kt").readText()
        val root = resRoot.resolve("layout/widget_display.xml").readText()
        val fallback = resRoot.resolve("layout/widget_display_fallback.xml").readText()

        assertTrue("The fixed layout should define eight content slots",
            (1..8).all { root.contains("@+id/widget_display_slot_${it}_root") })
        assertTrue("The renderer should use one pre-inflated layout",
            display.contains("R.layout.widget_display"))
        assertFalse("RemoteViews must not add dynamic child trees",
            display.contains("views.addView"))
        assertFalse("RemoteViews must not clear and rebuild the content tree",
            display.contains("views.removeAllViews"))
        assertTrue("Positioned canvas slots need runtime width and height",
            display.contains("setViewLayoutWidth") && display.contains("setViewLayoutHeight"))
        assertTrue("Positioned canvas slots need left and top margins",
            display.contains("setViewLayoutMargin(rootId, Gravity.LEFT") &&
                display.contains("setViewLayoutMargin(rootId, Gravity.TOP"))
        assertTrue("Absolute positioning must be guarded to Android 12 and newer",
            display.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.S"))
        val legacy = resRoot.resolve("layout/widget_display_legacy.xml").readText()
        assertTrue("Older Android versions retain a stacked fallback layout",
            legacy.contains("@+id/widget_display_slot_1_root"))
        assertTrue("The content types must be represented by static slots",
            listOf("_text", "_image", "_action", "_progress", "_divider", "_spacer")
                .all { root.contains(it) })
        assertTrue("The renderer catches errors and installs a recovery view",
            display.contains("createRecoveryViews"))
        assertTrue("The recovery view explains how to repair the widget",
            display.contains("Tap here to reopen setup and repair it"))
        assertTrue("The recovery view has a clickable root",
            fallback.contains("@+id/widget_display_fallback_root"))

        assertTrue("Bitmap data is bounded well below the Binder transaction limit",
            display.contains("MAX_REMOTE_BITMAP_BYTES = 480 * 1024"))
        assertTrue("Widget background bitmaps stay small",
            display.contains("BACKGROUND_WIDTH_PX = 192") &&
                display.contains("BACKGROUND_HEIGHT_PX = 144"))
        assertTrue("Widget image bitmaps are sampled before they are sent to the launcher",
            display.contains("IMAGE_WIDTH_PX = 160") &&
                display.contains("IMAGE_HEIGHT_PX = 90"))
        assertTrue("Uploaded icons are sampled to compact dimensions",
            display.contains("ICON_MAX_DIMENSION_PX = 40"))
        assertTrue("Small square widgets should use a two-by-two grid",
            display.contains("ContentMode.SQUARE_GRID"))
        assertTrue("Wide widgets should use a four-column grid",
            display.contains("ContentMode.WIDE_GRID"))
        assertTrue("Tall custom widgets should use a two-column grid",
            display.contains("ContentMode.PORTRAIT_GRID"))
        assertTrue("Compact widgets should show a summary instead of silently dropping content",
            display.contains("overflowSummary(1)") && display.contains("overflowSummary(3)"))
        assertTrue("Scene and Widget canvas elements should snap while the finger is still down",
            display.contains("setViewLayoutMargin(rootId, Gravity.LEFT") &&
                sourceRoot.resolve("com/opentasker/widget/WidgetDisplayCanvas.kt").readText()
                    .contains("dragX = (result.snappedX - element.xDp).toFloat()"))
    }

    @Test
    fun widgetStateEditorUsesTheSharedDraggableCanvasAndCanvaAlignmentTools() {
        val editor = sourceRoot.resolve("com/opentasker/widget/WidgetDisplayConfigEditor.kt").readText()
        val canvas = sourceRoot.resolve("com/opentasker/widget/WidgetDisplayCanvas.kt").readText()
        val model = sourceRoot.resolve("com/opentasker/widget/WidgetDisplay.kt").readText()
        val sceneCanvas = sourceRoot.resolve("com/opentasker/ui/screens/SceneEditorCanvas.kt").readText()
        val guides = sourceRoot.resolve("com/opentasker/core/scenes/SceneAlignmentGuides.kt").readText()

        assertTrue(editor.contains("WidgetDisplayCanvas("))
        assertTrue(editor.contains("SceneSelectionAlignment.CENTER_ON_CANVAS"))
        assertTrue(editor.contains("label = { Text(\"X\") }"))
        assertTrue(editor.contains("label = { Text(\"Y\") }"))
        assertTrue(editor.contains("label = { Text(\"Width\") }"))
        assertTrue(editor.contains("label = { Text(\"Height\") }"))
        assertTrue(canvas.contains("detectDragGestures"))
        assertTrue(canvas.contains("onResize"))
        assertTrue(canvas.contains("SceneAlignmentGuides.findGuides"))
        assertTrue(sceneCanvas.contains("CanvasAlignmentGuideOverlay"))
        assertTrue(sceneCanvas.contains("dragX = alignment.snappedX * canvasWidth / safeSceneWidth"))
        assertTrue(guides.contains("findHorizontalSpacing"))
        assertTrue(guides.contains("findVerticalSpacing"))
        assertTrue(model.contains("val xDp: Int = -1"))
        assertTrue(model.contains("val yDp: Int = -1"))
        assertTrue(model.contains("WidgetDisplayCanvasMetrics.normalize(state.elements)"))
    }

    @Test
    fun appLaunchRefreshesExistingWidgetDisplaysAfterAnUpgrade() {
        val main = sourceRoot.resolve("com/opentasker/app/MainActivity.kt").readText()
        assertTrue(main.contains("WidgetDisplayProvider.requestRefresh(applicationContext)"))
    }

    @Test
    fun widgetsTabHasOneAnimatedCreateMenuForBothWidgetTypes() {
        val screen = sourceRoot.resolve("com/opentasker/ui/screens/ShortcutsScreen.kt").readText()
        val host = sourceRoot.resolve("com/opentasker/ui/screens/ActiveAutomationUi.kt").readText()

        assertTrue(host.contains("OpenTaskerScreen.Shortcuts -> WidgetsCreateFab"))
        assertTrue(screen.contains("AnimatedVisibility("))
        assertTrue(screen.contains("slideInVertically"))
        assertTrue(screen.contains("scaleIn"))
        assertTrue(screen.contains("Text(\"Widget Shortcut\""))
        assertTrue(screen.contains("Text(\"Widget Display\""))
        assertFalse("The list itself should not have a second create button", screen.contains("onCreateNew"))
        assertFalse("The list itself should not have a separate Display button", screen.contains("OutlinedButton(onClick = { showDisplaySizes"))
    }

    @Test
    fun widgetDisplayAnnouncesTheSeventyFiveStateLimitOnlyAfterTheLimitIsReached() {
        val editor = sourceRoot.resolve("com/opentasker/widget/WidgetDisplayConfigEditor.kt").readText()
        assertTrue(editor.contains("states.size >= WidgetDisplayStore.MAX_STATES"))
        assertTrue(editor.contains("showStateLimitDialog = true"))
        assertTrue(editor.contains("A Widget Display can contain up to 75 states."))
        assertTrue(editor.contains("if (showStateLimitDialog)"))
        assertTrue(editor.contains("New state"))
        assertFalse(editor.substringBefore("if (showStateLimitDialog)").contains("up to 75 states"))
    }

    @Test
    fun widgetDisplayOffersFourLockedSizesAndOneResizableCustomVariant() {
        val provider = sourceRoot.resolve("com/opentasker/widget/WidgetDisplayProvider.kt").readText()
        val manifest = sourceRoot.resolve("../AndroidManifest.xml").readText()
        assertTrue(provider.contains("COMPACT"))
        assertTrue(provider.contains("SQUARE"))
        assertTrue(provider.contains("WIDE"))
        assertTrue(provider.contains("LARGE"))
        assertTrue(provider.contains("CUSTOM"))

        val sizes = listOf("compact", "square", "wide", "large", "custom")
        sizes.forEach { size ->
            val info = resRoot.resolve("xml/widget_display_${size}_info.xml").readText()
            assertTrue(info.contains("android:initialLayout=\"@layout/widget_display_fallback\""))
            assertTrue(info.contains("android:previewLayout=\"@layout/widget_display_fallback\""))
        }
        listOf("compact", "square", "wide", "large").forEach { size ->
            val info = resRoot.resolve("xml/widget_display_${size}_info.xml").readText()
            assertTrue(info.contains("android:resizeMode=\"none\""))
        }
        val custom = resRoot.resolve("xml/widget_display_custom_info.xml").readText()
        assertTrue(custom.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(manifest.contains("WidgetDisplayCustomProvider"))
        assertTrue(manifest.contains("WidgetDisplayConfigActivity"))
    }

}
