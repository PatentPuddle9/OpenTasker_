package com.opentasker.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
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
    fun customizationScreenIncludesTaskIconColorGradientAndPreviewControls() {
        val source = sourceRoot.resolve("com/opentasker/widget/TaskWidgetConfigActivity.kt").readText()
        assertTrue(source.contains("WidgetIconCatalog.all"))
        assertTrue(source.contains("WidgetColorEditor"))
        assertTrue(source.contains("HEX color"))
        assertTrue(source.contains("Switch(checked = useGradient"))
        assertTrue(source.contains("WidgetLivePreview"))
        assertTrue(source.contains("Add widget to home screen"))
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
        assertTrue(provider.contains("TaskRunActivity.EXTRA_TASK_ID"))
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
}
