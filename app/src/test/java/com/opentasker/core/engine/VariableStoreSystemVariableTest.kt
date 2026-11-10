package com.opentasker.core.engine

import com.opentasker.core.expressions.TemplateExpressionEngine
import com.opentasker.core.expressions.TemplateValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VariableStoreSystemVariableTest {
    @Test
    fun systemValuesResolveThroughTheirOwnTemplateNamespace() {
        val variables = VariableStore()
        variables.seedSystemVariables(
            mapOf(
                "battery_percent" to "72",
                "brightness_percent" to "40",
                "volume_percent" to "25",
            ),
        )

        val result = TemplateExpressionEngine().expand(
            "{{ system.battery_percent }}|{{ system.brightness_percent }}|{{ system.volume_percent }}",
            variables.toTemplateScope(),
        )

        assertEquals("72|40|25", result.value)
        assertEquals(TemplateValueSource.SYSTEM, result.traces.first().source)
    }

    @Test
    fun systemValuesDoNotOverwriteTaskOrPersistedGlobalVariables() {
        val variables = VariableStore()
        variables.set("brightness", "111")
        variables.seedGlobals(mapOf("Volume" to "4"))
        variables.seedSystemVariables(mapOf("brightness" to "200", "volume" to "9"))

        val scope = variables.toTemplateScope()
        assertEquals("111", scope.task["brightness"])
        assertEquals("4", scope.global["Volume"])
        assertEquals("200", scope.system["brightness"])
        assertEquals("9", scope.system["volume"])
        assertFalse(variables.globalSnapshot().containsKey("brightness"))
        assertFalse(variables.globalSnapshot().containsKey("volume"))
    }
}
