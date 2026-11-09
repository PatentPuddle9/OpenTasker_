package com.opentasker.core.scenes

import com.opentasker.core.model.Scene
import kotlin.math.abs
import kotlin.math.roundToInt

data class AlignmentGuide(
    val orientation: GuideOrientation,
    val position: Float,
    val snappedPosition: Int,
    val kind: AlignmentGuideKind = AlignmentGuideKind.ALIGNMENT,
    /** Segment start/end in the axis described by [orientation], used for equal-gap rulers. */
    val start: Float? = null,
    val end: Float? = null,
    val label: String? = null,
)

enum class GuideOrientation { HORIZONTAL, VERTICAL }
enum class AlignmentGuideKind { ALIGNMENT, SPACING }

data class CanvasElementBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

object SceneAlignmentGuides {
    const val SNAP_THRESHOLD_DP = 6

    fun findGuides(
        scene: Scene,
        movingIndex: Int,
        candidateX: Int,
        candidateY: Int,
        candidateW: Int,
        candidateH: Int,
        snapThresholdDp: Int = SNAP_THRESHOLD_DP,
    ): AlignmentResult = findGuides(
        canvasWidthDp = scene.widthDp,
        canvasHeightDp = scene.heightDp,
        elements = scene.elements.map {
            CanvasElementBounds(it.xDp, it.yDp, it.widthDp, it.heightDp)
        },
        movingIndex = movingIndex,
        candidateX = candidateX,
        candidateY = candidateY,
        candidateW = candidateW,
        candidateH = candidateH,
        snapThresholdDp = snapThresholdDp,
    )

    /**
     * Shared Canva-style snapping for scenes and widget canvases. It matches left/center/right
     * and top/middle/bottom anchors, snaps to the canvas center, then looks for equal gaps between
     * neighbouring objects. Spacing snaps never override a closer alignment snap.
     */
    fun findGuides(
        canvasWidthDp: Int,
        canvasHeightDp: Int,
        elements: List<CanvasElementBounds>,
        movingIndex: Int,
        candidateX: Int,
        candidateY: Int,
        candidateW: Int,
        candidateH: Int,
        snapThresholdDp: Int = SNAP_THRESHOLD_DP,
    ): AlignmentResult {
        val safeCanvasWidth = canvasWidthDp.coerceAtLeast(1)
        val safeCanvasHeight = canvasHeightDp.coerceAtLeast(1)
        val width = candidateW.coerceAtLeast(1)
        val height = candidateH.coerceAtLeast(1)
        val peers = elements.filterIndexed { index, _ -> index != movingIndex }

        val verticalAnchors = buildList {
            add(0)
            add(safeCanvasWidth / 2)
            add(safeCanvasWidth)
            peers.forEach { element ->
                add(element.x)
                add(element.x + element.width / 2)
                add(element.x + element.width)
            }
        }
        val horizontalAnchors = buildList {
            add(0)
            add(safeCanvasHeight / 2)
            add(safeCanvasHeight)
            peers.forEach { element ->
                add(element.y)
                add(element.y + element.height / 2)
                add(element.y + element.height)
            }
        }

        val xSnap = nearestSnap(
            candidate = candidateX,
            size = width,
            anchors = verticalAnchors,
            snapThresholdDp = snapThresholdDp,
        )
        val ySnap = nearestSnap(
            candidate = candidateY,
            size = height,
            anchors = horizontalAnchors,
            snapThresholdDp = snapThresholdDp,
        )
        var snappedX = xSnap?.position ?: candidateX
        var snappedY = ySnap?.position ?: candidateY
        val guides = mutableListOf<AlignmentGuide>()

        xSnap?.let {
            guides += AlignmentGuide(GuideOrientation.VERTICAL, it.anchor.toFloat(), it.anchor)
        }
        ySnap?.let {
            guides += AlignmentGuide(GuideOrientation.HORIZONTAL, it.anchor.toFloat(), it.anchor)
        }

        val horizontalSpacing = findHorizontalSpacing(
            peers = peers,
            candidateX = candidateX,
            candidateY = candidateY,
            width = width,
            height = height,
            snapThresholdDp = snapThresholdDp,
        )
        if (horizontalSpacing != null &&
            (xSnap == null || horizontalSpacing.distance < xSnap.distance)
        ) {
            snappedX = horizontalSpacing.position
            guides.removeAll { it.orientation == GuideOrientation.VERTICAL }
            guides += horizontalSpacing.guides
        }

        val verticalSpacing = findVerticalSpacing(
            peers = peers,
            candidateX = candidateX,
            candidateY = candidateY,
            width = width,
            height = height,
            snapThresholdDp = snapThresholdDp,
        )
        if (verticalSpacing != null &&
            (ySnap == null || verticalSpacing.distance < ySnap.distance)
        ) {
            snappedY = verticalSpacing.position
            guides.removeAll { it.orientation == GuideOrientation.HORIZONTAL }
            guides += verticalSpacing.guides
        }

        val maxX = (safeCanvasWidth - width).coerceAtLeast(0)
        val maxY = (safeCanvasHeight - height).coerceAtLeast(0)
        snappedX = snappedX.coerceIn(0, maxX)
        snappedY = snappedY.coerceIn(0, maxY)
        return AlignmentResult(snappedX, snappedY, guides.distinct())
    }

