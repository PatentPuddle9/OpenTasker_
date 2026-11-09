package com.opentasker.core.scenes

import com.opentasker.core.model.Scene
import com.opentasker.core.model.SceneElement
import com.opentasker.core.model.SceneElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneAlignmentGuidesTest {

    private fun scene(w: Int = 320, h: Int = 240, elements: List<SceneElement> = emptyList()) =
        Scene(id = 1, name = "test", widthDp = w, heightDp = h, elements = elements)

    private fun element(x: Int, y: Int, w: Int = 40, h: Int = 40) =
        SceneElement(id = 1, type = SceneElementType.BUTTON, xDp = x, yDp = y, widthDp = w, heightDp = h)

    @Test
    fun snapsToCanvasLeftEdge() {
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(),
            movingIndex = 0,
            candidateX = 3, candidateY = 50,
            candidateW = 40, candidateH = 40,
        )
        assertEquals(0, result.snappedX)
        assertTrue(result.guides.any { it.orientation == GuideOrientation.VERTICAL && it.snappedPosition == 0 })
    }

    @Test
    fun snapsToCanvasCenterVertical() {
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(),
            movingIndex = 0,
            candidateX = 138, candidateY = 50,
            candidateW = 40, candidateH = 40,
        )
        assertEquals(140, result.snappedX)
    }

    @Test
    fun snapsToOtherElementEdge() {
        val anchor = element(x = 100, y = 50, w = 60, h = 30)
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(elements = listOf(anchor, element(0, 0))),
            movingIndex = 1,
            candidateX = 157, candidateY = 10,
            candidateW = 40, candidateH = 40,
        )
        assertEquals(160, result.snappedX)
        assertTrue(result.guides.any { it.orientation == GuideOrientation.VERTICAL })
    }

    @Test
    fun matchesOtherObjectCentersAndEdges() {
        val anchor = element(x = 100, y = 90, w = 80, h = 40)
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(elements = listOf(anchor, element(0, 0))),
            movingIndex = 1,
            candidateX = 119,
            candidateY = 93,
            candidateW = 40,
            candidateH = 40,
        )
        assertEquals(120, result.snappedX)
        assertEquals(90, result.snappedY)
        assertTrue(result.guides.any { it.orientation == GuideOrientation.VERTICAL && it.snappedPosition == 140 })
        assertTrue(result.guides.any { it.orientation == GuideOrientation.HORIZONTAL && it.snappedPosition == 90 })
    }

    @Test
    fun snapsToEqualHorizontalSpacingBetweenTwoObjects() {
        val peers = listOf(
            CanvasElementBounds(x = 40, y = 100, width = 40, height = 40),
            CanvasElementBounds(x = 220, y = 100, width = 40, height = 40),
        )
        val result = SceneAlignmentGuides.findGuides(
            canvasWidthDp = 500,
            canvasHeightDp = 300,
            elements = peers + CanvasElementBounds(0, 0, 40, 40),
            movingIndex = 2,
            candidateX = 133,
            candidateY = 100,
            candidateW = 40,
            candidateH = 40,
        )
        assertEquals(130, result.snappedX)
        assertTrue(result.guides.count { it.kind == AlignmentGuideKind.SPACING } >= 2)
    }

    @Test
    fun snapsToEqualVerticalSpacingBetweenTwoObjects() {
        val peers = listOf(
            CanvasElementBounds(x = 120, y = 20, width = 40, height = 30),
            CanvasElementBounds(x = 120, y = 170, width = 40, height = 30),
        )
        val result = SceneAlignmentGuides.findGuides(
            canvasWidthDp = 320,
            canvasHeightDp = 260,
            elements = peers + CanvasElementBounds(0, 0, 40, 30),
            movingIndex = 2,
            candidateX = 120,
            candidateY = 92,
            candidateW = 40,
            candidateH = 30,
        )
        assertEquals(95, result.snappedY)
        assertTrue(result.guides.count { it.kind == AlignmentGuideKind.SPACING } >= 2)
    }

    @Test
    fun noSnapWhenFarFromAnchors() {
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(),
            movingIndex = 0,
            candidateX = 50, candidateY = 50,
            candidateW = 40, candidateH = 40,
        )
        assertEquals(50, result.snappedX)
        assertEquals(50, result.snappedY)
        assertTrue(result.guides.isEmpty())
    }

    @Test
    fun clampsToBoundsAfterSnap() {
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(w = 100, h = 100),
            movingIndex = 0,
            candidateX = 98, candidateY = 98,
            candidateW = 40, candidateH = 40,
        )
        assertTrue(result.snappedX <= 60)
        assertTrue(result.snappedY <= 60)
    }

    @Test
    fun horizontalSnapToCanvasTop() {
        val result = SceneAlignmentGuides.findGuides(
            scene = scene(),
            movingIndex = 0,
            candidateX = 50, candidateY = 4,
            candidateW = 40, candidateH = 40,
        )
        assertEquals(0, result.snappedY)
        assertTrue(result.guides.any { it.orientation == GuideOrientation.HORIZONTAL })
    }
}
