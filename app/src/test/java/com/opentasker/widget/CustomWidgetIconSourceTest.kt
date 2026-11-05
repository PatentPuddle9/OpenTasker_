package com.opentasker.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWidgetIconSourceTest {
    private val sourceRoot: Path = listOf(
        Path.of("src/main/java"),
        Path.of("app/src/main/java"),
    ).first(Files::exists)

    @Test
    fun settingsCanUploadNameRecolorPreviewRenameAndDeleteCustomIcons() {
        val settings = sourceRoot.resolve("com/opentasker/ui/screens/CustomIconsSettingsCard.kt").readText()
        val store = sourceRoot.resolve("com/opentasker/widget/CustomWidgetIconStore.kt").readText()

        assertTrue("Custom icon uploads should use the Android image picker", settings.contains("OpenDocument"))
        assertTrue("The picker should accept image files only", settings.contains("arrayOf(\"image/*\")"))
        assertTrue("Users should give each uploaded icon a name", settings.contains("Icon name"))
        assertTrue("Users should be able to toggle recolorability", settings.contains("addRecolorable"))
        assertTrue("Settings should show the recolored tint preview", settings.contains("Recolored preview"))
        assertTrue("Uploaded images should be copied to app-private storage", store.contains("filesDir, \"custom_icons\""))
        assertTrue("Custom icon assignments should use stable keys", store.contains("KEY_PREFIX = \"custom:\""))
        assertTrue("Custom icon metadata should persist the recolorability choice", store.contains("recolorable"))
    }

    @Test
    fun customIconsAreSelectableInTheActionAndWidgetIconPickers() {
        val picker = sourceRoot.resolve("com/opentasker/ui/screens/ActionIconPickerDialog.kt").readText()
        val profileEditor = sourceRoot.resolve("com/opentasker/ui/screens/EditorDialogs.kt").readText()
        val widgetConfig = sourceRoot.resolve("com/opentasker/widget/TaskWidgetConfigActivity.kt").readText()
        val catalog = sourceRoot.resolve("com/opentasker/widget/WidgetIconCatalog.kt").readText()

        assertTrue("The icon picker should include uploaded icons", picker.contains("filteredCustomIcons"))
        assertTrue("Custom picker results should keep their stable keys", picker.contains("option.key"))
        assertTrue("Profile editing should preserve custom keys", profileEditor.contains("WidgetIconCatalog.resolveKey(profile?.icon)"))
        assertTrue("Widget setup should preserve custom keys", widgetConfig.contains("WidgetIconCatalog.resolveKey(initialStyle.icon)"))
        assertTrue("The catalog should recognize saved custom icon keys", catalog.contains("CustomWidgetIconStore.idFromKey(value)"))
    }

    @Test
    fun profileCardsAndHomeScreenWidgetsRenderUploadedImages() {
        val profileCards = sourceRoot.resolve("com/opentasker/ui/screens/ActiveAutomationLists.kt").readText()
        val widgetProvider = sourceRoot.resolve("com/opentasker/widget/TaskWidgetProvider.kt").readText()
        val image = sourceRoot.resolve("com/opentasker/ui/icons/WidgetIconImage.kt").readText()

        assertTrue("Profile cards should render saved custom icon images", profileCards.contains("WidgetIconImage("))
        assertTrue("Compose should tint only icons marked recolorable", image.contains("customIcon.recolorable"))
        assertTrue("The home-screen widget should use bitmap custom icons", widgetProvider.contains("CustomWidgetIconStore.renderForWidget"))
        assertTrue("Non-recolorable custom icons should retain their original colors", widgetProvider.contains("customIconBitmap"))
    }
}