    private data class AxisSnap(
        val position: Int,
        val anchor: Int,
        val distance: Int,
    )

    private data class SpacingSnap(
        val position: Int,
        val distance: Int,
        val guides: List<AlignmentGuide>,
    )

    private fun nearestSnap(
        candidate: Int,
        size: Int,
        anchors: List<Int>,
        snapThresholdDp: Int,
    ): AxisSnap? {
        var best: AxisSnap? = null
        val movingAnchors = listOf(0, size / 2, size)
        anchors.distinct().forEach { anchor ->
            movingAnchors.forEachIndexed { index, offset ->
                val distance = abs(candidate + offset - anchor)
                if (distance <= snapThresholdDp && (best == null || distance < best!!.distance)) {
                    best = AxisSnap(
                        position = anchor - offset,
                        anchor = anchor,
                        distance = distance,
                    )
                }
            }
        }
        return best
    }

    private fun findHorizontalSpacing(
        peers: List<CanvasElementBounds>,
        candidateX: Int,
        candidateY: Int,
        width: Int,
        height: Int,
        snapThresholdDp: Int,
    ): SpacingSnap? {
        val sameRow = peers.filter { peer ->
            val overlap = minOf(candidateY + height, peer.y + peer.height) - maxOf(candidateY, peer.y)
            overlap >= minOf(height, peer.height).coerceAtLeast(1) / 3
        }
        var best: SpacingSnap? = null
        for (left in sameRow) {
            val leftEdge = left.x + left.width
            for (right in sameRow) {
                val rightEdge = right.x
                if (rightEdge <= leftEdge) continue
                val ideal = ((leftEdge + rightEdge - width) / 2f).roundToInt()
                val firstGap = ideal - leftEdge
                val secondGap = rightEdge - (ideal + width)
                if (firstGap < 0 || secondGap < 0) continue
                val distance = abs(candidateX - ideal)
                if (distance <= snapThresholdDp && (best == null || distance < best!!.distance)) {
                    best = SpacingSnap(
                        position = ideal,
                        distance = distance,
                        guides = listOf(
                            AlignmentGuide(
                                orientation = GuideOrientation.HORIZONTAL,
                                position = (candidateY + height / 2f),
                                snappedPosition = ideal,
                                kind = AlignmentGuideKind.SPACING,
                                start = leftEdge.toFloat(),
                                end = ideal.toFloat(),
                                label = firstGap.toString(),
                            ),
                            AlignmentGuide(
                                orientation = GuideOrientation.HORIZONTAL,
                                position = (candidateY + height / 2f),
                                snappedPosition = ideal,
                                kind = AlignmentGuideKind.SPACING,
                                start = (ideal + width).toFloat(),
                                end = rightEdge.toFloat(),
                                label = secondGap.toString(),
                            ),
                        ),
                    )
                }
            }
        }
        return best
    }

    private fun findVerticalSpacing(
        peers: List<CanvasElementBounds>,
        candidateX: Int,
        candidateY: Int,
        width: Int,
        height: Int,
        snapThresholdDp: Int,
    ): SpacingSnap? {
        val sameColumn = peers.filter { peer ->
            val overlap = minOf(candidateX + width, peer.x + peer.width) - maxOf(candidateX, peer.x)
            overlap >= minOf(width, peer.width).coerceAtLeast(1) / 3
        }
        var best: SpacingSnap? = null
        for (top in sameColumn) {
            val topEdge = top.y + top.height
            for (bottom in sameColumn) {
                val bottomEdge = bottom.y
                if (bottomEdge <= topEdge) continue
                val ideal = ((topEdge + bottomEdge - height) / 2f).roundToInt()
                val firstGap = ideal - topEdge
                val secondGap = bottomEdge - (ideal + height)
                if (firstGap < 0 || secondGap < 0) continue
                val distance = abs(candidateY - ideal)
                if (distance <= snapThresholdDp && (best == null || distance < best!!.distance)) {
                    best = SpacingSnap(
                        position = ideal,
                        distance = distance,
                        guides = listOf(
                            AlignmentGuide(
                                orientation = GuideOrientation.VERTICAL,
                                position = (candidateX + width / 2f),
                                snappedPosition = ideal,
                                kind = AlignmentGuideKind.SPACING,
                                start = topEdge.toFloat(),
                                end = ideal.toFloat(),
                                label = firstGap.toString(),
                            ),
                            AlignmentGuide(
                                orientation = GuideOrientation.VERTICAL,
                                position = (candidateX + width / 2f),
                                snappedPosition = ideal,
                                kind = AlignmentGuideKind.SPACING,
                                start = (ideal + height).toFloat(),
                                end = bottomEdge.toFloat(),
                                label = secondGap.toString(),
                            ),
                        ),
                    )
                }
            }
        }
        return best
    }
}

data class AlignmentResult(
    val snappedX: Int,
    val snappedY: Int,
    val guides: List<AlignmentGuide>,
)
