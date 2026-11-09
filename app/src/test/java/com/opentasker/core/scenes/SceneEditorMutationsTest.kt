package com.opentasker.core.scenes

import com.opentasker.core.model.Scene
import com.opentasker.core.model.SceneElement
import com.opentasker.core.model.SceneElementType
import com.opentasker.core.storage.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneEditorMutationsTest {
    @Test
    fun selectedElementsMoveInOneSceneMutationAndKeepRelativeSpacing() {
        val first = element(id = 1, x = 10, y = 20, width = 50, height = 40)
        val second = element(id = 2, x = 200, y = 80, width = 80, height = 40)
        val unselected = element(id = 3, x = 100, y = 140, width = 30, height = 30)
        val scene = Scene(1, "Canvas", 300, 200, listOf(first, second, unselected))

        val updated = SceneEditorMutations.moveSelected(scene, setOf(0, 1), requestedDeltaX = 50, requestedDeltaY = -30)

        assertEquals(first.copy(xDp = 30, yDp = 0), updated.elements[0])
        assertEquals(second.copy(xDp = 220, yDp = 60), updated.elements[1])
        assertEquals(unselected, updated.elements[2])
        assertEquals(190, updated.elements[1].xDp - updated.elements[0].xDp)
    }

    @Test
    fun moveSelectedIgnoresStaleOutOfRangeIndices() {
        val first = element(id = 1, x = 10, y = 20, width = 50, height = 40)
        val second = element(id = 2, x = 100, y = 60, width = 30, height = 30)
        val scene = Scene(1, "Canvas", 300, 200, listOf(first, second))

        // Index 5 no longer exists (e.g. an element was deleted); it must be ignored, not crash or
        // shift the wrong element.
        val updated = SceneEditorMutations.moveSelected(scene, setOf(0, 5), requestedDeltaX = 20, requestedDeltaY = 10)

        assertEquals(first.copy(xDp = 30, yDp = 30), updated.elements[0])
        assertEquals(second, updated.elements[1])
    }

    @Test
    fun alignSelectionCentersObjectsOnOneSharedAxis() {
        val first = element(id = 1, x = 10, y = 20, width = 20, height = 30)
        val second = element(id = 2, x = 90, y = 80, width = 40, height = 20)
        val scene = Scene(1, "Align", 300, 200, listOf(first, second))

        val aligned = SceneEditorMutations.alignSelected(
            scene,
            setOf(0, 1),
            SceneSelectionAlignment.CENTER_HORIZONTAL,
        )

        assertEquals(70, aligned.elements[0].xDp + aligned.elements[0].widthDp / 2)
        assertEquals(70, aligned.elements[1].xDp + aligned.elements[1].widthDp / 2)
        assertEquals(first.yDp, aligned.elements[0].yDp)
        assertEquals(second.yDp, aligned.elements[1].yDp)
    }

    @Test
    fun centerSelectionOnCanvasAsOneRigidGroup() {
        val first = element(id = 1, x = 10, y = 20, width = 20, height = 30)
        val second = element(id = 2, x = 90, y = 80, width = 40, height = 20)
        val scene = Scene(1, "Center", 300, 200, listOf(first, second))

        val centered = SceneEditorMutations.alignSelected(
            scene,
            setOf(0, 1),
            SceneSelectionAlignment.CENTER_ON_CANVAS,
        )

        val left = centered.elements.minOf { it.xDp }
        val right = centered.elements.maxOf { it.xDp + it.widthDp }
        val top = centered.elements.minOf { it.yDp }
        val bottom = centered.elements.maxOf { it.yDp + it.heightDp }
        assertEquals(150, (left + right) / 2)
        assertEquals(100, (top + bottom) / 2)
    }

    @Test
    fun distributeSelectedObjectsWithEqualHorizontalGaps() {
        val first = element(id = 1, x = 0, y = 10, width = 20, height = 20)
        val second = element(id = 2, x = 90, y = 30, width = 30, height = 20)
        val third = element(id = 3, x = 180, y = 50, width = 20, height = 20)
        val scene = Scene(1, "Distribute", 300, 120, listOf(first, second, third))

        val distributed = SceneEditorMutations.alignSelected(
            scene,
            setOf(0, 1, 2),
            SceneSelectionAlignment.DISTRIBUTE_HORIZONTAL,
        )

        val gap1 = distributed.elements[1].xDp -
            (distributed.elements[0].xDp + distributed.elements[0].widthDp)
        val gap2 = distributed.elements[2].xDp -
            (distributed.elements[1].xDp + distributed.elements[1].widthDp)
        assertEquals(gap1, gap2)
    }

    @Test
    fun portraitCanvasResizeUsesIndependentAxisScales() {
        val original = element(id = 1, x = 20, y = 40, width = 100, height = 100)
        val scene = Scene(1, "Portrait", 400, 800, listOf(original))

        val resized = SceneEditorMutations.resizeElementFromCanvasDelta(
            scene,
            original,
            deltaCanvasX = 10f,
            deltaCanvasY = 10f,
            canvasWidth = 200f,
            canvasHeight = 200f,
            minimumSizeDp = 8,
        )

        assertEquals(120, resized.widthDp)
        assertEquals(140, resized.heightDp)
    }

    @Test
    fun landscapeCanvasResizeUsesIndependentAxisScales() {
        val original = element(id = 1, x = 20, y = 40, width = 100, height = 100)
        val scene = Scene(1, "Landscape", 800, 400, listOf(original))

        val resized = SceneEditorMutations.resizeElementFromCanvasDelta(
            scene,
            original,
            deltaCanvasX = 10f,
            deltaCanvasY = 10f,
            canvasWidth = 200f,
            canvasHeight = 200f,
            minimumSizeDp = 8,
        )

        assertEquals(140, resized.widthDp)
        assertEquals(120, resized.heightDp)
    }

    @Test
    fun onePreMoveSnapshotRestoresEverySelectedElement() {
        val scene = Scene(
            1,
            "Undo",
            300,
            200,
            listOf(
                element(id = 1, x = 20, y = 30, width = 40, height = 40),
                element(id = 2, x = 100, y = 90, width = 50, height = 40),
            ),
        )
        val snapshotJson = scene.toEntity().elementsJson
        val moved = SceneEditorMutations.moveSelected(scene, setOf(0, 1), 15, 25)

        val restored = moved.toEntity().copy(elementsJson = snapshotJson).toDomain()

        assertEquals(scene.elements, restored.elements)
    }

    private fun element(id: Long, x: Int, y: Int, width: Int, height: Int) = SceneElement(
        id = id,
        type = SceneElementType.BUTTON,
        xDp = x,
        yDp = y,
        widthDp = width,
        heightDp = height,
    )
}
