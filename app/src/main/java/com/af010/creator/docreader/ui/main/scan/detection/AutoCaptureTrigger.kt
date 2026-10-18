package com.af010.creator.docreader.ui.main.scan.detection

import kotlin.math.hypot

class AutoCaptureTrigger {
    private var anchor: DocumentCorners? = null
    private var anchorTimeMs = 0L

    fun update(corners: DocumentCorners?, nowMs: Long): Boolean {
        if (corners == null) {
            reset()
            return false
        }
        val previous = anchor
        if (previous == null || !isSteady(previous, corners)) {
            anchor = corners
            anchorTimeMs = nowMs
            return false
        }
        return nowMs - anchorTimeMs >= HOLD_MS
    }

    fun reset() {
        anchor = null
        anchorTimeMs = 0L
    }

    private fun isSteady(a: DocumentCorners, b: DocumentCorners): Boolean {
        val diagonal = hypot(a.imageWidth.toDouble(), a.imageHeight.toDouble())
        return a.points.zip(b.points).all { (left, right) ->
            hypot(left.x - right.x, left.y - right.y) <= diagonal * MAX_MOVEMENT
        }
    }

    private companion object {
        const val HOLD_MS = 1200L
        const val MAX_MOVEMENT = 0.025
    }
}
