package com.af010.creator.docreader.ui.main.files

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.constraintlayout.motion.widget.MotionLayout
import kotlin.math.abs

/**
 * The Files tab's MotionLayout (fragment_files.xml, scene_files_home). It moves the scene with the
 * finger itself instead of OnSwipe: a plain MotionLayout never takes a drag that starts on a child
 * handling taps (the cards and tiles cover most of the screen), and OnSwipe cannot follow a region
 * that only moves by translation.
 *
 * A vertical drag the motion can follow is taken over once it passes the touch slop: up until
 * collapsed, down while [scrollingList] is at its top. Anything else (taps, sideways drags,
 * scrolling the list) goes to the children. On release the motion finishes in the direction of a
 * fling, or to the nearer state.
 */
class HomeMotionLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MotionLayout(context, attrs, defStyleAttr) {

    /** The list under the top region: dragging down scrolls it until it is back at its top. */
    var scrollingList: View? = null

    /** How far the finger moves from expanded to collapsed, in px. */
    var collapseDistance = 0

    private val config = ViewConfiguration.get(context)
    private val touchSlop = config.scaledTouchSlop
    private val minFling = config.scaledMinimumFlingVelocity

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragStartY = 0f
    private var dragStartProgress = 0f
    private var velocity: VelocityTracker? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startTracking(event)
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (!dragging && canStartDrag(event)) startDrag(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopTracking()
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            // Nothing under the finger took the touch: follow it from here.
            MotionEvent.ACTION_DOWN -> startTracking(event)
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (!dragging && canStartDrag(event)) startDrag(event)
                if (dragging && collapseDistance > 0) {
                    progress = (dragStartProgress - (event.y - dragStartY) / collapseDistance).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) settle()
                stopTracking()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) settle(fling = false)
                stopTracking()
            }
        }
        return true
    }

    private fun startTracking(event: MotionEvent) {
        downX = event.x
        downY = event.y
        dragging = false
        velocity?.recycle()
        velocity = VelocityTracker.obtain().apply { addMovement(event) }
    }

    private fun canStartDrag(event: MotionEvent): Boolean {
        val dy = event.y - downY
        if (collapseDistance <= 0 || abs(dy) <= touchSlop || abs(dy) <= abs(event.x - downX)) return false
        return if (dy < 0) progress < 1f else progress > 0f && scrollingList?.canScrollVertically(-1) != true
    }

    private fun startDrag(event: MotionEvent) {
        dragging = true
        dragStartY = event.y
        dragStartProgress = progress
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** Finishes the motion: in the direction of a fling, otherwise to the nearer state. */
    private fun settle(fling: Boolean = true) {
        val speed = velocity?.run { computeCurrentVelocity(1000); yVelocity } ?: 0f
        val collapse = if (fling && abs(speed) > minFling) speed < 0 else progress > 0.5f
        if (collapse) transitionToEnd() else transitionToStart()
    }

    private fun stopTracking() {
        dragging = false
        velocity?.recycle()
        velocity = null
    }
}
