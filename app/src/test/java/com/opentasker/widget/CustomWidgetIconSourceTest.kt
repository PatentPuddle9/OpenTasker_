package com.opentasker.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupIconSettingsOnlyTest {
    private val sourceRoot: Path = listOf(
        Path.of("src/main/java"),
        Path.of("app/src/main/java"),
    ).first(Files::exists)

    @Test
    fun popupImagesCanOnlyBeUploadedAndManagedInSettings() {
        val settings = sourceRoot.resolve("com/opentasker/ui/screens/CustomIconsSettingsCard.kt").readText()
        val editor = sourceRoot.resolve("com/opentasker/ui/screens/ActionEditorDialogs.kt").readText()
        val store = sourceRoot.resolve("com/opentasker/widget/CustomWidgetIconStore.kt").readText()

        assertTrue(settings.contains("OpenDocument"))
        assertTrue(settings.contains("arrayOf(\"image/*\")"))
        assertTrue(settings.contains("Custom icons"))
        assertTrue(settings.contains("Icon name"))
        assertTrue(settings.contains("addRecolorable"))
        assertTrue(settings.contains("Recolored preview"))
        assertTrue(store.contains("filesDir, \"custom_icons\""))
        assertTrue(store.contains("fun loadBitmapForPopup"))
        assertTrue(editor.contains("Add custom icons in Settings > General > Custom icons."))
        assertTrue(editor.contains("popupIcons.forEach"))
    }

    @Test
    fun uploadedIconsAppearInSharedCategorizedPickerAndRenderAcrossTheApp() {
        val picker = sourceRoot.resolve("com/opentasker/ui/screens/ActionIconPickerDialog.kt").readText()
        val image = sourceRoot.resolve("com/opentasker/ui/icons/WidgetIconImage.kt").readText()
        val provider = sourceRoot.resolve("com/opentasker/widget/TaskWidgetProvider.kt").readText()
        val catalog = sourceRoot.resolve("com/opentasker/widget/WidgetIconCatalog.kt").readText()

        assertTrue(picker.contains("CustomWidgetIconStore.observe"))
        assertTrue(picker.contains("category = \"Custom\""))
        assertTrue(picker.contains("FilterChip"))
        assertTrue(picker.contains("WidgetIconCatalog.categoryFor"))
        assertTrue(image.contains("CustomWidgetIconStore.idFromKey"))
        assertTrue(image.contains("CustomWidgetIconStore.loadBitmap"))
        assertTrue(provider.contains("CustomWidgetIconStore.loadBitmapForWidget"))
        assertTrue(catalog.contains("fun categoryFor(option: WidgetIconOption)"))
        assertTrue(catalog.contains("CustomWidgetIconStore.idFromKey"))
        assertTrue(catalog.contains("fun labelFor(value: String?)"))
    }

    @Test
    fun popupDialogAttachesSelectedImageToCreatedDialog() {
        val service = sourceRoot.resolve("com/opentasker/core/actions/PopupDialogService.kt").readText()
        assertTrue(service.contains("CustomWidgetIconStore.loadBitmapForPopup"))
        assertTrue(service.contains("popupIcon?.let { builder.setIcon(it) }"))
        assertTrue(service.contains("popupIcon?.let { setIcon(it) }"))
    }
}
