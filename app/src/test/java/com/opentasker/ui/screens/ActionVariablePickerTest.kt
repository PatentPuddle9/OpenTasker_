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
    )

    @Test
    fun categoriesFilterSystemAndTriggerVariablesSeparately() {
        assertEquals(
            listOf("battery_percent", "brightness_percent"),
            filterVariablePickerOptions(options, query = "", category = "System").map { it.name },
        )
        assertEquals(
            listOf("share_text"),
            filterVariablePickerOptions(options, query = "", category = "Trigger data").map { it.name },
        )
        assertEquals(4, filterVariablePickerOptions(options, query = "").size)
    }

    @Test
    fun searchMatchesNamesReferencesAndDescriptions() {
        assertEquals(
            listOf("battery_percent"),
            filterVariablePickerOptions(options, query = "battery").map { it.name },
        )
        assertEquals(
            listOf("brightness_percent"),
            filterVariablePickerOptions(options, query = "system.brightness").map { it.name },
        )
        assertEquals(
            listOf("brightness_percent"),
            filterVariablePickerOptions(options, query = "percentage").map { it.name },
        )
    }

    @Test
    fun variableFieldsUseAnIconAndDialogInsteadOfAnInlineChipStrip() {
        val source = ProductionSources.read("com/opentasker/ui/screens/ActionEditorDialogs.kt")

        assertTrue(source.contains("trailingIcon = {"))
        assertTrue(source.contains("IconButton("))
        assertTrue(source.contains("VariablePickerDialog("))
        assertFalse(source.contains("VariableChipPicker("))
        assertFalse(source.contains("private fun VariableChipPicker("))
    }
}
