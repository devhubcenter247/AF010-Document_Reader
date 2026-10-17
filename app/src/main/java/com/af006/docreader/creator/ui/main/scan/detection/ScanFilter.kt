package com.af010.creator.docreader.ui.main.scan.detection

import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Colour filters for a cropped scan. Each one reads a BGR [Mat] and writes a BGR result, so a filter
 * always starts from the unfiltered crop and never stacks on a previous one.
 *
 * Sizes used inside (background estimate, blur, threshold block) scale with the image, so a thumbnail
 * looks like the full page.
 */
enum class ScanFilter(@StringRes val label: Int) {
    Default(R.string.scanner_original) {
        override fun apply(src: Mat, dst: Mat) {
            src.copyTo(dst)
        }
    },

    /** Stretches each channel's levels to the full range: fixes dull or tinted photos. */
    Auto(R.string.scanner_filter_auto) {
        override fun apply(src: Mat, dst: Mat) {
            autoLevels(src, dst)
        }
    },

    /** Shadow-free gray page with darker text; keeps pencil and faint marks that B&W would drop. */
    Document(R.string.scanner_filter_document) {
        override fun apply(src: Mat, dst: Mat) {
            withShadowFreeGray(src, dst) { gray -> gray.convertTo(gray, -1, 1.25, -40.0) }
        }
    },

    /** Shadow-free black text on white, one global threshold. */
    BlackWhite(R.string.scanner_filter_black_white) {
        override fun apply(src: Mat, dst: Mat) {
            withShadowFreeGray(src, dst) { gray ->
                Imgproc.threshold(gray, gray, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            }
        }
    },

    Grayscale(R.string.scanner_filter_grayscale) {
        override fun apply(src: Mat, dst: Mat) {
            val gray = Mat()
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            try {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
                clahe.apply(gray, gray)
                Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2BGR)
            } finally {
                gray.release()
            }
        }
    },

    Bright(R.string.scanner_filter_bright) {
        override fun apply(src: Mat, dst: Mat) {
            src.convertTo(dst, -1, 1.15, 25.0)
        }
    },

    /** Enhance plus livelier colours and crisper edges: the all-round document look. */
    Magic(R.string.scanner_filter_magic) {
        override fun apply(src: Mat, dst: Mat) {
            removeShadows(src, dst)
            dst.convertTo(dst, -1, 1.15, -18.0)
            saturate(dst, 1.25)
            sharpen(dst, 0.6)
        }
    },

    /** Flattens uneven lighting and shadows to a white page while keeping ink colours. */
    Enhance(R.string.scanner_filter_enhance) {
        override fun apply(src: Mat, dst: Mat) {
            removeShadows(src, dst)
            dst.convertTo(dst, -1, 1.1, -12.0)
        }
    },

    Sharpen(R.string.scanner_filter_sharpen) {
        override fun apply(src: Mat, dst: Mat) {
            src.copyTo(dst)
            sharpen(dst, 1.0)
        }
    },

