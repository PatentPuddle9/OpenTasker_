package com.opentasker.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemVariableSnapshotTest {
    @Test
    fun batteryPercentageIsNormalizedAgainstTheReportedScale() {
        assertEquals(80, normalizeBatteryPercent(level = 80, scale = 100))
        assertEquals(50, normalizeBatteryPercent(level = 128, scale = 255))
        assertEquals(100, normalizeBatteryPercent(level = 300, scale = 255))
        assertEquals(0, normalizeBatteryPercent(level = 0, scale = 255))
    }

    @Test
    fun unknownBatteryReadingsAreNotPresentedAsRealValues() {
        assertNull(normalizeBatteryPercent(level = -1, scale = 100))
        assertNull(normalizeBatteryPercent(level = 50, scale = 0))
        assertNull(normalizeBatteryPercent(level = 50, scale = -1))
    }
}
