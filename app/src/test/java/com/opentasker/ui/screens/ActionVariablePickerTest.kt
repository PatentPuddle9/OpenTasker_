package com.opentasker.ui.screens

import com.opentasker.ProductionSources
import com.opentasker.core.actions.ActionValueType
import com.opentasker.core.actions.VariableChipOption
import com.opentasker.core.actions.VariableChipScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionVariablePickerTest {
    private val options = listOf(
        VariableChipOption(
            name = "brightness_percent",
            type = ActionValueType.NUMBER,
            scope = VariableChipScope.SYSTEM,
            reference = "{{ system.brightness_percent }}",
            description = "Screen brightness as a percentage.",
        ),
        VariableChipOption(
            name = "battery_percent",
            type = ActionValueType.NUMBER,
            scope = VariableChipScope.SYSTEM,
            reference = "{{ system.battery_percent }}",
            description = "Current battery charge percentage.",
        ),
        VariableChipOption(
            name = "volume_percent",
            type = ActionValueType.NUMBER,
            scope = VariableChipScope.SYSTEM,
            reference = "{{ system.volume_percent }}",
            description = "Media volume as a percentage.",
        ),
        VariableChipOption(
            name = "wifi_ssid",
            type = ActionValueType.TEXT,
            scope = VariableChipScope.SYSTEM,
            reference = "{{ system.wifi_ssid }}",
            description = "Connected Wi-Fi network name, when available.",
        ),
        VariableChipOption(
            name = "share_text",
            type = ActionValueType.TEXT,
            scope = VariableChipScope.EVENT,
            reference = "{{ event.share_text }}",
        ),
        VariableChipOption(
            name = "SavedCount",
            type = ActionValueType.ANY,
            scope = VariableChipScope.GLOBAL,
            reference = "{{ global.SavedCount }}",
        ),
        VariableChipOption(
            name = "FLOW_ERROR_MESSAGE",
            type = ActionValueType.TEXT,
            scope = VariableChipScope.FLOW_ERROR,
            reference = "{{ global.FLOW_ERROR_MESSAGE }}",
            description = "Failure message from the action.",
        ),
    )

    @Test
    fun categoriesGroupSystemVariablesByDeviceArea() {
        assertEquals(
            listOf("battery_percent"),
            filterVariablePickerOptions(options, query = "", category = "Battery").map { it.name },
        )
        assertEquals(
            listOf("brightness_percent"),
            filterVariablePickerOptions(options, query = "", category = "Display").map { it.name },
        )
        assertEquals(
            listOf("volume_percent"),
            filterVariablePickerOptions(options, query = "", category = "Audio").map { it.name },
        )
        assertEquals(
            listOf("wifi_ssid"),
            filterVariablePickerOptions(options, query = "", category = "Connectivity").map { it.name },
        )
        assertEquals(
            listOf("share_text"),
            filterVariablePickerOptions(options, query = "", category = "Trigger data").map { it.name },
        )
        assertEquals(
            listOf("FLOW_ERROR_MESSAGE"),
            filterVariablePickerOptions(options, query = "", category = "Flow errors").map { it.name },
        )
        assertEquals(7, filterVariablePickerOptions(options, query = "").size)
    }

    @Test
    fun searchMatchesNamesReferencesDescriptionsTypesAndCategories() {
        assertEquals(
            listOf("battery_percent"),
            filterVariablePickerOptions(options, query = "battery").map { it.name },
        )
        assertEquals(
            listOf("brightness_percent"),
            filterVariablePickerOptions(options, query = "system.brightness").map { it.name },
        )
        assertEquals(
            listOf("battery_percent", "brightness_percent"),
            filterVariablePickerOptions(options, query = "percentage").map { it.name },
        )
        assertEquals(
            listOf("volume_percent"),
            filterVariablePickerOptions(options, query = "audio").map { it.name },
        )
        assertEquals(
            listOf("wifi_ssid", "share_text").sorted(),
            filterVariablePickerOptions(options, query = "text").map { it.name }.sorted(),
        )
    }

    @Test
    fun variableFieldsUseTheUploadedIconAndDialogInsteadOfAnInlineChipStrip() {
        val source = ProductionSources.read("com/opentasker/ui/screens/ActionEditorDialogs.kt")

        assertTrue(source.contains("trailingIcon = {"))
        assertTrue(source.contains("IconButton("))
        assertTrue(source.contains("painterResource(R.drawable.ic_variable_picker)"))
        assertTrue(source.contains("VariablePickerDialog("))
        assertTrue(source.contains("variableOptions = variableOptions,"))
        assertFalse(source.contains("acceptsVariableType"))
        assertFalse(source.contains("VariableChipPicker("))
        assertFalse(source.contains("private fun VariableChipPicker("))

        val icon = ProductionSources.repoRoot.resolve("app/src/main/res/drawable/ic_variable_picker.xml").toFile()
        assertTrue("the uploaded variable icon must be installed as a vector drawable", icon.exists())
        assertTrue(icon.readText().contains("android:strokeLineCap=\"round\""))
    }
}
