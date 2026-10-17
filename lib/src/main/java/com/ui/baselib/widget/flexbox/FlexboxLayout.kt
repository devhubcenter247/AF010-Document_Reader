package com.ui.baselib.widget.flexbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.core.content.withStyledAttributes
import androidx.core.view.isGone
import com.ui.baselib.R
import com.ui.baselib.widget.flexbox.FlexboxLayout.Companion.SHOW_DIVIDER_BEGINNING
import kotlin.math.max
import kotlin.math.roundToInt

open class FlexboxLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr), FlexContainer {
    @IntDef(
        flag = true,
        value = [SHOW_DIVIDER_NONE, SHOW_DIVIDER_BEGINNING, SHOW_DIVIDER_MIDDLE, SHOW_DIVIDER_END]
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class DividerMode
    @FlexDirection
    private var mFlexDirection: Int = FlexDirection.ROW
    @FlexWrap
    private var mFlexWrap: Int = FlexWrap.NOWRAP
    @JustifyContent
    private var mJustifyContent: Int = JustifyContent.FLEX_START
    @AlignItems
    private var mAlignItems: Int = AlignItems.FLEX_START
    @AlignContent
    private var mAlignContent: Int = AlignContent.FLEX_START
    private var mMaxLine: Int = NOT_SET
    private var mDividerDrawableHorizontal: Drawable? = null
    private var mDividerDrawableVertical: Drawable? = null
    private var mShowDividerHorizontal: Int = SHOW_DIVIDER_NONE
    private var mShowDividerVertical: Int = SHOW_DIVIDER_NONE
    private var mDividerHorizontalHeight: Int = 0
    private var mDividerVerticalWidth: Int = 0
    private var mReorderedIndices: IntArray? = null

    private var mOrderCache: SparseIntArray? = null
    private val mFlexboxHelper = FlexboxHelper(this)
    private var mFlexLines: MutableList<FlexLine> = ArrayList()

    private val mFlexLinesResult = FlexboxHelper.FlexLinesResult()

    init {
        context.withStyledAttributes(attrs, R.styleable.FlexboxLayout, defStyleAttr, 0) {
            mFlexDirection = getInt(R.styleable.FlexboxLayout_uiFlexDirection, FlexDirection.ROW)
            mFlexWrap = getInt(R.styleable.FlexboxLayout_uiFlexWrap, FlexWrap.NOWRAP)
            mJustifyContent =
                getInt(R.styleable.FlexboxLayout_uiJustifyContent, JustifyContent.FLEX_START)
            mAlignItems = getInt(R.styleable.FlexboxLayout_uiAlignItems, AlignItems.FLEX_START)
            mAlignContent = getInt(R.styleable.FlexboxLayout_uiAlignContent, AlignContent.FLEX_START)
            mMaxLine = getInt(R.styleable.FlexboxLayout_uiMaxLine, NOT_SET)
            val drawable = getDrawable(R.styleable.FlexboxLayout_uiDividerDrawable)
            if (drawable != null) {
                setDividerDrawableHorizontals(drawable)
                setDividerDrawableVerticals(drawable)
            }
            val drawableHorizontal =
                getDrawable(R.styleable.FlexboxLayout_uiDividerDrawableHorizontal)
            if (drawableHorizontal != null) {
                setDividerDrawableHorizontals(drawableHorizontal)
            }
            val drawableVertical = getDrawable(R.styleable.FlexboxLayout_uiDividerDrawableVertical)
            if (drawableVertical != null) {
                setDividerDrawableVerticals(drawableVertical)
            }
            val dividerMode = getInt(R.styleable.FlexboxLayout_uiShowDivider, SHOW_DIVIDER_NONE)
            if (dividerMode != SHOW_DIVIDER_NONE) {
                mShowDividerVertical = dividerMode
                mShowDividerHorizontal = dividerMode
            }
            val dividerModeVertical =
                getInt(R.styleable.FlexboxLayout_uiShowDividerVertical, SHOW_DIVIDER_NONE)
            if (dividerModeVertical != SHOW_DIVIDER_NONE) {
                mShowDividerVertical = dividerModeVertical
            }
            val dividerModeHorizontal =
                getInt(R.styleable.FlexboxLayout_uiShowDividerHorizontal, SHOW_DIVIDER_NONE)
            if (dividerModeHorizontal != SHOW_DIVIDER_NONE) {
                mShowDividerHorizontal = dividerModeHorizontal
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mOrderCache == null) {
            mOrderCache = SparseIntArray(childCount)
        }
        mOrderCache?.let { mOrderCache ->
            if (mFlexboxHelper.isOrderChangedFromLastMeasurement(mOrderCache)) {
                mReorderedIndices = mFlexboxHelper.createReorderedIndices(mOrderCache)
            }
        }



        when (mFlexDirection) {
            FlexDirection.ROW, FlexDirection.ROW_REVERSE -> measureHorizontal(
                widthMeasureSpec,
                heightMeasureSpec
            )
            FlexDirection.COLUMN, FlexDirection.COLUMN_REVERSE -> measureVertical(
                widthMeasureSpec,
                heightMeasureSpec
            )
            else -> throw IllegalStateException("Invalid value for the flex direction is set: $mFlexDirection")
        }
    }

    override val flexItemCount: Int
        get() = childCount

    override fun getFlexItemAt(index: Int): View? = getChildAt(index)
    fun getReorderedChildAt(index: Int): View? {
        val reorderedIndices = mReorderedIndices ?: return null
        if (index < 0 || index >= reorderedIndices.size) {
            return null
        }
        return getChildAt(reorderedIndices[index])
    }

    override fun getReorderedFlexItemAt(index: Int): View? = getReorderedChildAt(index)

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (mOrderCache == null) {
            mOrderCache = SparseIntArray(childCount)
        }




        mReorderedIndices =
            mFlexboxHelper.createReorderedIndices(child, index, params, mOrderCache!!)
        super.addView(child, index, params)
    }
    private fun measureHorizontal(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mFlexLines.clear()

        mFlexLinesResult.reset()
        mFlexboxHelper.calculateHorizontalFlexLines(
            mFlexLinesResult,
            widthMeasureSpec,
            heightMeasureSpec
        )
        mFlexLines = mFlexLinesResult.mFlexLines

        mFlexboxHelper.determineMainSize(widthMeasureSpec, heightMeasureSpec)


        if (mAlignItems == AlignItems.BASELINE) {
            for (flexLine in mFlexLines) {
                var largestHeightInLine = Int.MIN_VALUE
                for (i in 0 until flexLine.mItemCount) {
                    val viewIndex = flexLine.mFirstIndex + i
                    val child = getReorderedChildAt(viewIndex)
                    if (child == null || child.isGone) {
                        continue
                    }
                    val lp = child.layoutParams as LayoutParams
                    if (mFlexWrap != FlexWrap.WRAP_REVERSE) {
                        var marginTop = flexLine.mMaxBaseline - child.baseline
                        marginTop = max(marginTop, lp.topMargin)
                        largestHeightInLine = max(
                            largestHeightInLine,
                            child.measuredHeight + marginTop + lp.bottomMargin
                        )
                    } else {
                        var marginBottom =
                            flexLine.mMaxBaseline - child.measuredHeight + child.baseline
                        marginBottom = max(marginBottom, lp.bottomMargin)
                        largestHeightInLine = max(
                            largestHeightInLine,
                            child.measuredHeight + lp.topMargin + marginBottom
                        )
                    }
                }
                flexLine.mCrossSize = largestHeightInLine
            }
        }

        mFlexboxHelper.determineCrossSize(
            widthMeasureSpec, heightMeasureSpec,
            paddingTop + paddingBottom
        )


        mFlexboxHelper.stretchViews()
        setMeasuredDimensionForFlex(
            mFlexDirection, widthMeasureSpec, heightMeasureSpec,
            mFlexLinesResult.mChildState
        )
    }
    private fun measureVertical(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        mFlexLines.clear()
        mFlexLinesResult.reset()
        mFlexboxHelper.calculateVerticalFlexLines(
            mFlexLinesResult,
            widthMeasureSpec,
            heightMeasureSpec
        )
        mFlexLines = mFlexLinesResult.mFlexLines

        mFlexboxHelper.determineMainSize(widthMeasureSpec, heightMeasureSpec)
        mFlexboxHelper.determineCrossSize(
            widthMeasureSpec, heightMeasureSpec,
            paddingLeft + paddingRight
        )


        mFlexboxHelper.stretchViews()
        setMeasuredDimensionForFlex(
            mFlexDirection, widthMeasureSpec, heightMeasureSpec,
            mFlexLinesResult.mChildState
        )
    }

    private fun setMeasuredDimensionForFlex(
        @FlexDirection flexDirection: Int,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        childState: Int
    ) {
        var mutableChildState = childState
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        var heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var (calculatedMaxHeight, calculatedMaxWidth) = when (flexDirection) {
            FlexDirection.ROW, FlexDirection.ROW_REVERSE -> {
                Pair(
                    sumOfCrossSize + paddingTop + paddingBottom,
                    largestMainSize
                )
            }
            FlexDirection.COLUMN, FlexDirection.COLUMN_REVERSE -> {
                Pair(
                    largestMainSize,
                    sumOfCrossSize + paddingLeft + paddingRight
                )
            }
            else -> throw IllegalArgumentException("Invalid flex direction: $flexDirection")
        }
        calculatedMaxWidth = max(calculatedMaxWidth, suggestedMinimumWidth)
        calculatedMaxHeight = max(calculatedMaxHeight, suggestedMinimumHeight)
        val widthSizeAndState = when (widthMode) {
            MeasureSpec.EXACTLY -> {
                if (widthSize < calculatedMaxWidth) {
                    mutableChildState =
                        combineMeasuredStates(mutableChildState, MEASURED_STATE_TOO_SMALL)
                }
                resolveSizeAndState(widthSize, widthMeasureSpec, mutableChildState)
            }
            MeasureSpec.AT_MOST -> {
                if (widthSize < calculatedMaxWidth) {
                    mutableChildState =
                        combineMeasuredStates(mutableChildState, MEASURED_STATE_TOO_SMALL)
                } else {
                    widthSize = calculatedMaxWidth
                }
                resolveSizeAndState(widthSize, widthMeasureSpec, mutableChildState)
            }
            MeasureSpec.UNSPECIFIED -> {
                resolveSizeAndState(calculatedMaxWidth, widthMeasureSpec, mutableChildState)
            }
            else -> throw IllegalStateException("Unknown width mode is set: $widthMode")
        }
        val heightSizeAndState = when (heightMode) {
            MeasureSpec.EXACTLY -> {
                if (heightSize < calculatedMaxHeight) {
                    mutableChildState = combineMeasuredStates(
                        mutableChildState,
                        MEASURED_STATE_TOO_SMALL shr MEASURED_HEIGHT_STATE_SHIFT
                    )
                }
                resolveSizeAndState(heightSize, heightMeasureSpec, mutableChildState)
            }
            MeasureSpec.AT_MOST -> {
                if (heightSize < calculatedMaxHeight) {
                    mutableChildState = combineMeasuredStates(
                        mutableChildState,
                        MEASURED_STATE_TOO_SMALL shr MEASURED_HEIGHT_STATE_SHIFT
                    )
                } else {
                    heightSize = calculatedMaxHeight
                }
                resolveSizeAndState(heightSize, heightMeasureSpec, mutableChildState)
            }
            MeasureSpec.UNSPECIFIED -> {
                resolveSizeAndState(calculatedMaxHeight, heightMeasureSpec, mutableChildState)
            }
            else -> throw IllegalStateException("Unknown height mode is set: $heightMode")
        }
        setMeasuredDimension(widthSizeAndState, heightSizeAndState)
    }

    override val largestMainSize: Int
        get() {
            var largestSize = Int.MIN_VALUE
            for (flexLine in mFlexLines) {
                largestSize = max(largestSize, flexLine.mMainSize)
            }
            return largestSize
        }
    override val sumOfCrossSize: Int
        get() {
            var sum = 0
            for (i in mFlexLines.indices) {
                val flexLine = mFlexLines[i]


                if (hasDividerBeforeFlexLine(i)) {
                    sum += if (isMainAxisDirectionHorizontal()) {
                        mDividerHorizontalHeight
                    } else {
                        mDividerVerticalWidth
                    }
                }


                if (hasEndDividerAfterFlexLine(i)) {
                    sum += if (isMainAxisDirectionHorizontal()) {
                        mDividerHorizontalHeight
                    } else {
                        mDividerVerticalWidth
                    }
                }
                sum += flexLine.mCrossSize
            }
            return sum
        }

    override fun isMainAxisDirectionHorizontal(): Boolean {
        return mFlexDirection == FlexDirection.ROW || mFlexDirection == FlexDirection.ROW_REVERSE
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val layoutDirection = getLayoutDirection()
        var isRtl: Boolean
        when (mFlexDirection) {
            FlexDirection.ROW -> {
                isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                layoutHorizontal(isRtl, left, top, right, bottom)
            }
            FlexDirection.ROW_REVERSE -> {
                isRtl = layoutDirection != LAYOUT_DIRECTION_RTL
                layoutHorizontal(isRtl, left, top, right, bottom)
            }
            FlexDirection.COLUMN -> {
                isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    isRtl = !isRtl
                }
                layoutVertical(isRtl, false, left, top, right, bottom)
            }
            FlexDirection.COLUMN_REVERSE -> {
                isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    isRtl = !isRtl
                }
                layoutVertical(isRtl, true, left, top, right, bottom)
            }
            else -> throw IllegalStateException("Invalid flex direction is set: $mFlexDirection")
        }
    }

    private fun layoutHorizontal(isRtl: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        var childLeft: Float
        val height = bottom - top
        val width = right - left
        var childBottom = height - paddingBottom
        var childTop = paddingTop
        var childRight: Float

        for (i in mFlexLines.indices) {
            val flexLine = mFlexLines[i]
            if (hasDividerBeforeFlexLine(i)) {
                childBottom -= mDividerHorizontalHeight
                childTop += mDividerHorizontalHeight
            }
            var spaceBetweenItem = 0f
            when (mJustifyContent) {
                JustifyContent.FLEX_START -> {
                    childLeft = paddingLeft.toFloat()
                    childRight = (width - paddingRight).toFloat()
                }
                JustifyContent.FLEX_END -> {
                    childLeft = (width - flexLine.mMainSize + paddingRight).toFloat()
                    childRight = (flexLine.mMainSize - paddingLeft).toFloat()
                }
                JustifyContent.CENTER -> {
                    childLeft = paddingLeft + (width - flexLine.mMainSize) / 2f
                    childRight = width - paddingRight - (width - flexLine.mMainSize) / 2f
                }
                JustifyContent.SPACE_AROUND -> {
                    val visibleCount = flexLine.itemCountNotGone
                    if (visibleCount != 0) {
                        spaceBetweenItem = (width - flexLine.mMainSize) / visibleCount.toFloat()
                    }
                    childLeft = paddingLeft + spaceBetweenItem / 2f
                    childRight = width - paddingRight - spaceBetweenItem / 2f
                }
                JustifyContent.SPACE_BETWEEN -> {
                    childLeft = paddingLeft.toFloat()
                    val visibleCount = flexLine.itemCountNotGone
                    val denominator = if (visibleCount != 1) visibleCount - 1 else 1
                    spaceBetweenItem = ((width - flexLine.mMainSize) / denominator).toFloat()
                    childRight = (width - paddingRight).toFloat()
                }
                JustifyContent.SPACE_EVENLY -> {
                    val visibleCount = flexLine.itemCountNotGone
                    if (visibleCount != 0) {
                        spaceBetweenItem =
                            (width - flexLine.mMainSize) / (visibleCount + 1).toFloat()
                    }
                    childLeft = paddingLeft + spaceBetweenItem
                    childRight = width - paddingRight - spaceBetweenItem
                }
                else -> throw IllegalStateException("Invalid justifyContent is set: $mJustifyContent")
            }
            spaceBetweenItem = max(spaceBetweenItem, 0f)

            for (j in 0 until flexLine.mItemCount) {
                val index = flexLine.mFirstIndex + j
                val child = getReorderedChildAt(index)
                if (child == null || child.isGone) {
                    continue
                }
                val lp = child.layoutParams as LayoutParams
                childLeft += lp.leftMargin
                childRight -= lp.rightMargin
                var beforeDividerLength = 0
                var endDividerLength = 0
                if (hasDividerBeforeChildAtAlongMainAxis(index, j)) {
                    beforeDividerLength = mDividerVerticalWidth
                    childLeft += beforeDividerLength
                    childRight -= beforeDividerLength
                }
                if (j == flexLine.mItemCount - 1 && (mShowDividerVertical and SHOW_DIVIDER_END) > 0) {
                    endDividerLength = mDividerVerticalWidth
                }

                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    if (isRtl) {
                        mFlexboxHelper.layoutSingleChildHorizontal(
                            child, flexLine,
                            childRight.roundToInt() - child.measuredWidth,
                            childBottom - child.measuredHeight, childRight.roundToInt(),
                            childBottom
                        )
                    } else {
                        mFlexboxHelper.layoutSingleChildHorizontal(
                            child, flexLine,
                            childLeft.roundToInt(), childBottom - child.measuredHeight,
                            childLeft.roundToInt() + child.measuredWidth, childBottom
                        )
                    }
                } else {
                    if (isRtl) {
                        mFlexboxHelper.layoutSingleChildHorizontal(
                            child, flexLine,
                            childRight.roundToInt() - child.measuredWidth,
                            childTop, childRight.roundToInt(),
                            childTop + child.measuredHeight
                        )
                    } else {
                        mFlexboxHelper.layoutSingleChildHorizontal(
                            child, flexLine,
                            childLeft.roundToInt(), childTop,
                            childLeft.roundToInt() + child.measuredWidth,
                            childTop + child.measuredHeight
                        )
                    }
                }
                childLeft += child.measuredWidth + spaceBetweenItem + lp.rightMargin
                childRight -= child.measuredWidth + spaceBetweenItem + lp.leftMargin

                if (isRtl) {
                    flexLine.updatePositionFromView(
                        child, /*leftDecoration*/endDividerLength, 0,
                        /*rightDecoration*/ beforeDividerLength, 0
                    )
                } else {
                    flexLine.updatePositionFromView(
                        child, /*leftDecoration*/beforeDividerLength, 0,
                        /*rightDecoration*/ endDividerLength, 0
                    )
                }
            }
            childTop += flexLine.mCrossSize
            childBottom -= flexLine.mCrossSize
        }
    }

    private fun layoutVertical(
        isRtl: Boolean,
        fromBottomToTop: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom
        val paddingRight = paddingRight
        var childLeft = paddingLeft
        val width = right - left
        val height = bottom - top
        var childRight = width - paddingRight
        var childTop: Float
        var childBottom: Float

        for (i in mFlexLines.indices) {
            val flexLine = mFlexLines[i]
            if (hasDividerBeforeFlexLine(i)) {
                childLeft += mDividerVerticalWidth
                childRight -= mDividerVerticalWidth
            }
            var spaceBetweenItem = 0f
            when (mJustifyContent) {
                JustifyContent.FLEX_START -> {
                    childTop = paddingTop.toFloat()
                    childBottom = (height - paddingBottom).toFloat()
                }
                JustifyContent.FLEX_END -> {
                    childTop = (height - flexLine.mMainSize + paddingBottom).toFloat()
                    childBottom = (flexLine.mMainSize - paddingTop).toFloat()
                }
                JustifyContent.CENTER -> {
                    childTop = paddingTop + (height - flexLine.mMainSize) / 2f
                    childBottom = height - paddingBottom - (height - flexLine.mMainSize) / 2f
                }
                JustifyContent.SPACE_AROUND -> {
                    val visibleCount = flexLine.itemCountNotGone
                    if (visibleCount != 0) {
                        spaceBetweenItem = (height - flexLine.mMainSize) / visibleCount.toFloat()
                    }
                    childTop = paddingTop + spaceBetweenItem / 2f
                    childBottom = height - paddingBottom - spaceBetweenItem / 2f
                }
                JustifyContent.SPACE_BETWEEN -> {
                    childTop = paddingTop.toFloat()
                    val visibleCount = flexLine.itemCountNotGone
                    val denominator = if (visibleCount != 1) visibleCount - 1 else 1
                    spaceBetweenItem = ((height - flexLine.mMainSize) / denominator).toFloat()
                    childBottom = (height - paddingBottom).toFloat()
                }
                JustifyContent.SPACE_EVENLY -> {
                    val visibleCount = flexLine.itemCountNotGone
                    if (visibleCount != 0) {
                        spaceBetweenItem =
                            (height - flexLine.mMainSize) / (visibleCount + 1).toFloat()
                    }
                    childTop = paddingTop + spaceBetweenItem
                    childBottom = height - paddingBottom - spaceBetweenItem
                }
                else -> throw IllegalStateException("Invalid justifyContent is set: $mJustifyContent")
            }
            spaceBetweenItem = max(spaceBetweenItem, 0f)

            for (j in 0 until flexLine.mItemCount) {
                val index = flexLine.mFirstIndex + j
                val child = getReorderedChildAt(index)
                if (child == null || child.isGone) {
                    continue
                }
                val lp = child.layoutParams as LayoutParams
                childTop += lp.topMargin
                childBottom -= lp.bottomMargin
                var beforeDividerLength = 0
                var endDividerLength = 0
                if (hasDividerBeforeChildAtAlongMainAxis(index, j)) {
                    beforeDividerLength = mDividerHorizontalHeight
                    childTop += beforeDividerLength
                    childBottom -= beforeDividerLength
                }
                if (j == flexLine.mItemCount - 1 && (mShowDividerHorizontal and SHOW_DIVIDER_END) > 0) {
                    endDividerLength = mDividerHorizontalHeight
                }
                if (isRtl) {
                    if (fromBottomToTop) {
                        mFlexboxHelper.layoutSingleChildVertical(
                            child, flexLine, true,
                            childRight - child.measuredWidth,
                            childBottom.roundToInt() - child.measuredHeight, childRight,
                            childBottom.roundToInt()
                        )
                    } else {
                        mFlexboxHelper.layoutSingleChildVertical(
                            child, flexLine, true,
                            childRight - child.measuredWidth, childTop.roundToInt(),
                            childRight, childTop.roundToInt() + child.measuredHeight
                        )
                    }
                } else {
                    if (fromBottomToTop) {
                        mFlexboxHelper.layoutSingleChildVertical(
                            child, flexLine, false,
                            childLeft, childBottom.roundToInt() - child.measuredHeight,
                            childLeft + child.measuredWidth, childBottom.roundToInt()
                        )
                    } else {
                        mFlexboxHelper.layoutSingleChildVertical(
                            child, flexLine, false,
                            childLeft, childTop.roundToInt(),
                            childLeft + child.measuredWidth,
                            childTop.roundToInt() + child.measuredHeight
                        )
                    }
                }
                childTop += child.measuredHeight + spaceBetweenItem + lp.bottomMargin
                childBottom -= child.measuredHeight + spaceBetweenItem + lp.topMargin

                if (fromBottomToTop) {
                    flexLine.updatePositionFromView(
                        child, 0, /*topDecoration*/endDividerLength, 0,
                        /*bottomDecoration*/ beforeDividerLength
                    )
                } else {
                    flexLine.updatePositionFromView(
                        child, 0, /*topDecoration*/beforeDividerLength,
                        0, /*bottomDecoration*/endDividerLength
                    )
                }
            }
            childLeft += flexLine.mCrossSize
            childRight -= flexLine.mCrossSize
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (mDividerDrawableVertical == null && mDividerDrawableHorizontal == null) {
            return
        }
        if (mShowDividerHorizontal == SHOW_DIVIDER_NONE && mShowDividerVertical == SHOW_DIVIDER_NONE) {
            return
        }
        val layoutDirection = getLayoutDirection()
        var isRtl: Boolean
        var fromBottomToTop = false
        when (mFlexDirection) {
            FlexDirection.ROW -> {
                isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    fromBottomToTop = true
                }
                drawDividersHorizontal(canvas, isRtl, fromBottomToTop)
            }
            FlexDirection.ROW_REVERSE -> {
                isRtl = layoutDirection != LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    fromBottomToTop = true
                }
                drawDividersHorizontal(canvas, isRtl, fromBottomToTop)
            }
            FlexDirection.COLUMN -> {
                isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    isRtl = !isRtl
                }
                drawDividersVertical(canvas, isRtl, false)
            }
            FlexDirection.COLUMN_REVERSE -> {
                isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) {
                    isRtl = !isRtl
                }
                drawDividersVertical(canvas, isRtl, true)
            }
        }
    }
    private fun drawDividersHorizontal(canvas: Canvas, isRtl: Boolean, fromBottomToTop: Boolean) {
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val horizontalDividerLength = max(0, width - paddingRight - paddingLeft)
        for (i in mFlexLines.indices) {
            val flexLine = mFlexLines[i]
            for (j in 0 until flexLine.mItemCount) {
                val viewIndex = flexLine.mFirstIndex + j
                val view = getReorderedChildAt(viewIndex)
                if (view == null || view.isGone) {
                    continue
                }
                val lp = view.layoutParams as LayoutParams


                if (hasDividerBeforeChildAtAlongMainAxis(viewIndex, j)) {
                    val dividerLeft = if (isRtl) {
                        view.right + lp.rightMargin
                    } else {
                        view.left - lp.leftMargin - mDividerVerticalWidth
                    }

                    drawVerticalDivider(canvas, dividerLeft, flexLine.mTop, flexLine.mCrossSize)
                }


                if (j == flexLine.mItemCount - 1) {
                    if ((mShowDividerVertical and SHOW_DIVIDER_END) > 0) {
                        val dividerLeft = if (isRtl) {
                            view.left - lp.leftMargin - mDividerVerticalWidth
                        } else {
                            view.right + lp.rightMargin
                        }

                        drawVerticalDivider(canvas, dividerLeft, flexLine.mTop, flexLine.mCrossSize)
                    }
                }
            }


            if (hasDividerBeforeFlexLine(i)) {
                val horizontalDividerTop = if (fromBottomToTop) {
                    flexLine.mBottom
                } else {
                    flexLine.mTop - mDividerHorizontalHeight
                }
                drawHorizontalDivider(
                    canvas,
                    paddingLeft,
                    horizontalDividerTop,
                    horizontalDividerLength
                )
            }

            if (hasEndDividerAfterFlexLine(i)) {
                if ((mShowDividerHorizontal and SHOW_DIVIDER_END) > 0) {
                    val horizontalDividerTop = if (fromBottomToTop) {
                        flexLine.mTop - mDividerHorizontalHeight
                    } else {
                        flexLine.mBottom
                    }
                    drawHorizontalDivider(
                        canvas,
                        paddingLeft,
                        horizontalDividerTop,
                        horizontalDividerLength
                    )
                }
            }
        }
    }

    private fun drawDividersVertical(canvas: Canvas, isRtl: Boolean, fromBottomToTop: Boolean) {
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom
        val verticalDividerLength = max(0, height - paddingBottom - paddingTop)
        for (i in mFlexLines.indices) {
            val flexLine = mFlexLines[i]


            for (j in 0 until flexLine.mItemCount) {
                val viewIndex = flexLine.mFirstIndex + j
                val view = getReorderedChildAt(viewIndex)
                if (view == null || view.isGone) {
                    continue
                }
                val lp = view.layoutParams as LayoutParams


                if (hasDividerBeforeChildAtAlongMainAxis(viewIndex, j)) {
                    val dividerTop = if (fromBottomToTop) {
                        view.bottom + lp.bottomMargin
                    } else {
                        view.top - lp.topMargin - mDividerHorizontalHeight
                    }

                    drawHorizontalDivider(canvas, flexLine.mLeft, dividerTop, flexLine.mCrossSize)
                }


                if (j == flexLine.mItemCount - 1) {
                    if ((mShowDividerHorizontal and SHOW_DIVIDER_END) > 0) {
                        val dividerTop = if (fromBottomToTop) {
                            view.top - lp.topMargin - mDividerHorizontalHeight
                        } else {
                            view.bottom + lp.bottomMargin
                        }

                        drawHorizontalDivider(
                            canvas,
                            flexLine.mLeft,
                            dividerTop,
                            flexLine.mCrossSize
                        )
                    }
                }
            }


            if (hasDividerBeforeFlexLine(i)) {
                val verticalDividerLeft = if (isRtl) {
                    flexLine.mRight
                } else {
                    flexLine.mLeft - mDividerVerticalWidth
                }
                drawVerticalDivider(canvas, verticalDividerLeft, paddingTop, verticalDividerLength)
            }
            if (hasEndDividerAfterFlexLine(i)) {
                if ((mShowDividerVertical and SHOW_DIVIDER_END) > 0) {
                    val verticalDividerLeft = if (isRtl) {
                        flexLine.mLeft - mDividerVerticalWidth
                    } else {
                        flexLine.mRight
                    }
                    drawVerticalDivider(
                        canvas,
                        verticalDividerLeft,
                        paddingTop,
                        verticalDividerLength
                    )
                }
            }
        }
    }

    private fun drawVerticalDivider(canvas: Canvas, left: Int, top: Int, length: Int) {
        val drawable = mDividerDrawableVertical ?: return
        drawable.setBounds(left, top, left + mDividerVerticalWidth, top + length)
        drawable.draw(canvas)
    }

    private fun drawHorizontalDivider(canvas: Canvas, left: Int, top: Int, length: Int) {
        val drawable = mDividerDrawableHorizontal ?: return
        drawable.setBounds(left, top, left + length, top + mDividerHorizontalHeight)
        drawable.draw(canvas)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams
    }

    override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return when (lp) {
            is LayoutParams -> LayoutParams(lp)
            is MarginLayoutParams -> LayoutParams(lp)
            else -> LayoutParams(lp)
        }
    }
    @FlexDirection
    override var flexDirection: Int
        get() = mFlexDirection
        set(@FlexDirection flexDirection) {
            if (mFlexDirection != flexDirection) {
                mFlexDirection = flexDirection
                requestLayout()
            }
        }
    @FlexWrap
    override var flexWrap: Int
        get() = mFlexWrap
        set(@FlexWrap flexWrap) {
            if (mFlexWrap != flexWrap) {
                mFlexWrap = flexWrap
                requestLayout()
            }
        }
    @JustifyContent
    override var justifyContent: Int
        get() = mJustifyContent
        set(@JustifyContent justifyContent) {
            if (mJustifyContent != justifyContent) {
                mJustifyContent = justifyContent
                requestLayout()
            }
        }
    @AlignItems
    override var alignItems: Int
        get() = mAlignItems
        set(@AlignItems alignItems) {
            if (mAlignItems != alignItems) {
                mAlignItems = alignItems
                requestLayout()
            }
        }
    @AlignContent
    override var alignContent: Int
        get() = mAlignContent
        set(@AlignContent alignContent) {
            if (mAlignContent != alignContent) {
                mAlignContent = alignContent
                requestLayout()
            }
        }
    override var maxLine: Int
        get() = mMaxLine
        set(maxLine) {
            if (mMaxLine != maxLine) {
                mMaxLine = maxLine
                requestLayout()
            }
        }
    /**
     * @return the flex lines composing this flex container. This method returns a copy of the
     * original list excluding a dummy flex line (flex line that doesn't have any flex items in it
     * but used for the alignment along the cross axis).
     * Thus any changes of the returned list are not reflected to the original list.
     */
    override val flexLines: List<FlexLine>
        get() {
            val result = ArrayList<FlexLine>(mFlexLines.size)
            for (flexLine in mFlexLines) {
                if (flexLine.itemCountNotGone == 0) {
                    continue
                }
                result.add(flexLine)
            }
            return result
        }

    override fun getDecorationLengthMainAxis(view: View, index: Int, indexInFlexLine: Int): Int {
        var decorationLength = 0
        if (isMainAxisDirectionHorizontal()) {
            if (hasDividerBeforeChildAtAlongMainAxis(index, indexInFlexLine)) {
                decorationLength += mDividerVerticalWidth
            }
            if ((mShowDividerVertical and SHOW_DIVIDER_END) > 0) {
                decorationLength += mDividerVerticalWidth
            }
        } else {
            if (hasDividerBeforeChildAtAlongMainAxis(index, indexInFlexLine)) {
                decorationLength += mDividerHorizontalHeight
            }
            if ((mShowDividerHorizontal and SHOW_DIVIDER_END) > 0) {
                decorationLength += mDividerHorizontalHeight
            }
        }
        return decorationLength
    }

    override fun getDecorationLengthCrossAxis(view: View): Int {
        return 0
    }

    override fun onNewFlexLineAdded(flexLine: FlexLine) {
        if (isMainAxisDirectionHorizontal()) {
            if ((mShowDividerVertical and SHOW_DIVIDER_END) > 0) {
                flexLine.mMainSize += mDividerVerticalWidth
                flexLine.mDividerLengthInMainSize += mDividerVerticalWidth
            }
        } else {
            if ((mShowDividerHorizontal and SHOW_DIVIDER_END) > 0) {
                flexLine.mMainSize += mDividerHorizontalHeight
                flexLine.mDividerLengthInMainSize += mDividerHorizontalHeight
            }
        }
    }

    override fun getChildWidthMeasureSpec(widthSpec: Int, padding: Int, childDimension: Int): Int {
        return getChildMeasureSpec(widthSpec, padding, childDimension)
    }

    override fun getChildHeightMeasureSpec(
        heightSpec: Int,
        padding: Int,
        childDimension: Int
    ): Int {
        return getChildMeasureSpec(heightSpec, padding, childDimension)
    }

    override fun onNewFlexItemAdded(
        view: View,
        index: Int,
        indexInFlexLine: Int,
        flexLine: FlexLine
    ) {
        if (hasDividerBeforeChildAtAlongMainAxis(index, indexInFlexLine)) {
            if (isMainAxisDirectionHorizontal()) {
                flexLine.mMainSize += mDividerVerticalWidth
                flexLine.mDividerLengthInMainSize += mDividerVerticalWidth
            } else {
                flexLine.mMainSize += mDividerHorizontalHeight
                flexLine.mDividerLengthInMainSize += mDividerHorizontalHeight
            }
        }
    }

    override fun setFlexLines(flexLines: List<FlexLine>) {
        mFlexLines = flexLines.toMutableList()
    }

    override val flexLinesInternal: List<FlexLine>
        get() = mFlexLines

    override fun updateViewCache(position: Int, view: View) {
    }
    /**
     * @return the horizontal divider drawable that will divide each item.
     * @see setDividerDrawable
     * @see setDividerDrawableHorizontals
     */
    var dividerDrawableHorizontal: Drawable?
        get() = mDividerDrawableHorizontal
        set(divider) {
            setDividerDrawableHorizontals(divider)
        }
    /**
     * @return the vertical divider drawable that will divide each item.
     * @see setDividerDrawable
     * @see setDividerDrawableVerticals
     */
    var dividerDrawableVertical: Drawable?
        get() = mDividerDrawableVertical
        set(divider) = setDividerDrawableVerticals(divider)
    /**
     * Set a drawable to be used as a divider between items. The drawable is used for both
     * horizontal and vertical dividers.
     *
     * @param divider Drawable that will divide each item for both horizontally and vertically.
     * @see setShowDivider
     */
    fun setDividerDrawable(divider: Drawable?) {
        setDividerDrawableHorizontals(divider)
        setDividerDrawableVerticals(divider)
    }
    /**
     * Set a drawable to be used as a horizontal divider between items.
     *
     * @param divider Drawable that will divide each item.
     * @see setDividerDrawable
     * @see setShowDivider
     * @see setShowDividerHorizontals
     */
    fun setDividerDrawableHorizontals(divider: Drawable?) {
        if (divider === mDividerDrawableHorizontal) {
            return
        }
        mDividerDrawableHorizontal = divider
        mDividerHorizontalHeight = divider?.intrinsicHeight ?: 0
        setWillNotDrawFlag()
        requestLayout()
    }
    /**
     * Set a drawable to be used as a vertical divider between items.
     *
     * @param divider Drawable that will divide each item.
     * @see setDividerDrawable
     * @see setShowDivider
     * @see setShowDividerVerticals
     */
    fun setDividerDrawableVerticals(divider: Drawable?) {
        if (divider === mDividerDrawableVertical) {
            return
        }
        mDividerDrawableVertical = divider
        mDividerVerticalWidth = divider?.intrinsicWidth ?: 0
        setWillNotDrawFlag()
        requestLayout()
    }
    @DividerMode
    var showDividerVertical: Int
        get() = mShowDividerVertical
        set(@DividerMode dividerMode) = setShowDividerVerticals(dividerMode)
    @DividerMode
    var showDividerHorizontal: Int
        get() = mShowDividerHorizontal
        set(@DividerMode dividerMode) = setShowDividerHorizontals(dividerMode)
    /**
     * Set how dividers should be shown between items in this layout. This method sets the
     * divider mode for both horizontally and vertically.
     *
     * @param dividerMode One or more of [SHOW_DIVIDER_BEGINNING],
     *                    [SHOW_DIVIDER_MIDDLE], or [SHOW_DIVIDER_END],
     *                    or [SHOW_DIVIDER_NONE] to show no dividers.
     * @see setShowDividerVerticals
     * @see setShowDividerHorizontals
     */
    fun setShowDivider(@DividerMode dividerMode: Int) {
        setShowDividerVerticals(dividerMode)
        setShowDividerHorizontals(dividerMode)
    }
    /**
     * Set how vertical dividers should be shown between items in this layout
     *
     * @param dividerMode One or more of [SHOW_DIVIDER_BEGINNING],
     *                    [SHOW_DIVIDER_MIDDLE], or [SHOW_DIVIDER_END],
     *                    or [SHOW_DIVIDER_NONE] to show no dividers.
     * @see setShowDivider
     */
    fun setShowDividerVerticals(@DividerMode dividerMode: Int) {
        if (dividerMode != mShowDividerVertical) {
            mShowDividerVertical = dividerMode
            requestLayout()
        }
    }
    /**
     * Set how horizontal dividers should be shown between items in this layout.
     *
     * @param dividerMode One or more of [SHOW_DIVIDER_BEGINNING],
     *                    [SHOW_DIVIDER_MIDDLE], or [SHOW_DIVIDER_END],
     *                    or [SHOW_DIVIDER_NONE] to show no dividers.
     * @see setShowDivider
     */
    fun setShowDividerHorizontals(@DividerMode dividerMode: Int) {
        if (dividerMode != mShowDividerHorizontal) {
            mShowDividerHorizontal = dividerMode
            requestLayout()
        }
    }

    private fun setWillNotDrawFlag() {
        setWillNotDraw(mDividerDrawableHorizontal == null && mDividerDrawableVertical == null)
    }
    /**
     * Check if a divider is needed before the view whose indices are passed as arguments.
     *
     * @param index           the absolute index of the view to be judged
     * @param indexInFlexLine the relative index in the flex line where the view
     *                        belongs
     * @return `true` if a divider is needed, `false` otherwise
     */
    private fun hasDividerBeforeChildAtAlongMainAxis(index: Int, indexInFlexLine: Int): Boolean {
        return if (allViewsAreGoneBefore(index, indexInFlexLine)) {
            if (isMainAxisDirectionHorizontal()) {
                (mShowDividerVertical and SHOW_DIVIDER_BEGINNING) != 0
            } else {
                (mShowDividerHorizontal and SHOW_DIVIDER_BEGINNING) != 0
            }
        } else {
            if (isMainAxisDirectionHorizontal()) {
                (mShowDividerVertical and SHOW_DIVIDER_MIDDLE) != 0
            } else {
                (mShowDividerHorizontal and SHOW_DIVIDER_MIDDLE) != 0
            }
        }
    }

    private fun allViewsAreGoneBefore(index: Int, indexInFlexLine: Int): Boolean {
        for (i in 1..indexInFlexLine) {
            val view = getReorderedChildAt(index - i)
            if (view != null && view.visibility != GONE) {
                return false
            }
        }
        return true
    }
    /**
     * Check if a divider is needed before the flex line whose index is passed as an argument.
     *
     * @param flexLineIndex the index of the flex line to be checked
     * @return `true` if a divider is needed, `false` otherwise
     */
    private fun hasDividerBeforeFlexLine(flexLineIndex: Int): Boolean {
        if (flexLineIndex < 0 || flexLineIndex >= mFlexLines.size) {
            return false
        }
        return if (allFlexLinesAreDummyBefore(flexLineIndex)) {
            if (isMainAxisDirectionHorizontal()) {
                (mShowDividerHorizontal and SHOW_DIVIDER_BEGINNING) != 0
            } else {
                (mShowDividerVertical and SHOW_DIVIDER_BEGINNING) != 0
            }
        } else {
            if (isMainAxisDirectionHorizontal()) {
                (mShowDividerHorizontal and SHOW_DIVIDER_MIDDLE) != 0
            } else {
                (mShowDividerVertical and SHOW_DIVIDER_MIDDLE) != 0
            }
        }
    }

    private fun allFlexLinesAreDummyBefore(flexLineIndex: Int): Boolean {
        for (i in 0 until flexLineIndex) {
            if (mFlexLines[i].itemCountNotGone > 0) {
                return false
            }
        }
        return true
    }
    /**
     * Check if a end divider is needed after the flex line whose index is passed as an argument.
     *
     * @param flexLineIndex the index of the flex line to be checked
     * @return `true` if a divider is needed, `false` otherwise
     */
    private fun hasEndDividerAfterFlexLine(flexLineIndex: Int): Boolean {
        if (flexLineIndex < 0 || flexLineIndex >= mFlexLines.size) {
            return false
        }

        for (i in flexLineIndex + 1 until mFlexLines.size) {
            if (mFlexLines[i].itemCountNotGone > 0) {
                return false
            }
        }
        return if (isMainAxisDirectionHorizontal()) {
            (mShowDividerHorizontal and SHOW_DIVIDER_END) != 0
        } else {
            (mShowDividerVertical and SHOW_DIVIDER_END) != 0
        }
    }
    /**
     * Per child parameters for children views of the [FlexboxLayout].
     *
     * Note that some parent fields (which are not primitive nor a class implements
     * [Parcelable]) are not included as the stored/restored fields after this class
     * is serialized/de-serialized as an [Parcelable].
     */
    @Suppress("PROPERTY_HIDES_JAVA_FIELD")
    class LayoutParams : MarginLayoutParams, FlexItem {
        /** @see FlexItem.order */
        private var mOrder = FlexItem.ORDER_DEFAULT
        /** @see FlexItem.flexGrow */
        private var mFlexGrow = FlexItem.FLEX_GROW_DEFAULT
        /** @see FlexItem.flexShrink */
        private var mFlexShrink = FlexItem.FLEX_SHRINK_DEFAULT
        /** @see FlexItem.alignSelf */
        private var mAlignSelf = AlignSelf.AUTO
        /** @see FlexItem.flexBasisPercent */
        private var mFlexBasisPercent = FlexItem.FLEX_BASIS_PERCENT_DEFAULT
        /** @see FlexItem.minWidth */
        private var mMinWidth = NOT_SET
        /** @see FlexItem.minHeight */
        private var mMinHeight = NOT_SET
        /** @see FlexItem.maxWidth */
        private var mMaxWidth = FlexItem.MAX_SIZE
        /** @see FlexItem.maxHeight */
        private var mMaxHeight = FlexItem.MAX_SIZE
        /** @see FlexItem.isWrapBefore */
        private var mWrapBefore = false

        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
            context.withStyledAttributes(attrs, R.styleable.FlexboxLayout_Layout) {
                mOrder =
                    getInt(R.styleable.FlexboxLayout_Layout_layout_uiOrder, FlexItem.ORDER_DEFAULT)
                mFlexGrow = getFloat(
                    R.styleable.FlexboxLayout_Layout_layout_uiFlexGrow,
                    FlexItem.FLEX_GROW_DEFAULT
                )
                mFlexShrink = getFloat(
                    R.styleable.FlexboxLayout_Layout_layout_uiFlexShrink,
                    FlexItem.FLEX_SHRINK_DEFAULT
                )
                mAlignSelf =
                    getInt(R.styleable.FlexboxLayout_Layout_layout_uiAlignSelf, AlignSelf.AUTO)
                mFlexBasisPercent = getFraction(
                    R.styleable.FlexboxLayout_Layout_layout_uiFlexBasisPercent, 1, 1,
                    FlexItem.FLEX_BASIS_PERCENT_DEFAULT
                )
                mMinWidth =
                    getDimensionPixelSize(R.styleable.FlexboxLayout_Layout_layout_uiMinWidth, NOT_SET)
                mMinHeight =
                    getDimensionPixelSize(
                        R.styleable.FlexboxLayout_Layout_layout_uiMinHeight,
                        NOT_SET
                    )
                mMaxWidth = getDimensionPixelSize(
                    R.styleable.FlexboxLayout_Layout_layout_uiMaxWidth,
                    FlexItem.MAX_SIZE
                )
                mMaxHeight = getDimensionPixelSize(
                    R.styleable.FlexboxLayout_Layout_layout_uiMaxHeight,
                    FlexItem.MAX_SIZE
                )
                mWrapBefore = getBoolean(R.styleable.FlexboxLayout_Layout_layout_uiWrapBefore, false)
            }
        }

        constructor(source: LayoutParams) : super(source) {
            mOrder = source.mOrder
            mFlexGrow = source.mFlexGrow
            mFlexShrink = source.mFlexShrink
            mAlignSelf = source.mAlignSelf
            mFlexBasisPercent = source.mFlexBasisPercent
            mMinWidth = source.mMinWidth
            mMinHeight = source.mMinHeight
            mMaxWidth = source.mMaxWidth
            mMaxHeight = source.mMaxHeight
            mWrapBefore = source.mWrapBefore
        }

        constructor(source: ViewGroup.LayoutParams) : super(source)

        constructor(width: Int, height: Int) : super(width, height)

        constructor(source: MarginLayoutParams) : super(source)

        private constructor(parcel: Parcel) : super(0, 0) {
            mOrder = parcel.readInt()
            mFlexGrow = parcel.readFloat()
            mFlexShrink = parcel.readFloat()
            mAlignSelf = parcel.readInt()
            mFlexBasisPercent = parcel.readFloat()
            mMinWidth = parcel.readInt()
            mMinHeight = parcel.readInt()
            mMaxWidth = parcel.readInt()
            mMaxHeight = parcel.readInt()
            mWrapBefore = parcel.readByte() != 0.toByte()
            bottomMargin = parcel.readInt()
            leftMargin = parcel.readInt()
            rightMargin = parcel.readInt()
            topMargin = parcel.readInt()
            height = parcel.readInt()
            width = parcel.readInt()
        }

        override var width: Int
            get() = super.width
            set(value) {
                super.width = value
            }
        override var height: Int
            get() = super.height
            set(value) {
                super.height = value
            }
        override var order: Int
            get() = mOrder
            set(order) {
                mOrder = order
            }
        override var flexGrow: Float
            get() = mFlexGrow
            set(flexGrow) {
                mFlexGrow = flexGrow
            }
        override var flexShrink: Float
            get() = mFlexShrink
            set(flexShrink) {
                mFlexShrink = flexShrink
            }
        @AlignSelf
        override var alignSelf: Int
            get() = mAlignSelf
            set(@AlignSelf alignSelf) {
                mAlignSelf = alignSelf
            }
        override var minWidth: Int
            get() = mMinWidth
            set(minWidth) {
                mMinWidth = minWidth
            }
        override var minHeight: Int
            get() = mMinHeight
            set(minHeight) {
                mMinHeight = minHeight
            }
        override var maxWidth: Int
            get() = mMaxWidth
            set(maxWidth) {
                mMaxWidth = maxWidth
            }
        override var maxHeight: Int
            get() = mMaxHeight
            set(maxHeight) {
                mMaxHeight = maxHeight
            }
        override var isWrapBefore: Boolean
            get() = mWrapBefore
            set(wrapBefore) {
                mWrapBefore = wrapBefore
            }
        override var flexBasisPercent: Float
            get() = mFlexBasisPercent
            set(flexBasisPercent) {
                mFlexBasisPercent = flexBasisPercent
            }
        override val marginLeft: Int
            get() = leftMargin
        override val marginTop: Int
            get() = topMargin
        override val marginRight: Int
            get() = rightMargin
        override val marginBottom: Int
            get() = bottomMargin
        override val marginStarts: Int
            get() = marginStart
        override val marginEnds: Int
            get() = marginEnd

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(mOrder)
            dest.writeFloat(mFlexGrow)
            dest.writeFloat(mFlexShrink)
            dest.writeInt(mAlignSelf)
            dest.writeFloat(mFlexBasisPercent)
            dest.writeInt(mMinWidth)
            dest.writeInt(mMinHeight)
            dest.writeInt(mMaxWidth)
            dest.writeInt(mMaxHeight)
            dest.writeByte(if (mWrapBefore) 1 else 0)
            dest.writeInt(bottomMargin)
            dest.writeInt(leftMargin)
            dest.writeInt(rightMargin)
            dest.writeInt(topMargin)
            dest.writeInt(height)
            dest.writeInt(width)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<LayoutParams> =
                object : Parcelable.Creator<LayoutParams> {
                    override fun createFromParcel(source: Parcel): LayoutParams {
                        return LayoutParams(source)
                    }

                    override fun newArray(size: Int): Array<LayoutParams?> {
                        return arrayOfNulls(size)
                    }
                }
        }
    }

    companion object {
        /** Constant to show no dividers */
        const val SHOW_DIVIDER_NONE = 0
        /** Constant to show a divider at the beginning of the flex lines (or flex items). */
        const val SHOW_DIVIDER_BEGINNING = 1
        /** Constant to show dividers between flex lines or flex items. */
        const val SHOW_DIVIDER_MIDDLE = 2
        /** Constant to show a divider at the end of the flex lines or flex items. */
        const val SHOW_DIVIDER_END = 4
        private const val NOT_SET = FlexContainer.NOT_SET
    }
}
