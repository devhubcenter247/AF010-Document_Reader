package com.af010.creator.docreader.ui.main.scan.detection

import android.graphics.Bitmap
import com.af010.creator.docreader.ui.main.scan.PendingCrop
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

data class ProcessedScan(val original: File, val output: File)

/** A line painted on the clean-up screen: [points] as fractions of the image, [width] as a fraction of its width. */
class EraseStroke(val points: List<Point>, val width: Double)

class DocumentImageProcessor(
    private val detector: DocumentEdgeDetector = DocumentEdgeDetector(),
) {
    fun process(original: File, liveCorners: List<Point>? = null): ProcessedScan {
        val source = Imgcodecs.imread(original.absolutePath, Imgcodecs.IMREAD_COLOR)
        if (source.empty()) {
            source.release()
            return ProcessedScan(original, original)
        }
        val gray = Mat()
        return try {
            val points = liveCorners?.takeIf { it.size == 4 }?.let { fractions ->
                DocumentEdgeDetector.orderPoints(fractions.map { Point(it.x * source.cols(), it.y * source.rows()) })
            } ?: run {
                Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)
                detector.detect(gray)?.expandedBy()?.points
            } ?: return ProcessedScan(original, original)

            val tl = points[0]
            val tr = points[1]
            val br = points[2]
            val bl = points[3]
            val outputWidth = max(distance(tl, tr), distance(bl, br)).toInt().coerceAtLeast(1)
            val outputHeight = max(distance(tl, bl), distance(tr, br)).toInt().coerceAtLeast(1)
            val sourcePoints = MatOfPoint2f(tl, tr, br, bl)
            val targetPoints = MatOfPoint2f(
                Point(0.0, 0.0), Point(outputWidth - 1.0, 0.0),
                Point(outputWidth - 1.0, outputHeight - 1.0), Point(0.0, outputHeight - 1.0),
            )
            val transform = Imgproc.getPerspectiveTransform(sourcePoints, targetPoints)
            val output = Mat()
            try {
                Imgproc.warpPerspective(source, output, transform, Size(outputWidth.toDouble(), outputHeight.toDouble()), Imgproc.INTER_CUBIC)
                val corrected = File(original.parentFile, "scan_${System.currentTimeMillis()}.jpg")
                if (Imgcodecs.imwrite(corrected.absolutePath, output)) ProcessedScan(original, corrected)
                else ProcessedScan(original, original)
            } finally {
                sourcePoints.release()
                targetPoints.release()
                transform.release()
                output.release()
            }
        } finally {
            gray.release()
            source.release()
        }
    }

    fun prepareCrop(original: File, liveCorners: List<Point>? = null): PendingCrop? {
        val source = Imgcodecs.imread(original.absolutePath, Imgcodecs.IMREAD_COLOR)
        val gray = Mat()
        try {
            if (source.empty()) return null
            val width = source.cols()
            val height = source.rows()
            val corners = liveCorners?.takeIf { it.size == 4 } ?: run {
                Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY)
                detector.detect(gray)?.expandedBy()?.points?.map { Point(it.x / width, it.y / height) }
            } ?: listOf(
                Point(DEFAULT_INSET, DEFAULT_INSET), Point(1 - DEFAULT_INSET, DEFAULT_INSET),
                Point(1 - DEFAULT_INSET, 1 - DEFAULT_INSET), Point(DEFAULT_INSET, 1 - DEFAULT_INSET),
            )
            return PendingCrop(original, width, height, corners)
        } finally {
            gray.release()
            source.release()
        }
    }

    fun rotateClockwise(file: File): File? {
        val source = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_COLOR)
        val rotated = Mat()
        try {
            if (source.empty()) return null
            Core.rotate(source, rotated, Core.ROTATE_90_CLOCKWISE)
            val target = File(file.parentFile, "rotated_${System.currentTimeMillis()}.jpg")
            return if (Imgcodecs.imwrite(target.absolutePath, rotated)) target else null
        } finally {
            rotated.release()
            source.release()
        }
    }

    /** Writes [file] with [filter] applied to a new JPEG; [ScanFilter.Default] returns [file] itself. */
    fun applyFilter(file: File, filter: ScanFilter): File? {
        if (filter == ScanFilter.Default) return file
        val source = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_COLOR)
        val filtered = Mat()
        try {
            if (source.empty()) return null
            filter.apply(source, filtered)
            val target = File(file.parentFile, "filter_${System.currentTimeMillis()}.jpg")
            return if (Imgcodecs.imwrite(target.absolutePath, filtered)) target else null
        } finally {
            filtered.release()
            source.release()
        }
    }

    /**
     * Writes [file] with the painted [strokes] removed to a new JPEG: what is under them is
     * replaced by the paper around it. Null when nothing could be written.
     */
    fun cleanUp(file: File, strokes: List<EraseStroke>): File? {
        val mats = mutableListOf<Mat>()
        fun mat() = Mat().also { mats += it }
        try {
            val source = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_COLOR).also { mats += it }
            if (source.empty()) return null
            val width = source.cols()
            val height = source.rows()

            val mask = Mat.zeros(height, width, CvType.CV_8UC1).also { mats += it }
            val white = Scalar(255.0)
            for (stroke in strokes) {
                val thickness = (stroke.width * width).roundToInt().coerceIn(1, MAX_THICKNESS)
                val points = stroke.points.map { Point(it.x * width, it.y * height) }
                points.firstOrNull()?.let { Imgproc.circle(mask, it, thickness / 2, white, Imgproc.FILLED) }
                if (points.size > 1) {
                    val line = MatOfPoint(*points.toTypedArray()).also { mats += it }
                    Imgproc.polylines(mask, listOf(line), false, white, thickness)
                }
            }
            if (Core.countNonZero(mask) == 0) return null

            // The paper is estimated on a small copy: a median wipes text and thin lines out of it.
            val scale = minOf(1.0, FILL_SIDE.toDouble() / max(width, height))
            val small = mat()
            Imgproc.resize(source, small, Size(), scale, scale, Imgproc.INTER_AREA)
            val paper = mat()
            Imgproc.medianBlur(small, paper, MEDIAN_SIZE)
            // The hole is a little wider than the strokes, so the rim of a stain does not leak in.
            val hole = mat()
            Imgproc.resize(mask, hole, small.size(), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.threshold(hole, hole, 0.0, 255.0, Imgproc.THRESH_BINARY)
            val holeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, Size(2.0 * HOLE_MARGIN + 1, 2.0 * HOLE_MARGIN + 1)
            ).also { mats += it }
            Imgproc.dilate(hole, hole, holeKernel)
            val fillSmall = mat()
            fillHole(paper, hole).also { mats += it }.convertTo(fillSmall, CvType.CV_8U)
            val fill = mat()
            Imgproc.resize(fillSmall, fill, source.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)

            // Blend with a soft edge that stays inside the margin of the hole.
            val edge = ((HOLE_MARGIN - 1) / scale).toInt().coerceAtLeast(3) or 1
            val bounds = Imgproc.boundingRect(mask)
            val left = (bounds.x - 2 * edge).coerceAtLeast(0)
            val top = (bounds.y - 2 * edge).coerceAtLeast(0)
            val right = (bounds.x + bounds.width + 2 * edge).coerceAtMost(width)
            val bottom = (bounds.y + bounds.height + 2 * edge).coerceAtMost(height)
            val area = Rect(left, top, right - left, bottom - top)
            val target = source.submat(area).also { mats += it }
            val alpha = mat()
            val edgeKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, Size(edge.toDouble(), edge.toDouble())
            ).also { mats += it }
            Imgproc.dilate(mask.submat(area).also { mats += it }, alpha, edgeKernel)
            Imgproc.GaussianBlur(alpha, alpha, Size(edge.toDouble(), edge.toDouble()), 0.0)
            val alpha3 = mat()
            Core.merge(listOf(alpha, alpha, alpha), alpha3)
            val keep3 = mat()
            Core.bitwise_not(alpha3, keep3)
            val filled = mat()
            Core.multiply(fill.submat(area).also { mats += it }, alpha3, filled, 1.0 / 255)
            val kept = mat()
            Core.multiply(target, keep3, kept, 1.0 / 255)
            Core.add(filled, kept, target)

            val cleaned = File(file.parentFile, "clean_${System.currentTimeMillis()}.jpg")
            return if (Imgcodecs.imwrite(cleaned.absolutePath, source)) cleaned else null
        } finally {
            mats.forEach { it.release() }
        }
    }

    /**
     * [image] as floats with the pixels under [hole] replaced by a blurred average of the known
     * pixels around them, working inwards one band per pass until the hole is closed.
     */
    private fun fillHole(image: Mat, hole: Mat): Mat {
        val result = Mat()
        image.convertTo(result, CvType.CV_32F)
        val known = Mat()
        hole.convertTo(known, CvType.CV_32F, -1.0 / 255, 1.0)
        val unknown = Mat()
        val known3 = Mat()
        val sum = Mat()
        val weight = Mat()
        val weight3 = Mat()
        val reached = Mat()
        try {
            repeat(MAX_PASSES) {
                Core.compare(known, Scalar(0.5), unknown, Core.CMP_LT)
                if (Core.countNonZero(unknown) == 0) return result
                Core.merge(listOf(known, known, known), known3)
                Core.multiply(result, known3, sum)
                Imgproc.GaussianBlur(sum, sum, Size(), FILL_SIGMA)
                Imgproc.GaussianBlur(known, weight, Size(), FILL_SIGMA)
                Core.compare(weight, Scalar(MIN_WEIGHT), reached, Core.CMP_GT)
                Core.bitwise_and(reached, unknown, reached)
                // Nothing known anywhere: the whole page was painted, the median is all there is.
                if (Core.countNonZero(reached) == 0) return result
                Core.merge(listOf(weight, weight, weight), weight3)
                Core.add(weight3, Scalar.all(1e-6), weight3)
                Core.divide(sum, weight3, sum)
                sum.copyTo(result, reached)
                known.setTo(Scalar(1.0), reached)
            }
            return result
        } finally {
            listOf(known, unknown, known3, sum, weight, weight3, reached).forEach { it.release() }
        }
    }

    /** Decodes [file] shrunk so its longer side is at most [maxSide], for on-screen previews. */
    fun loadPreview(file: File, maxSide: Int): Bitmap? {
        val source = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_COLOR)
        val scaled = Mat()
        try {
            if (source.empty()) return null
            val scale = minOf(1.0, maxSide.toDouble() / max(source.cols(), source.rows()))
            Imgproc.resize(source, scaled, Size(), scale, scale, Imgproc.INTER_AREA)
            return scaled.toBitmap()
        } finally {
            scaled.release()
            source.release()
        }
    }

    /** Returns a new bitmap with [filter] applied to [bitmap]. */
    fun filterBitmap(bitmap: Bitmap, filter: ScanFilter): Bitmap {
        val rgba = Mat()
        val bgr = Mat()
        val filtered = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            filter.apply(bgr, filtered)
            return filtered.toBitmap()
        } finally {
            filtered.release()
            bgr.release()
            rgba.release()
        }
    }

    private fun Mat.toBitmap(): Bitmap {
        val rgba = Mat()
        try {
            Imgproc.cvtColor(this, rgba, Imgproc.COLOR_BGR2RGBA)
            return Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(rgba, it) }
        } finally {
            rgba.release()
        }
    }

    private fun distance(a: Point, b: Point): Double = hypot(a.x - b.x, a.y - b.y)

    private companion object {
        const val DEFAULT_INSET = 0.05

        /** Longer side of the copy the clean-up fill is computed on. */
        const val FILL_SIDE = 512
        const val MEDIAN_SIZE = 21

        /** How far the filled hole reaches past the strokes, in pixels of the small copy. */
        const val HOLE_MARGIN = 4
        const val FILL_SIGMA = 6.0
        const val MIN_WEIGHT = 0.1
        const val MAX_PASSES = 64

        /** OpenCV's limit for a line. */
        const val MAX_THICKNESS = 32767
    }
}
