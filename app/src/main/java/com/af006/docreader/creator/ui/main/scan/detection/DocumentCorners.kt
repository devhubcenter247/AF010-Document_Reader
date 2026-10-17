package com.af010.creator.docreader.ui.main.scan.detection

import kotlin.math.abs
import kotlin.math.hypot
import org.opencv.core.Point

data class DocumentCorners(
    val points: List<Point>,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    fun toUprightFractions(rotationDegrees: Int): List<Point> = points.map { point ->
        val u = point.x / imageWidth
        val v = point.y / imageHeight
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Point(1.0 - v, u)
            180 -> Point(1.0 - u, 1.0 - v)
            270 -> Point(v, 1.0 - u)
            else -> Point(u, v)
        }
    }

    fun expandedBy(ratio: Double = MARGIN_RATIO): DocumentCorners {
        val sides = points.indices.map { index -> points[index] to points[(index + 1) % 4] }
        val margin = sides.sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) } / 4.0 * ratio
        val centerX = points.sumOf { it.x } / 4.0
        val centerY = points.sumOf { it.y } / 4.0

        val shifted = sides.map { (a, b) ->
            val length = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1e-6)
            val dx = (b.x - a.x) / length
            val dy = (b.y - a.y) / length
            var nx = -dy
            var ny = dx
            if (nx * ((a.x + b.x) / 2.0 - centerX) + ny * ((a.y + b.y) / 2.0 - centerY) < 0.0) {
                nx = -nx
                ny = -ny
            }
            doubleArrayOf(a.x + nx * margin, a.y + ny * margin, dx, dy)
        }
        val expanded = points.indices.map { index ->
            val first = shifted[(index + 3) % 4]
            val second = shifted[index]
            val determinant = first[2] * second[3] - first[3] * second[2]
            if (abs(determinant) < 1e-6) return this
            val t = ((second[0] - first[0]) * second[3] - (second[1] - first[1]) * second[2]) / determinant
            Point(
                (first[0] + first[2] * t).coerceIn(0.0, imageWidth - 1.0),
                (first[1] + first[3] * t).coerceIn(0.0, imageHeight - 1.0),
            )
        }
        return copy(points = expanded)
    }

    companion object {
        const val MARGIN_RATIO = 0.03
    }
}
