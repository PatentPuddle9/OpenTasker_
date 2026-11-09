package com.opentasker.core.scenes

import com.opentasker.core.model.Scene
import com.opentasker.core.model.SceneElement
import kotlin.math.roundToInt

enum class SceneSelectionAlignment {
    LEFT,
    CENTER_HORIZONTAL,
    RIGHT,
    TOP,
    CENTER_VERTICAL,
    BOTTOM,
    CENTER_ON_CANVAS,
    DISTRIBUTE_HORIZONTAL,
    DISTRIBUTE_VERTICAL,
}

/** Pure scene mutations shared by editor gestures and behavior tests. */
object SceneEditorMutations {
    fun replaceElement(scene: Scene, index: Int, element: SceneElement): Scene {
        if (index !in scene.elements.indices) return scene
        return scene.copy(
            elements = scene.elements.mapIndexed { candidateIndex, existing ->
                if (candidateIndex == index) element else existing
            },
        )
    }

    /** Moves the selection as one rigid group, clamping one shared delta at the scene edges. */
    fun moveSelected(
        scene: Scene,
        selectedIndices: Set<Int>,
        requestedDeltaX: Int,
        requestedDeltaY: Int,
    ): Scene {
        val selected = scene.elements.filterIndexed { index, _ -> index in selectedIndices }
        if (selected.isEmpty()) return scene

        val minDeltaX = selected.maxOf { -it.xDp }
        val maxDeltaX = selected.minOf { scene.widthDp - it.xDp - it.widthDp }
        val minDeltaY = selected.maxOf { -it.yDp }
        val maxDeltaY = selected.minOf { scene.heightDp - it.yDp - it.heightDp }
        val deltaX = requestedDeltaX.coerceIn(minDeltaX.coerceAtMost(maxDeltaX), maxDeltaX)
        val deltaY = requestedDeltaY.coerceIn(minDeltaY.coerceAtMost(maxDeltaY), maxDeltaY)

        return scene.copy(
            elements = scene.elements.mapIndexed { index, element ->
                if (index in selectedIndices) {
                    element.copy(xDp = element.xDp + deltaX, yDp = element.yDp + deltaY)
                } else {
                    element
                }
            },
        )
    }

    /**
     * Aligns a multi-selection to common edges/centres, centres the group on the canvas, or
     * distributes the objects with equal gaps while preserving their sizes and relative order.
     */
    fun alignSelected(
        scene: Scene,
        selectedIndices: Set<Int>,
        alignment: SceneSelectionAlignment,
    ): Scene {
        val selected = scene.elements.withIndex()
            .filter { it.index in selectedIndices }
        if (selected.size < 2 && alignment != SceneSelectionAlignment.CENTER_ON_CANVAS) return scene
        if (selected.isEmpty()) return scene

        val left = selected.minOf { it.value.xDp }
        val top = selected.minOf { it.value.yDp }
        val right = selected.maxOf { it.value.xDp + it.value.widthDp }
        val bottom = selected.maxOf { it.value.yDp + it.value.heightDp }
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        val updates = mutableMapOf<Int, Pair<Int, Int>>()

        when (alignment) {
            SceneSelectionAlignment.LEFT -> selected.forEach {
                updates[it.index] = left to it.value.yDp
            }
            SceneSelectionAlignment.CENTER_HORIZONTAL -> selected.forEach {
                updates[it.index] = (centerX - it.value.widthDp / 2) to it.value.yDp
            }
            SceneSelectionAlignment.RIGHT -> selected.forEach {
                updates[it.index] = (right - it.value.widthDp) to it.value.yDp
            }
            SceneSelectionAlignment.TOP -> selected.forEach {
                updates[it.index] = it.value.xDp to top
            }
            SceneSelectionAlignment.CENTER_VERTICAL -> selected.forEach {
                updates[it.index] = it.value.xDp to (centerY - it.value.heightDp / 2)
            }
            SceneSelectionAlignment.BOTTOM -> selected.forEach {
                updates[it.index] = it.value.xDp to (bottom - it.value.heightDp)
            }
            SceneSelectionAlignment.CENTER_ON_CANVAS -> {
                val deltaX = scene.widthDp / 2 - centerX
                val deltaY = scene.heightDp / 2 - centerY
                val minDeltaX = selected.maxOf { -it.value.xDp }
                val maxDeltaX = selected.minOf { scene.widthDp - it.value.xDp - it.value.widthDp }
                val minDeltaY = selected.maxOf { -it.value.yDp }
                val maxDeltaY = selected.minOf { scene.heightDp - it.value.yDp - it.value.heightDp }
                val dx = deltaX.coerceIn(minDeltaX.coerceAtMost(maxDeltaX), maxDeltaX)
                val dy = deltaY.coerceIn(minDeltaY.coerceAtMost(maxDeltaY), maxDeltaY)
                selected.forEach { updates[it.index] = (it.value.xDp + dx) to (it.value.yDp + dy) }
            }
            SceneSelectionAlignment.DISTRIBUTE_HORIZONTAL -> {
                val ordered = selected.sortedBy { it.value.xDp }
                val totalWidth = ordered.sumOf { it.value.widthDp }
                val gap = ((right - left - totalWidth).toFloat() / (ordered.size - 1))
                    .coerceAtLeast(0f)
                var cursor = left.toFloat()
                ordered.forEach { item ->
                    updates[item.index] = cursor.roundToInt() to item.value.yDp
                    cursor += item.value.widthDp + gap
                }
            }
            SceneSelectionAlignment.DISTRIBUTE_VERTICAL -> {
                val ordered = selected.sortedBy { it.value.yDp }
                val totalHeight = ordered.sumOf { it.value.heightDp }
                val gap = ((bottom - top - totalHeight).toFloat() / (ordered.size - 1))
                    .coerceAtLeast(0f)
                var cursor = top.toFloat()
                ordered.forEach { item ->
                    updates[item.index] = item.value.xDp to cursor.roundToInt()
                    cursor += item.value.heightDp + gap
                }
            }
        }

        return scene.copy(
            elements = scene.elements.mapIndexed { index, element ->
                val (x, y) = updates[index] ?: return@mapIndexed element
                element.copy(
                    xDp = x.coerceIn(0, (scene.widthDp - element.widthDp).coerceAtLeast(0)),
                    yDp = y.coerceIn(0, (scene.heightDp - element.heightDp).coerceAtLeast(0)),
                )
            },
        )
    }

    fun resizeElementFromCanvasDelta(
        scene: Scene,
        element: SceneElement,
        deltaCanvasX: Float,
        deltaCanvasY: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        minimumSizeDp: Int,
    ): SceneElement {
        val safeCanvasWidth = canvasWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeCanvasHeight = canvasHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
        val widthDelta = (deltaCanvasX * scene.widthDp.coerceAtLeast(1) / safeCanvasWidth).roundToInt()
        val heightDelta = (deltaCanvasY * scene.heightDp.coerceAtLeast(1) / safeCanvasHeight).roundToInt()
        val maximumWidth = (scene.widthDp - element.xDp).coerceAtLeast(1)
        val maximumHeight = (scene.heightDp - element.yDp).coerceAtLeast(1)
        val minimumWidth = minimumSizeDp.coerceIn(1, maximumWidth)
        val minimumHeight = minimumSizeDp.coerceIn(1, maximumHeight)
        return element.copy(
            widthDp = (element.widthDp + widthDelta).coerceIn(minimumWidth, maximumWidth),
            heightDp = (element.heightDp + heightDelta).coerceIn(minimumHeight, maximumHeight),
        )
    }
}
