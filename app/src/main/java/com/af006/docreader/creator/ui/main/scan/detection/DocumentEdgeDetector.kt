package com.af010.creator.docreader.ui.main.scan.detection

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfInt
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class DocumentEdgeDetector {
    private var previous: DocumentCorners? = null

    private val contrastBooster by lazy { Imgproc.createCLAHE(3.0, Size(8.0, 8.0)) }

    fun detect(graySource: Mat): DocumentCorners? {
        if (graySource.empty() || graySource.width() < 2 || graySource.height() < 2) return null
        val sourceWidth = graySource.width()
        val sourceHeight = graySource.height()
        val scale = min(1.0, MAX_ANALYSIS_SIZE / maxOf(sourceWidth, sourceHeight).toDouble())
        val resized = Mat()
        val equalized = Mat()
        val blurred = Mat()
        val edges = Mat()
        val canny = Mat()
        val threshold = Mat()
        val edgeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))

        return try {
            if (scale < 1.0) Imgproc.resize(graySource, resized, Size(sourceWidth * scale, sourceHeight * scale))
            else graySource.copyTo(resized)
            contrastBooster.apply(resized, equalized)
            Imgproc.GaussianBlur(equalized, blurred, Size(5.0, 5.0), 0.0)

            Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)
            Imgproc.dilate(edges, canny, edgeKernel)
            Imgproc.morphologyEx(canny, canny, Imgproc.MORPH_CLOSE, closeKernel)
            val candidates = findCandidates(canny, sourceWidth, sourceHeight, scale)

            Imgproc.adaptiveThreshold(
                blurred, threshold, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 21, 5.0,
            )
            Imgproc.morphologyEx(threshold, threshold, Imgproc.MORPH_CLOSE, closeKernel)
            (candidates + findCandidates(threshold, sourceWidth, sourceHeight, scale))
                .maxByOrNull { score(it, sourceWidth, sourceHeight) }
                ?.let { refine(it, edges, scale) }
                ?.also { previous = it }
        } finally {
            resized.release()
            equalized.release()
            blurred.release()
            edges.release()
            canny.release()
            threshold.release()
            edgeKernel.release()
            closeKernel.release()
        }
    }

    private fun findCandidates(mask: Mat, sourceWidth: Int, sourceHeight: Int, scale: Double): List<DocumentCorners> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            contours.mapNotNull { contour ->
                if (Imgproc.contourArea(contour) < mask.width() * mask.height() * MINIMUM_AREA_RATIO) return@mapNotNull null
                    val source = MatOfPoint2f(*contour.toArray())
                    val approximation = MatOfPoint2f()
                    var hullPoints: MatOfPoint? = null
                    var hullIndices: MatOfInt? = null
                    try {
                    Imgproc.approxPolyDP(source, approximation, Imgproc.arcLength(source, true) * APPROXIMATION_RATIO, true)
                    val candidatePoints = if (approximation.total() == 4L) {
                        approximation.toArray()
                    } else {
                        hullIndices = MatOfInt()
                        Imgproc.convexHull(contour, hullIndices)
                        val contourPoints = contour.toArray()
                        val hull = hullIndices!!.toArray().toList().mapNotNull { index -> contourPoints.getOrNull(index) }
                        if (hull.size < 4) return@mapNotNull null
                        val hullPointsValue = MatOfPoint(*hull.toTypedArray())
                        hullPoints = hullPointsValue
                        val hull2f = MatOfPoint2f(*hullPointsValue.toArray())
                        try {
                            val hullApprox = MatOfPoint2f()
                            try {
                                Imgproc.approxPolyDP(hull2f, hullApprox, Imgproc.arcLength(hull2f, true) * HULL_APPROXIMATION_RATIO, true)
                                hullApprox.toArray()
                            } finally {
                                hullApprox.release()
                            }
                        } finally {
                            hull2f.release()
                        }
                    }
                    if (candidatePoints.size != 4) return@mapNotNull null
                    val pointsMat = MatOfPoint(*candidatePoints)
                    try {
                        if (!Imgproc.isContourConvex(pointsMat)) return@mapNotNull null
                    } finally {
                        pointsMat.release()
                    }
                    val points = orderPoints(candidatePoints.map { Point(it.x / scale, it.y / scale) })
                    if (!isPlausible(points, sourceWidth, sourceHeight)) return@mapNotNull null
                    DocumentCorners(points, sourceWidth, sourceHeight)
                } finally {
                    source.release()
                    approximation.release()
                    hullPoints?.release()
                    hullIndices?.release()
                }
            }
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
        }
    }

    private fun refine(candidate: DocumentCorners, edges: Mat, scale: Double): DocumentCorners {
        val width = edges.width()
        val height = edges.height()
        val pixels = ByteArray(width * height)
        edges.get(0, 0, pixels)
        val corners = candidate.points.map { Point(it.x * scale, it.y * scale) }
        val lines = corners.indices.map { index ->
            val start = corners[index]
            val end = corners[(index + 1) % 4]
            fitSide(pixels, width, height, start, end) ?: EdgeLine.through(start, end)
        }
        val refined = corners.indices.map { index ->
            val corner = intersect(lines[(index + 3) % 4], lines[index]) ?: return candidate
            if (distance(corner, corners[index]) > MAX_REFINE_SHIFT) return candidate
            Point(
                (corner.x / scale).coerceIn(0.0, candidate.imageWidth - 1.0),
                (corner.y / scale).coerceIn(0.0, candidate.imageHeight - 1.0),
            )
        }
        if (!isConvex(refined)) return candidate
        val ordered = orderPoints(refined)
        if (!isPlausible(ordered, candidate.imageWidth, candidate.imageHeight)) return candidate
        return candidate.copy(points = ordered)
    }

    private fun fitSide(pixels: ByteArray, width: Int, height: Int, start: Point, end: Point): EdgeLine? {
        val length = distance(start, end)
        if (length < MIN_REFINE_SIDE_LENGTH) return null
        val dx = (end.x - start.x) / length
        val dy = (end.y - start.y) / length
        val samples = (length / REFINE_SAMPLE_STEP).toInt().coerceIn(8, 80)
        val hits = ArrayList<Point>(samples + 1)
        for (sample in 0..samples) {
            val t = REFINE_SIDE_MARGIN + (1.0 - 2.0 * REFINE_SIDE_MARGIN) * sample / samples
            val baseX = start.x + (end.x - start.x) * t
            val baseY = start.y + (end.y - start.y) * t

            search@ for (offset in 0..REFINE_SEARCH_BAND) {
                for (sign in intArrayOf(1, -1)) {
                    if (offset == 0 && sign < 0) continue
                    val x = (baseX - dy * offset * sign).roundToInt()
                    val y = (baseY + dx * offset * sign).roundToInt()
                    if (x !in 0 until width || y !in 0 until height) continue
                    if (pixels[y * width + x].toInt() != 0) {
                        hits.add(Point(x.toDouble(), y.toDouble()))
                        break@search
                    }
                }
            }
        }
        if (hits.size < (samples + 1) * MIN_EDGE_SUPPORT) return null

        val hitsMat = MatOfPoint2f(*hits.toTypedArray())
        val lineMat = Mat()
        return try {
            Imgproc.fitLine(hitsMat, lineMat, Imgproc.DIST_HUBER, 0.0, 0.01, 0.01)
            val line = EdgeLine(lineMat.get(2, 0)[0], lineMat.get(3, 0)[0], lineMat.get(0, 0)[0], lineMat.get(1, 0)[0])

            if (abs(line.dx * dy - line.dy * dx) > MAX_REFINE_ANGLE_SIN) null else line
        } finally {
            hitsMat.release()
            lineMat.release()
        }
    }

    private fun intersect(a: EdgeLine, b: EdgeLine): Point? {
        val determinant = a.dx * b.dy - a.dy * b.dx
        if (abs(determinant) < 1e-6) return null
        val t = ((b.x - a.x) * b.dy - (b.y - a.y) * b.dx) / determinant
        return Point(a.x + a.dx * t, a.y + a.dy * t)
    }

    private fun isConvex(points: List<Point>): Boolean {
        val signs = points.indices.map { index ->
            val a = points[index]
            val b = points[(index + 1) % 4]
            val c = points[(index + 2) % 4]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) > 0.0
        }
        return signs.all { it } || signs.none { it }
    }

    private data class EdgeLine(val x: Double, val y: Double, val dx: Double, val dy: Double) {
        companion object {
            fun through(start: Point, end: Point): EdgeLine {
                val length = hypot(end.x - start.x, end.y - start.y).coerceAtLeast(1e-6)
                return EdgeLine(start.x, start.y, (end.x - start.x) / length, (end.y - start.y) / length)
            }
        }
    }

    private fun score(candidate: DocumentCorners, width: Int, height: Int): Double {
        val points = candidate.points
        val areaRatio = polygonArea(points) / (width.toDouble() * height.toDouble())
        val center = Point(points.sumOf { it.x } / 4.0, points.sumOf { it.y } / 4.0)
        val frameCenter = Point(width / 2.0, height / 2.0)
        val diagonal = hypot(width.toDouble(), height.toDouble())
        val centerScore = 1.0 - (hypot(center.x - frameCenter.x, center.y - frameCenter.y) / diagonal).coerceIn(0.0, 1.0)
        val rectangularity = polygonArea(points) / boundingArea(points).coerceAtLeast(1.0)
        val temporalScore = previous?.let { old ->
            val distance = old.points.zip(points).map { (a, b) -> hypot(a.x - b.x, a.y - b.y) }.average()
            1.0 - (distance / diagonal).coerceIn(0.0, 1.0)
        } ?: 0.5
        val framePenalty = points.count { point ->
            point.x < width * 0.02 || point.x > width * 0.98 || point.y < height * 0.02 || point.y > height * 0.98
        } * 0.25
        return areaRatio * 2.0 + rectangularity * 0.6 + centerScore * 0.8 + temporalScore - framePenalty
    }

    private fun isPlausible(points: List<Point>, width: Int, height: Int): Boolean {
        val areaRatio = polygonArea(points) / (width.toDouble() * height.toDouble())
        if (areaRatio < MINIMUM_AREA_RATIO) return false
        val frameEdges = points.count { point ->
            point.x < width * 0.02 || point.x > width * 0.98 || point.y < height * 0.02 || point.y > height * 0.98
        }
        if (frameEdges >= 4) return false
        val sideLengths = points.indices.map { index -> distance(points[index], points[(index + 1) % 4]) }
        if (sideLengths.minOrNull()!! / sideLengths.maxOrNull()!! < MIN_SIDE_RATIO) return false
        val bounds = boundingRect(points)
        val aspect = maxOf(bounds.width, bounds.height).toDouble() / minOf(bounds.width, bounds.height).coerceAtLeast(1)
        return aspect <= MAX_ASPECT_RATIO
    }

    private fun polygonArea(points: List<Point>): Double = abs(points.indices.sumOf { index ->
        val next = (index + 1) % points.size
        points[index].x * points[next].y - points[next].x * points[index].y
    }) / 2.0

    private fun boundingArea(points: List<Point>): Double {
        val rect = boundingRect(points)
        return rect.width.toDouble() * rect.height.toDouble()
    }

    private fun boundingRect(points: List<Point>): Rect {
        val minX = points.minOf { it.x }.toInt()
        val minY = points.minOf { it.y }.toInt()
        val maxX = points.maxOf { it.x }.toInt()
        val maxY = points.maxOf { it.y }.toInt()
        return Rect(minX, minY, maxX - minX, maxY - minY)
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)

    companion object {
        private const val MAX_ANALYSIS_SIZE = 720.0
        private const val MINIMUM_AREA_RATIO = 0.05
        private const val MIN_SIDE_RATIO = 0.04
        private const val MAX_ASPECT_RATIO = 12.0
        private const val CANNY_LOW = 35.0
        private const val CANNY_HIGH = 140.0
        private const val APPROXIMATION_RATIO = 0.025
        private const val HULL_APPROXIMATION_RATIO = 0.03

        private const val REFINE_SEARCH_BAND = 6
        private const val REFINE_SAMPLE_STEP = 4.0
        private const val REFINE_SIDE_MARGIN = 0.1
        private const val MIN_REFINE_SIDE_LENGTH = 40.0
        private const val MIN_EDGE_SUPPORT = 0.5
        private const val MAX_REFINE_SHIFT = 12.0
        private const val MAX_REFINE_ANGLE_SIN = 0.14

        fun orderPoints(points: List<Point>): List<Point> {
            require(points.size == 4)
            val centerX = points.sumOf { it.x } / 4.0
            val centerY = points.sumOf { it.y } / 4.0
            val sorted = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
            val first = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            val rotated = (0 until 4).map { sorted[(first + it) % 4] }
            val cross = (rotated[1].x - rotated[0].x) * (rotated[2].y - rotated[1].y) -
                (rotated[1].y - rotated[0].y) * (rotated[2].x - rotated[1].x)
            return if (cross >= 0.0) rotated else listOf(rotated[0], rotated[3], rotated[2], rotated[1])
        }
    }
}
