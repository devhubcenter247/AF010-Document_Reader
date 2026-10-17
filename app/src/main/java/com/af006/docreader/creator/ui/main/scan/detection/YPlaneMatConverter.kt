package com.af010.creator.docreader.ui.main.scan.detection

import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat

object YPlaneMatConverter {
    fun toMat(image: ImageProxy): Mat {
        val plane = image.planes[0]
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val output = ByteArray(width * height)

        if (pixelStride == 1 && rowStride == width && buffer.remaining() >= output.size) {
            buffer.get(output)
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until height) {
                val offset = y * rowStride
                val available = buffer.limit() - offset
                if (available <= 0) break
                val count = minOf(rowStride, available)
                buffer.position(offset)
                buffer.get(row, 0, count)
                for (x in 0 until width) {
                    val sourceIndex = x * pixelStride
                    if (sourceIndex < count) output[y * width + x] = row[sourceIndex]
                }
            }
        }

        return Mat(height, width, CvType.CV_8UC1).also { it.put(0, 0, output) }
    }
}