    /** Local threshold per area: stays readable on unevenly lit or wrinkled pages where B&W fails. */
    Text(R.string.scanner_filter_text) {
        override fun apply(src: Mat, dst: Mat) {
            val gray = Mat()
            try {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
                val block = (min(gray.cols(), gray.rows()) / 25 or 1).coerceAtLeast(11)
                Imgproc.adaptiveThreshold(
                    gray, gray, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, block, 12.0,
                )
                Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2BGR)
            } finally {
                gray.release()
            }
        }
    },

    /** Shadow-free gray with lighter ink, to save toner when printing. */
    Eco(R.string.scanner_filter_eco) {
        override fun apply(src: Mat, dst: Mat) {
            withShadowFreeGray(src, dst) { gray -> gray.convertTo(gray, -1, 0.6, 102.0) }
        }
    },

    /** White text on black, for reading in the dark. */
    Night(R.string.scanner_filter_night) {
        override fun apply(src: Mat, dst: Mat) {
            withShadowFreeGray(src, dst) { gray -> Core.bitwise_not(gray, gray) }
        }
    },

    /** Stronger saturation and contrast, for colourful pages and photos. */
    Vivid(R.string.scanner_filter_vivid) {
        override fun apply(src: Mat, dst: Mat) {
            src.copyTo(dst)
            saturate(dst, 1.4)
            dst.convertTo(dst, -1, 1.1, -10.0)
        }
    },

    Warm(R.string.scanner_filter_warm) {
        override fun apply(src: Mat, dst: Mat) {
            Core.multiply(src, Scalar(0.88, 1.0, 1.12), dst)
        }
    },

    Cool(R.string.scanner_filter_cool) {
        override fun apply(src: Mat, dst: Mat) {
            Core.multiply(src, Scalar(1.12, 1.0, 0.9), dst)
        }
    },

    Sepia(R.string.scanner_filter_sepia) {
        override fun apply(src: Mat, dst: Mat) {
            // Classic sepia matrix, rows/columns in OpenCV's BGR order.
            val kernel = Mat(3, 3, CvType.CV_32F).apply {
                put(0, 0, 0.131, 0.534, 0.272)
                put(1, 0, 0.168, 0.686, 0.349)
                put(2, 0, 0.189, 0.769, 0.393)
            }
            try {
                Core.transform(src, dst, kernel)
            } finally {
                kernel.release()
            }
        }
    };

    /** [src] is BGR (8-bit, 3 channels); writes a BGR image of the same size into [dst]. */
    abstract fun apply(src: Mat, dst: Mat)

    private companion object {
        const val BACKGROUND_SIDE = 256.0
        const val LEVELS_CLIP = 0.01

        /**
         * Paper colour at every pixel: text is removed with a dilate + median blur on a small copy,
         * then scaled back up. Dividing by it evens out shadows and lighting.
         */
        fun estimateBackground(src: Mat): Mat {
            val scale = min(1.0, BACKGROUND_SIDE / min(src.cols(), src.rows()))
            val small = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            val background = Mat()
            try {
                Imgproc.resize(src, small, Size(), scale, scale, Imgproc.INTER_AREA)
                Imgproc.dilate(small, small, kernel)
                Imgproc.medianBlur(small, small, 21)
                Imgproc.resize(small, background, src.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            } finally {
                kernel.release()
                small.release()
            }
            return background
        }

        /** [src] divided by its background, so the page turns white; works for gray or BGR. */
        fun removeShadows(src: Mat, dst: Mat) {
            val background = estimateBackground(src)
            try {
                Core.divide(src, background, dst, 255.0)
            } finally {
                background.release()
            }
        }

        /** Runs [block] on a shadow-free gray copy of [src] and writes it back to [dst] as BGR. */
        inline fun withShadowFreeGray(src: Mat, dst: Mat, block: (Mat) -> Unit) {
            val gray = Mat()
            try {
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
                removeShadows(gray, gray)
                block(gray)
                Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2BGR)
            } finally {
                gray.release()
            }
        }

        /** Scales the saturation of a BGR [mat] in place by [factor]. */
        fun saturate(mat: Mat, factor: Double) {
            val hsv = Mat()
            val channels = ArrayList<Mat>(3)
            try {
                Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)
                Core.split(hsv, channels)
                channels[1].convertTo(channels[1], -1, factor, 0.0)
                Core.merge(channels, hsv)
                Imgproc.cvtColor(hsv, mat, Imgproc.COLOR_HSV2BGR)
            } finally {
                channels.forEach { it.release() }
                hsv.release()
            }
        }

        /** Unsharp mask in place; the blur radius follows the image size. */
        fun sharpen(mat: Mat, amount: Double) {
            val blurred = Mat()
            try {
                val sigma = max(1.0, min(mat.cols(), mat.rows()) / 600.0)
                Imgproc.GaussianBlur(mat, blurred, Size(), sigma)
                Core.addWeighted(mat, 1.0 + amount, blurred, -amount, 0.0, mat)
            } finally {
                blurred.release()
            }
        }

        /** Per channel, maps the darkest/brightest [LEVELS_CLIP] of pixels to 0/255. */
        fun autoLevels(src: Mat, dst: Mat) {
            val channels = ArrayList<Mat>(3)
            val hist = Mat()
            try {
                Core.split(src, channels)
                val total = src.total().toDouble()
                for (channel in channels) {
                    Imgproc.calcHist(listOf(channel), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
                    val counts = FloatArray(256).also { hist.get(0, 0, it) }
                    var low = 0
                    var sum = 0.0
                    while (low < 255 && sum + counts[low] <= total * LEVELS_CLIP) sum += counts[low++]
                    var high = 255
                    sum = 0.0
                    while (high > low && sum + counts[high] <= total * LEVELS_CLIP) sum += counts[high--]
                    if (high - low < 10) continue
                    val alpha = 255.0 / (high - low)
                    channel.convertTo(channel, -1, alpha, -low * alpha)
                }
                Core.merge(channels, dst)
            } finally {
                hist.release()
                channels.forEach { it.release() }
            }
        }
    }
}
