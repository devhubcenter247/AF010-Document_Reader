package com.af010.creator.docreader.ui.main.scan.detection

import kotlin.math.hypot
import org.opencv.core.Point

enum class DocumentState { NOT_FOUND, SEARCHING, STABLE }

class CornerStabilizer {
    private var candidate: DocumentCorners? = null
    private var stable: DocumentCorners? = null
    private var similarFrames = 0
    private var missedFrames = 0

    var state: DocumentState = DocumentState.NOT_FOUND
        private set

    fun update(value: DocumentCorners?): DocumentCorners? {
        if (value == null) {
            missedFrames++
            if (missedFrames >= MAX_MISSED_FRAMES) {
                candidate = null
                stable = null
                similarFrames = 0
                state = DocumentState.NOT_FOUND
                return null
            }
            state = if (stable == null) DocumentState.SEARCHING else DocumentState.STABLE
            return stable
        }
        missedFrames = 0
        val previous = candidate
        if (previous != null && isSimilar(previous, value)) {
            similarFrames++
        } else {
            candidate = value
            similarFrames = 1
        }
        if (similarFrames < REQUIRED_FRAMES) {
            state = DocumentState.SEARCHING
            return stable
        }
        val base = stable ?: value
        val averaged = base.points.zip(value.points) { old, new ->
            Point((old.x + new.x) / 2.0, (old.y + new.y) / 2.0)
        }
        stable = value.copy(points = averaged)
        state = DocumentState.STABLE
        return stable
    }

    private fun isSimilar(a: DocumentCorners, b: DocumentCorners): Boolean {
        val diagonal = hypot(a.imageWidth.toDouble(), a.imageHeight.toDouble())
        return a.points.zip(b.points).all { (left, right) ->
            hypot(left.x - right.x, left.y - right.y) <= diagonal * MAX_NORMALIZED_DISTANCE
        }
    }

    private companion object {
        const val REQUIRED_FRAMES = 2

        const val MAX_MISSED_FRAMES = 12
        const val MAX_NORMALIZED_DISTANCE = 0.08
    }
}
