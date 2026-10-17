package com.ui.baselib.widget.flexbox

import android.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min

class FlexboxItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private var mDrawable: Drawable?

    private var mOrientation: Int = BOTH

    init {
        val a = context.obtainStyledAttributes(LIST_DIVIDER_ATTRS)
        mDrawable = a.getDrawable(0)
        a.recycle()
        setOrientation(BOTH)
    }

    /**
     * Set the drawable used as the item decoration.
     * If the drawable is not set, the default list divider is used as the
     * item decoration.
     */
    fun setDrawable(drawable: Drawable) {
        mDrawable = drawable
    }

    /**
     * Set the orientation for the decoration.
     * Orientation for the decoration can be either of:
     * - Horizontal (setOrientation(HORIZONTAL))
     * - Vertical (setOrientation(VERTICAL))
     * - Both orientation (setOrientation(BOTH))
     */
    fun setOrientation(orientation: Int) {
        mOrientation = orientation
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        drawHorizontalDecorations(canvas, parent)
        drawVerticalDecorations(canvas, parent)
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == 0) {
            return
        }
        if (!needsHorizontalDecoration() && !needsVerticalDecoration()) {
            outRect.set(0, 0, 0, 0)
            return
        }
        val layoutManager = parent.layoutManager as FlexboxLayoutManager
        val flexLines = layoutManager.flexLines
        val flexDirection = layoutManager.flexDirection
        setOffsetAlongMainAxis(outRect, position, layoutManager, flexLines, flexDirection)
        setOffsetAlongCrossAxis(outRect, position, layoutManager, flexLines)
    }

    private fun setOffsetAlongCrossAxis(
        outRect: Rect,
        position: Int,
        layoutManager: FlexboxLayoutManager,
        flexLines: List<FlexLine>
    ) {
        if (flexLines.isEmpty()) {
            return
        }
        val flexLineIndex = layoutManager.getPositionToFlexLineIndex(position)
        if (flexLineIndex == 0) {
            return
        }

        val drawable = mDrawable ?: return

        if (layoutManager.isMainAxisDirectionHorizontal()) {
            if (!needsHorizontalDecoration()) {
                outRect.top = 0
                outRect.bottom = 0
                return
            }
            outRect.top = drawable.intrinsicHeight
            outRect.bottom = 0
        } else {
            if (!needsVerticalDecoration()) {
                return
            }
            if (layoutManager.isLayoutRtl()) {
                outRect.right = drawable.intrinsicWidth
                outRect.left = 0
            } else {
                outRect.left = drawable.intrinsicWidth
                outRect.right = 0
            }
        }
    }

    private fun setOffsetAlongMainAxis(
        outRect: Rect,
        position: Int,
        layoutManager: FlexboxLayoutManager,
        flexLines: List<FlexLine>,
        flexDirection: Int
    ) {
        if (isFirstItemInLine(position, flexLines, layoutManager)) {
            return
        }

        val drawable = mDrawable ?: return

        if (layoutManager.isMainAxisDirectionHorizontal()) {
            if (!needsVerticalDecoration()) {
                outRect.left = 0
                outRect.right = 0
                return
            }
            if (layoutManager.isLayoutRtl()) {
                outRect.right = drawable.intrinsicWidth
                outRect.left = 0
            } else {
                outRect.left = drawable.intrinsicWidth
                outRect.right = 0
            }
        } else {
            if (!needsHorizontalDecoration()) {
                outRect.top = 0
                outRect.bottom = 0
                return
            }
            if (flexDirection == FlexDirection.COLUMN_REVERSE) {
                outRect.bottom = drawable.intrinsicHeight
                outRect.top = 0
            } else {
                outRect.top = drawable.intrinsicHeight
                outRect.bottom = 0
            }
        }
    }

    private fun drawVerticalDecorations(canvas: Canvas, parent: RecyclerView) {
        if (!needsVerticalDecoration()) {
            return
        }
        val drawable = mDrawable ?: return
        val layoutManager = parent.layoutManager as FlexboxLayoutManager
        val parentTop = parent.top - parent.paddingTop
        val parentBottom = parent.bottom + parent.paddingBottom
        val childCount = parent.childCount
        val flexDirection = layoutManager.flexDirection

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val lp = child.layoutParams as RecyclerView.LayoutParams

            val left: Int
            val right: Int
            if (layoutManager.isLayoutRtl()) {
                left = child.right + lp.rightMargin
                right = left + drawable.intrinsicWidth
            } else {
                right = child.left - lp.leftMargin
                left = right - drawable.intrinsicWidth
            }

            val top: Int
            val bottom: Int
            if (layoutManager.isMainAxisDirectionHorizontal()) {
                top = child.top - lp.topMargin
                bottom = child.bottom + lp.bottomMargin
            } else {
                if (flexDirection == FlexDirection.COLUMN_REVERSE) {
                    bottom = min(
                        child.bottom + lp.bottomMargin + drawable.intrinsicHeight,
                        parentBottom
                    )
                    top = child.top - lp.topMargin
                } else {
                    top = max(
                        child.top - lp.topMargin - drawable.intrinsicHeight,
                        parentTop
                    )
                    bottom = child.bottom + lp.bottomMargin
                }
            }

            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }
    }

    private fun drawHorizontalDecorations(canvas: Canvas, parent: RecyclerView) {
        if (!needsHorizontalDecoration()) {
            return
        }
        val drawable = mDrawable ?: return
        val layoutManager = parent.layoutManager as FlexboxLayoutManager
        val flexDirection = layoutManager.flexDirection
        val parentLeft = parent.left - parent.paddingLeft
        val parentRight = parent.right + parent.paddingRight
        val childCount = parent.childCount

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val lp = child.layoutParams as RecyclerView.LayoutParams

            val top: Int
            val bottom: Int
            if (flexDirection == FlexDirection.COLUMN_REVERSE) {
                top = child.bottom + lp.bottomMargin
                bottom = top + drawable.intrinsicHeight
            } else {
                bottom = child.top - lp.topMargin
                top = bottom - drawable.intrinsicHeight
            }

            val left: Int
            val right: Int
            if (layoutManager.isMainAxisDirectionHorizontal()) {
                if (layoutManager.isLayoutRtl()) {
                    right = min(
                        child.right + lp.rightMargin + drawable.intrinsicWidth,
                        parentRight
                    )
                    left = child.left - lp.leftMargin
                } else {
                    left = max(
                        child.left - lp.leftMargin - drawable.intrinsicWidth,
                        parentLeft
                    )
                    right = child.right + lp.rightMargin
                }
            } else {
                left = child.left - lp.leftMargin
                right = child.right + lp.rightMargin
            }
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }
    }

    private fun needsHorizontalDecoration(): Boolean {
        return (mOrientation and HORIZONTAL) > 0
    }

    private fun needsVerticalDecoration(): Boolean {
        return (mOrientation and VERTICAL) > 0
    }

    /**
     * @return `true` if the given position is the first item in a flex line.
     */
    private fun isFirstItemInLine(
        position: Int,
        flexLines: List<FlexLine>,
        layoutManager: FlexboxLayoutManager
    ): Boolean {
        val flexLineIndex = layoutManager.getPositionToFlexLineIndex(position)
        if (flexLineIndex != RecyclerView.NO_POSITION &&
            flexLineIndex < layoutManager.flexLinesInternal.size &&
            layoutManager.flexLinesInternal[flexLineIndex].mFirstIndex == position
        ) {
            return true
        }
        if (position == 0) {
            return true
        }
        if (flexLines.isEmpty()) {
            return false
        }
        // Check if the position is the "lastIndex + 1" of the last line in case the FlexLine which
        // has the View, whose index is position is not included in the flexLines. (E.g. flexLines
        // is being calculated
        val lastLine = flexLines[flexLines.size - 1]
        return lastLine.mLastIndex == position - 1
    }

    companion object {
        @JvmField
        val HORIZONTAL = 1

        @JvmField
        val VERTICAL = 1 shl 1

        @JvmField
        val BOTH = HORIZONTAL or VERTICAL

        private val LIST_DIVIDER_ATTRS = intArrayOf(R.attr.listDivider)
    }
}
