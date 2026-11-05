package com.opentasker.widget

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedIconLibrarySourceTest {
    private val sourceRoot: Path = listOf(
        Path.of("src/main/java"),
        Path.of("app/src/main/java"),
    ).first(Files::exists)

    @Test
    fun customIconsAreAddedThroughAnInAppSquareCrop() {
        val settings = sourceRoot.resolve("com/opentasker/ui/screens/CustomIconsSettingsCard.kt").readText()
        val crop = sourceRoot.resolve("com/opentasker/ui/screens/SquareImageCropDialog.kt").readText()
        val store = sourceRoot.resolve("com/opentasker/widget/CustomWidgetIconStore.kt").readText()
        assertTrue(settings.contains("CustomIconsSettingsCard"))
        assertTrue(settings.contains("SquareImageCropDialog"))
        assertTrue(settings.contains("Use square crop"))
        assertTrue(settings.contains("CustomWidgetIconStore.add(context, image, addName, addRecolorable)"))
        assertTrue(crop.contains("detectTransformGestures"))
        assertTrue(crop.contains("cropSquareIcon"))
        assertTrue(store.contains("croppedImage.width == croppedImage.height"))
    }

    @Test
    fun builtInAndCustomIconsShareTheSamePickerAndRenderers() {
        val picker = sourceRoot.resolve("com/opentasker/ui/screens/ActionIconPickerDialog.kt").readText()
        val image = sourceRoot.resolve("com/opentasker/ui/icons/WidgetIconImage.kt").readText()
        val provider = sourceRoot.resolve("com/opentasker/widget/TaskWidgetProvider.kt").readText()
        val catalog = sourceRoot.resolve("com/opentasker/widget/WidgetIconCatalog.kt").readText()
        val profileEditor = sourceRoot.resolve("com/opentasker/ui/screens/EditorDialogs.kt").readText()
        assertTrue(picker.contains("CustomWidgetIconStore.observe"))
        assertTrue(picker.contains("WidgetIconCatalog.all"))
        assertTrue(picker.contains("Search icons"))
        assertTrue(image.contains("CustomWidgetIconStore.observe"))
        assertTrue(provider.contains("CustomWidgetIconStore.loadBitmapForPopup"))
        assertTrue(provider.contains("setImageViewBitmap"))
        assertTrue(catalog.contains("fun resolveKey(value: String?)"))
        assertTrue(catalog.contains("CustomWidgetIconStore.idFromKey"))
        assertTrue(profileEditor.contains("CustomWidgetIconStore.observe"))
        assertFalse(picker.contains("Search all Coolicons"))
    }

    @Test
    fun popupActionsSupportCustomAndBuiltInIcons() {
        val editor = sourceRoot.resolve("com/opentasker/ui/screens/ActionEditorDialogs.kt").readText()
        val service = sourceRoot.resolve("com/opentasker/core/actions/PopupDialogService.kt").readText()
        assertTrue(editor.contains("ActionIconPickerDialog("))
        assertTrue(editor.contains("Custom icons"))
        assertTrue(service.contains("CustomWidgetIconStore.loadBitmapForPopup"))
        assertTrue(service.contains("WidgetIconCatalog.findBuiltIn(value)"))
        assertTrue(service.contains("popupIcon?.let { builder.setIcon(it) }"))
        assertTrue(service.contains("popupIcon?.let { setIcon(it) }"))
    }
}
