package com.ui.baselib.widget.flexbox

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

open class FlexboxLayoutManager : RecyclerView.LayoutManager, FlexContainer,
    RecyclerView.SmoothScroller.ScrollVectorProvider {

    private var mFlexDirection: Int = FlexDirection.ROW
    private var mFlexWrap: Int = FlexWrap.WRAP
    private var mJustifyContent: Int = JustifyContent.FLEX_START
    private var mAlignItems: Int = AlignItems.STRETCH
    override var maxLine: Int = FlexContainer.Companion.NOT_SET
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    private var mIsRtl: Boolean = false
    private var mFromBottomToTop: Boolean = false

    private var mFlexLines: MutableList<FlexLine> = ArrayList()
    private val mFlexboxHelper = FlexboxHelper(this)

    private var mRecycler: RecyclerView.Recycler? = null
    private var mState: RecyclerView.State? = null
    private var mLayoutState: LayoutState? = null
    private val mAnchorInfo = AnchorInfo()

    private var mOrientationHelper: OrientationHelper? = null
    private var mSubOrientationHelper: OrientationHelper? = null

    private var mPendingSavedState: SavedState? = null
    private var mPendingScrollPosition: Int = RecyclerView.NO_POSITION
    private var mPendingScrollPositionOffset: Int = LinearLayoutManager.INVALID_OFFSET

    private var mLastWidth: Int = Int.MIN_VALUE
    private var mLastHeight: Int = Int.MIN_VALUE

    var recycleChildrenOnDetach: Boolean = false

    private val mViewCache = SparseArray<View>()
    private val mContext: Context
    private var mParent: View? = null
    private var mDirtyPosition: Int = RecyclerView.NO_POSITION

    private val mFlexLinesResult = FlexboxHelper.FlexLinesResult()

    constructor(context: Context) : this(context, FlexDirection.ROW, FlexWrap.WRAP)

    constructor(context: Context, @FlexDirection flexDirection: Int) : this(context, flexDirection, FlexWrap.WRAP)

    constructor(context: Context, @FlexDirection flexDirection: Int, @FlexWrap flexWrap: Int) : super() {
        this.flexDirection = flexDirection
        this.flexWrap = flexWrap
        alignItems = AlignItems.STRETCH
        mContext = context
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super() {
        val properties = getProperties(context, attrs, defStyleAttr, defStyleRes)
        when (properties.orientation) {
            LinearLayoutManager.HORIZONTAL -> {
                flexDirection = if (properties.reverseLayout) FlexDirection.ROW_REVERSE else FlexDirection.ROW
            }
            LinearLayoutManager.VERTICAL -> {
                flexDirection = if (properties.reverseLayout) FlexDirection.COLUMN_REVERSE else FlexDirection.COLUMN
            }
        }
        flexWrap = FlexWrap.WRAP
        alignItems = AlignItems.STRETCH
        mContext = context
    }

    override fun isAutoMeasureEnabled(): Boolean = true

    @FlexDirection
    override var flexDirection: Int
        get() = mFlexDirection
        set(value) {
            if (mFlexDirection != value) {
                removeAllViews()
                mFlexDirection = value
                mOrientationHelper = null
                mSubOrientationHelper = null
                clearFlexLines()
                requestLayout()
            }
        }

    @FlexWrap
    override var flexWrap: Int
        get() = mFlexWrap
        set(value) {
            if (value == FlexWrap.WRAP_REVERSE) {
                throw UnsupportedOperationException("wrap_reverse is not supported in FlexboxLayoutManager")
            }
            if (mFlexWrap != value) {
                if (mFlexWrap == FlexWrap.NOWRAP || value == FlexWrap.NOWRAP) {
                    removeAllViews()
                    clearFlexLines()
                }
                mFlexWrap = value
                mOrientationHelper = null
                mSubOrientationHelper = null
                requestLayout()
            }
        }

    @JustifyContent
    override var justifyContent: Int
        get() = mJustifyContent
        set(value) {
            if (mJustifyContent != value) {
                mJustifyContent = value
                requestLayout()
            }
        }

    @AlignItems
    override var alignItems: Int
        get() = mAlignItems
        set(value) {
            if (mAlignItems != value) {
                if (mAlignItems == AlignItems.STRETCH || value == AlignItems.STRETCH) {
                    removeAllViews()
                    clearFlexLines()
                }
                mAlignItems = value
                requestLayout()
            }
        }

    @AlignContent
    override var alignContent: Int
        get() = AlignContent.STRETCH
        set(_) {
            throw UnsupportedOperationException(
                "Setting the alignContent in the FlexboxLayoutManager is not supported. " +
                        "Use FlexboxLayout if you need to use this attribute."
            )
        }

    override val flexLines: List<FlexLine>
        get() = mFlexLines.filter { it.itemCount != 0 }

    override fun getDecorationLengthMainAxis(view: View, index: Int, indexInFlexLine: Int): Int {
        return if (isMainAxisDirectionHorizontal()) {
            getLeftDecorationWidth(view) + getRightDecorationWidth(view)
        } else {
            getTopDecorationHeight(view) + getBottomDecorationHeight(view)
        }
    }

    override fun getDecorationLengthCrossAxis(view: View): Int {
        return if (isMainAxisDirectionHorizontal()) {
            getTopDecorationHeight(view) + getBottomDecorationHeight(view)
        } else {
            getLeftDecorationWidth(view) + getRightDecorationWidth(view)
        }
    }

    override fun onNewFlexItemAdded(view: View, index: Int, indexInFlexLine: Int, flexLine: FlexLine) {
        calculateItemDecorationsForChild(view, TEMP_RECT)
        if (isMainAxisDirectionHorizontal()) {
            val decorationWidth = getLeftDecorationWidth(view) + getRightDecorationWidth(view)
            flexLine.mMainSize += decorationWidth
            flexLine.mDividerLengthInMainSize += decorationWidth
        } else {
            val decorationHeight = getTopDecorationHeight(view) + getBottomDecorationHeight(view)
            flexLine.mMainSize += decorationHeight
            flexLine.mDividerLengthInMainSize += decorationHeight
        }
    }

    override val flexItemCount: Int
        get() = mState?.itemCount ?: 0

    override fun getFlexItemAt(index: Int): View? {
        mViewCache.get(index)?.let { return it }
        return mRecycler?.getViewForPosition(index)
    }

    override fun getReorderedFlexItemAt(index: Int): View? = getFlexItemAt(index)

    override fun onNewFlexLineAdded(flexLine: FlexLine) {
        // No op
    }

    override fun getChildWidthMeasureSpec(widthSpec: Int, padding: Int, childDimension: Int): Int {
        return getChildMeasureSpec(width, widthMode, padding, childDimension, canScrollHorizontally())
    }

    override fun getChildHeightMeasureSpec(heightSpec: Int, padding: Int, childDimension: Int): Int {
        return getChildMeasureSpec(height, heightMode, padding, childDimension, canScrollVertically())
    }

    override val largestMainSize: Int
        get() {
            if (mFlexLines.isEmpty()) return 0
            var largestSize = Int.MIN_VALUE
            for (flexLine in mFlexLines) {
                largestSize = max(largestSize, flexLine.mMainSize)
            }
            return largestSize
        }

    override val sumOfCrossSize: Int
        get() {
            var sum = 0
            for (flexLine in mFlexLines) {
                sum += flexLine.mCrossSize
            }
            return sum
        }

    override fun setFlexLines(flexLines: List<FlexLine>) {
        mFlexLines = flexLines.toMutableList()
    }

    override val flexLinesInternal: List<FlexLine>
        get() = mFlexLines

    override fun updateViewCache(position: Int, view: View) {
        mViewCache.put(position, view)
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        if (childCount == 0) return null
        val view = getChildAt(0) ?: return null
        val firstChildPos = getPosition(view)
        val direction = if (targetPosition < firstChildPos) -1 else 1
        return if (isMainAxisDirectionHorizontal()) {
            PointF(0f, direction.toFloat())
        } else {
            PointF(direction.toFloat(), 0f)
        }
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun generateLayoutParams(c: Context, attrs: AttributeSet): RecyclerView.LayoutParams {
        return LayoutParams(c, attrs)
    }

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean {
        return lp is LayoutParams
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        removeAllViews()
    }

    override fun onSaveInstanceState(): Parcelable? {
        mPendingSavedState?.let { return SavedState(it) }
        val savedState = SavedState()
        if (childCount > 0) {
            val firstView = getChildClosestToStart()
            firstView?.let {
                savedState.mAnchorPosition = getPosition(it)
                savedState.mAnchorOffset = mOrientationHelper!!.getDecoratedStart(it) -
                        mOrientationHelper!!.startAfterPadding
            }
        } else {
            savedState.invalidateAnchor()
        }
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            mPendingSavedState = state
            requestLayout()
            if (DEBUG) Log.d(TAG, "Loaded saved state. $mPendingSavedState")
        } else {
            if (DEBUG) Log.w(TAG, "Invalid state was trying to be restored. $state")
        }
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        super.onItemsAdded(recyclerView, positionStart, itemCount)
        updateDirtyPosition(positionStart)
    }

    override fun onItemsUpdated(recyclerView: RecyclerView, positionStart: Int, itemCount: Int, payload: Any?) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount, payload)
        updateDirtyPosition(positionStart)
    }

    override fun onItemsUpdated(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount)
        updateDirtyPosition(positionStart)
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount)
        updateDirtyPosition(positionStart)
    }

    override fun onItemsMoved(recyclerView: RecyclerView, from: Int, to: Int, itemCount: Int) {
        super.onItemsMoved(recyclerView, from, to, itemCount)
        updateDirtyPosition(minOf(from, to))
    }

    private fun updateDirtyPosition(positionStart: Int) {
        val lastVisiblePosition = findLastVisibleItemPosition()
        if (positionStart >= lastVisiblePosition) return

        val childCount = childCount
        mFlexboxHelper.ensureMeasureSpecCache(childCount)
        mFlexboxHelper.ensureMeasuredSizeCache(childCount)
        mFlexboxHelper.ensureIndexToFlexLine(childCount)

        val indexToFlexLine = mFlexboxHelper.mIndexToFlexLine ?: return
        if (positionStart >= indexToFlexLine.size) return

        mDirtyPosition = positionStart

        val firstView = getChildClosestToStart() ?: return
        mPendingScrollPosition = getPosition(firstView)

        mPendingScrollPositionOffset = if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            mOrientationHelper!!.getDecoratedEnd(firstView) + mOrientationHelper!!.endPadding
        } else {
            mOrientationHelper!!.getDecoratedStart(firstView) - mOrientationHelper!!.startAfterPadding
        }
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (DEBUG) {
            Log.d(TAG, "onLayoutChildren started")
            Log.d(TAG, "getChildCount: $childCount")
            Log.d(TAG, "State: $state")
        }

        mRecycler = recycler
        mState = state
        val childCount = state.itemCount
        if (childCount == 0 && state.isPreLayout) return

        resolveLayoutDirection()
        ensureOrientationHelper()
        ensureLayoutState()
        mFlexboxHelper.ensureMeasureSpecCache(childCount)
        mFlexboxHelper.ensureMeasuredSizeCache(childCount)
        mFlexboxHelper.ensureIndexToFlexLine(childCount)

        mLayoutState!!.mShouldRecycle = false

        if (mPendingSavedState != null && mPendingSavedState!!.hasValidAnchor(childCount)) {
            mPendingScrollPosition = mPendingSavedState!!.mAnchorPosition
        }

        if (!mAnchorInfo.mValid || mPendingScrollPosition != RecyclerView.NO_POSITION || mPendingSavedState != null) {
            mAnchorInfo.reset()
            updateAnchorInfoForLayout(state, mAnchorInfo)
            mAnchorInfo.mValid = true
        }
        detachAndScrapAttachedViews(recycler)

        if (mAnchorInfo.mLayoutFromEnd) {
            updateLayoutStateToFillStart(mAnchorInfo, false, true)
        } else {
            updateLayoutStateToFillEnd(mAnchorInfo, false, true)
        }

        updateFlexLines(childCount)

        var startOffset: Int
        var endOffset: Int
        val filledToEnd = fill(recycler, state, mLayoutState!!)

        if (mAnchorInfo.mLayoutFromEnd) {
            startOffset = mLayoutState!!.mOffset
            updateLayoutStateToFillEnd(mAnchorInfo, true, false)
            fill(recycler, state, mLayoutState!!)
            endOffset = mLayoutState!!.mOffset
        } else {
            endOffset = mLayoutState!!.mOffset
            updateLayoutStateToFillStart(mAnchorInfo, true, false)
            fill(recycler, state, mLayoutState!!)
            startOffset = mLayoutState!!.mOffset
        }

        if (getChildCount() > 0) {
            if (mAnchorInfo.mLayoutFromEnd) {
                val fixOffset = fixLayoutEndGap(endOffset, recycler, state, true)
                startOffset += fixOffset
                fixLayoutStartGap(startOffset, recycler, state, false)
            } else {
                val fixOffset = fixLayoutStartGap(startOffset, recycler, state, true)
                endOffset += fixOffset
                fixLayoutEndGap(endOffset, recycler, state, false)
            }
        }
    }

    private fun fixLayoutStartGap(startOffset: Int, recycler: RecyclerView.Recycler,
                                  state: RecyclerView.State, canOffsetChildren: Boolean): Int {
        var offset = startOffset
        val gap: Int
        val fixOffset: Int
        if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            gap = mOrientationHelper!!.endAfterPadding - offset
            if (gap > 0) {
                fixOffset = handleScrollingMainOrientation(-gap, recycler, state)
            } else {
                return 0
            }
        } else {
            gap = offset - mOrientationHelper!!.startAfterPadding
            if (gap > 0) {
                fixOffset = -handleScrollingMainOrientation(gap, recycler, state)
            } else {
                return 0
            }
        }
        offset += fixOffset
        if (canOffsetChildren) {
            val newGap = offset - mOrientationHelper!!.startAfterPadding
            if (newGap > 0) {
                mOrientationHelper!!.offsetChildren(-newGap)
                return fixOffset - newGap
            }
        }
        return fixOffset
    }

    private fun fixLayoutEndGap(endOffset: Int, recycler: RecyclerView.Recycler,
                                state: RecyclerView.State, canOffsetChildren: Boolean): Int {
        var offset = endOffset
        val columnAndRtl = !isMainAxisDirectionHorizontal() && mIsRtl
        val fixOffset: Int
        val gap: Int
        if (columnAndRtl) {
            gap = offset - mOrientationHelper!!.startAfterPadding
            fixOffset = if (gap > 0) {
                handleScrollingMainOrientation(gap, recycler, state)
            } else {
                return 0
            }
        } else {
            gap = mOrientationHelper!!.endAfterPadding - offset
            fixOffset = if (gap > 0) {
                -handleScrollingMainOrientation(-gap, recycler, state)
            } else {
                return 0
            }
        }
        offset += fixOffset
        if (canOffsetChildren) {
            val newGap = mOrientationHelper!!.endAfterPadding - offset
            if (newGap > 0) {
                mOrientationHelper!!.offsetChildren(newGap)
                return newGap + fixOffset
            }
        }
        return fixOffset
    }

    private fun updateFlexLines(childCount: Int) {
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, widthMode)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, heightMode)
        val width = width
        val height = height
        val isMainSizeChanged: Boolean
        val needsToFill: Int

        if (isMainAxisDirectionHorizontal()) {
            isMainSizeChanged = mLastWidth != Int.MIN_VALUE && mLastWidth != width
            needsToFill = if (mLayoutState!!.mInfinite) {
                mContext.resources.displayMetrics.heightPixels
            } else {
                mLayoutState!!.mAvailable
            }
        } else {
            isMainSizeChanged = mLastHeight != Int.MIN_VALUE && mLastHeight != height
            needsToFill = if (mLayoutState!!.mInfinite) {
                mContext.resources.displayMetrics.widthPixels
            } else {
                mLayoutState!!.mAvailable
            }
        }

        mLastWidth = width
        mLastHeight = height

        if (mDirtyPosition == RecyclerView.NO_POSITION &&
            (mPendingScrollPosition != RecyclerView.NO_POSITION || isMainSizeChanged)) {
            if (mAnchorInfo.mLayoutFromEnd) return
            mFlexLines.clear()
            mFlexLinesResult.reset()
            if (isMainAxisDirectionHorizontal()) {
                mFlexboxHelper.calculateHorizontalFlexLinesToIndex(
                    mFlexLinesResult, widthMeasureSpec, heightMeasureSpec,
                    needsToFill, mAnchorInfo.mPosition, mFlexLines
                )
            } else {
                mFlexboxHelper.calculateVerticalFlexLinesToIndex(
                    mFlexLinesResult, widthMeasureSpec, heightMeasureSpec,
                    needsToFill, mAnchorInfo.mPosition, mFlexLines
                )
            }
            mFlexLines = mFlexLinesResult.mFlexLines.toMutableList()
            mFlexboxHelper.determineMainSize(widthMeasureSpec, heightMeasureSpec)
            mFlexboxHelper.stretchViews()
            mAnchorInfo.mFlexLinePosition = mFlexboxHelper.mIndexToFlexLine!![mAnchorInfo.mPosition]
            mLayoutState!!.mFlexLinePosition = mAnchorInfo.mFlexLinePosition
        } else {
            val fromIndex = if (mDirtyPosition != RecyclerView.NO_POSITION) {
                minOf(mDirtyPosition, mAnchorInfo.mPosition)
            } else {
                mAnchorInfo.mPosition
            }

            mFlexLinesResult.reset()
            if (isMainAxisDirectionHorizontal()) {
                if (mFlexLines.isNotEmpty()) {
                    mFlexboxHelper.clearFlexLines(mFlexLines, fromIndex)
                    mFlexboxHelper.calculateFlexLines(
                        mFlexLinesResult, widthMeasureSpec, heightMeasureSpec,
                        needsToFill, fromIndex, mAnchorInfo.mPosition, mFlexLines
                    )
                } else {
                    mFlexboxHelper.ensureIndexToFlexLine(childCount)
                    mFlexboxHelper.calculateHorizontalFlexLines(
                        mFlexLinesResult, widthMeasureSpec, heightMeasureSpec,
                        needsToFill, 0, mFlexLines
                    )
                }
            } else {
                if (mFlexLines.isNotEmpty()) {
                    mFlexboxHelper.clearFlexLines(mFlexLines, fromIndex)
                    mFlexboxHelper.calculateFlexLines(
                        mFlexLinesResult, heightMeasureSpec, widthMeasureSpec,
                        needsToFill, fromIndex, mAnchorInfo.mPosition, mFlexLines
                    )
                } else {
                    mFlexboxHelper.ensureIndexToFlexLine(childCount)
                    mFlexboxHelper.calculateVerticalFlexLines(
                        mFlexLinesResult, widthMeasureSpec, heightMeasureSpec,
                        needsToFill, 0, mFlexLines
                    )
                }
            }
            mFlexLines = mFlexLinesResult.mFlexLines.toMutableList()
            mFlexboxHelper.determineMainSize(widthMeasureSpec, heightMeasureSpec, fromIndex)
            mFlexboxHelper.stretchViews(fromIndex)
        }
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
        mPendingSavedState = null
        mPendingScrollPosition = RecyclerView.NO_POSITION
        mPendingScrollPositionOffset = LinearLayoutManager.INVALID_OFFSET
        mDirtyPosition = RecyclerView.NO_POSITION
        mAnchorInfo.reset()
        mViewCache.clear()
    }

    internal fun isLayoutRtl(): Boolean = mIsRtl

    private fun resolveLayoutDirection() {
        val layoutDirection = layoutDirection
        when (mFlexDirection) {
            FlexDirection.ROW -> {
                mIsRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                mFromBottomToTop = mFlexWrap == FlexWrap.WRAP_REVERSE
            }
            FlexDirection.ROW_REVERSE -> {
                mIsRtl = layoutDirection != LAYOUT_DIRECTION_RTL
                mFromBottomToTop = mFlexWrap == FlexWrap.WRAP_REVERSE
            }
            FlexDirection.COLUMN -> {
                mIsRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) mIsRtl = !mIsRtl
                mFromBottomToTop = false
            }
            FlexDirection.COLUMN_REVERSE -> {
                mIsRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (mFlexWrap == FlexWrap.WRAP_REVERSE) mIsRtl = !mIsRtl
                mFromBottomToTop = true
            }
            else -> {
                mIsRtl = false
                mFromBottomToTop = false
            }
        }
    }

    private fun updateAnchorInfoForLayout(state: RecyclerView.State, anchorInfo: AnchorInfo) {
        if (updateAnchorFromPendingState(state, anchorInfo, mPendingSavedState)) return
        if (updateAnchorFromChildren(state, anchorInfo)) return
        anchorInfo.assignCoordinateFromPadding()
        anchorInfo.mPosition = 0
        anchorInfo.mFlexLinePosition = 0
    }

    private fun updateAnchorFromPendingState(state: RecyclerView.State, anchorInfo: AnchorInfo,
                                             savedState: SavedState?): Boolean {
        if (state.isPreLayout || mPendingScrollPosition == RecyclerView.NO_POSITION) return false
        if (mPendingScrollPosition < 0 || mPendingScrollPosition >= state.itemCount) {
            mPendingScrollPosition = RecyclerView.NO_POSITION
            mPendingScrollPositionOffset = LinearLayoutManager.INVALID_OFFSET
            return false
        }

        anchorInfo.mPosition = mPendingScrollPosition
        anchorInfo.mFlexLinePosition = mFlexboxHelper.mIndexToFlexLine!![anchorInfo.mPosition]

        if (mPendingSavedState != null && mPendingSavedState!!.hasValidAnchor(state.itemCount)) {
            anchorInfo.mCoordinate = mOrientationHelper!!.startAfterPadding + savedState!!.mAnchorOffset
            anchorInfo.mAssignedFromSavedState = true
            anchorInfo.mFlexLinePosition = RecyclerView.NO_POSITION
            return true
        }

        if (mPendingScrollPositionOffset == LinearLayoutManager.INVALID_OFFSET) {
            val anchorView = findViewByPosition(mPendingScrollPosition)
            if (anchorView != null) {
                if (mOrientationHelper!!.getDecoratedMeasurement(anchorView) > mOrientationHelper!!.totalSpace) {
                    anchorInfo.assignCoordinateFromPadding()
                    return true
                }
                val startGap = mOrientationHelper!!.getDecoratedStart(anchorView) - mOrientationHelper!!.startAfterPadding
                if (startGap < 0) {
                    anchorInfo.mCoordinate = mOrientationHelper!!.startAfterPadding
                    anchorInfo.mLayoutFromEnd = false
                    return true
                }
                val endGap = mOrientationHelper!!.endAfterPadding - mOrientationHelper!!.getDecoratedEnd(anchorView)
                if (endGap < 0) {
                    anchorInfo.mCoordinate = mOrientationHelper!!.endAfterPadding
                    anchorInfo.mLayoutFromEnd = true
                    return true
                }
                anchorInfo.mCoordinate = if (anchorInfo.mLayoutFromEnd) {
                    mOrientationHelper!!.getDecoratedEnd(anchorView) + mOrientationHelper!!.totalSpaceChange
                } else {
                    mOrientationHelper!!.getDecoratedStart(anchorView)
                }
            } else {
                if (childCount > 0) {
                    getChildAt(0)?.let { view ->
                        val position = getPosition(view)
                        anchorInfo.mLayoutFromEnd = mPendingScrollPosition < position
                    }
                }
                anchorInfo.assignCoordinateFromPadding()
            }
            return true
        }

        anchorInfo.mCoordinate = if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            mPendingScrollPositionOffset - mOrientationHelper!!.endPadding
        } else {
            mOrientationHelper!!.startAfterPadding + mPendingScrollPositionOffset
        }
        return true
    }

    private fun updateAnchorFromChildren(state: RecyclerView.State, anchorInfo: AnchorInfo): Boolean {
        if (childCount == 0) return false
        val referenceChild = if (anchorInfo.mLayoutFromEnd) {
            findLastReferenceChild(state.itemCount)
        } else {
            findFirstReferenceChild(state.itemCount)
        }
        if (referenceChild != null) {
            anchorInfo.assignFromView(referenceChild)
            if (!state.isPreLayout && supportsPredictiveItemAnimations()) {
                val notVisible = mOrientationHelper!!.getDecoratedStart(referenceChild) >= mOrientationHelper!!.endAfterPadding ||
                        mOrientationHelper!!.getDecoratedEnd(referenceChild) < mOrientationHelper!!.startAfterPadding
                if (notVisible) {
                    anchorInfo.mCoordinate = if (anchorInfo.mLayoutFromEnd) {
                        mOrientationHelper!!.endAfterPadding
                    } else {
                        mOrientationHelper!!.startAfterPadding
                    }
                }
            }
            return true
        }
        return false
    }

    private fun findFirstReferenceChild(itemCount: Int): View? {
        val firstFound = findReferenceChild(0, childCount, itemCount) ?: return null
        val firstFoundPosition = getPosition(firstFound)
        val firstFoundLinePosition = mFlexboxHelper.mIndexToFlexLine!![firstFoundPosition]
        if (firstFoundLinePosition == RecyclerView.NO_POSITION) return null
        val firstFoundLine = mFlexLines[firstFoundLinePosition]
        return findFirstReferenceViewInLine(firstFound, firstFoundLine)
    }

    private fun findLastReferenceChild(itemCount: Int): View? {
        val lastFound = findReferenceChild(childCount - 1, -1, itemCount) ?: return null
        val lastFoundPosition = getPosition(lastFound)
        val lastFoundLinePosition = mFlexboxHelper.mIndexToFlexLine!![lastFoundPosition]
        val lastFoundLine = mFlexLines[lastFoundLinePosition]
        return findLastReferenceViewInLine(lastFound, lastFoundLine)
    }

    private fun findReferenceChild(start: Int, end: Int, itemCount: Int): View? {
        ensureOrientationHelper()
        ensureLayoutState()
        var invalidMatch: View? = null
        var outOfBoundsMatch: View? = null
        val boundStart = mOrientationHelper!!.startAfterPadding
        val boundEnd = mOrientationHelper!!.endAfterPadding
        val diff = if (end > start) 1 else -1
        var i = start
        while (i != end) {
            val view = getChildAt(i)
            if (view != null) {
                val position = getPosition(view)
                if (position in 0 until itemCount) {
                    if ((view.layoutParams as RecyclerView.LayoutParams).isItemRemoved) {
                        if (invalidMatch == null) invalidMatch = view
                    } else if (mOrientationHelper!!.getDecoratedStart(view) < boundStart ||
                        mOrientationHelper!!.getDecoratedEnd(view) > boundEnd) {
                        if (outOfBoundsMatch == null) outOfBoundsMatch = view
                    } else {
                        return view
                    }
                }
            }
            i += diff
        }
        return outOfBoundsMatch ?: invalidMatch
    }

    private fun getChildClosestToStart(): View? = getChildAt(0)

    private fun fill(recycler: RecyclerView.Recycler, state: RecyclerView.State, layoutState: LayoutState): Int {
        if (layoutState.mScrollingOffset != LayoutState.SCROLLING_OFFSET_NaN) {
            if (layoutState.mAvailable < 0) {
                layoutState.mScrollingOffset += layoutState.mAvailable
            }
            recycleByLayoutState(recycler, layoutState)
        }
        val start = layoutState.mAvailable
        var remainingSpace = layoutState.mAvailable
        var consumed = 0
        val mainAxisHorizontal = isMainAxisDirectionHorizontal()

        while ((remainingSpace > 0 || mLayoutState!!.mInfinite) && layoutState.hasMore(state, mFlexLines)) {
            val flexLine = mFlexLines[layoutState.mFlexLinePosition]
            layoutState.mPosition = flexLine.mFirstIndex
            consumed += layoutFlexLine(flexLine, layoutState)

            if (!mainAxisHorizontal && mIsRtl) {
                layoutState.mOffset -= flexLine.crossSize * layoutState.mLayoutDirection
            } else {
                layoutState.mOffset += flexLine.crossSize * layoutState.mLayoutDirection
            }
            remainingSpace -= flexLine.crossSize
        }
        layoutState.mAvailable -= consumed
        if (layoutState.mScrollingOffset != LayoutState.SCROLLING_OFFSET_NaN) {
            layoutState.mScrollingOffset += consumed
            if (layoutState.mAvailable < 0) {
                layoutState.mScrollingOffset += layoutState.mAvailable
            }
            recycleByLayoutState(recycler, layoutState)
        }
        return start - layoutState.mAvailable
    }

    private fun recycleByLayoutState(recycler: RecyclerView.Recycler, layoutState: LayoutState) {
        if (!layoutState.mShouldRecycle) return
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            recycleFlexLinesFromEnd(recycler, layoutState)
        } else {
            recycleFlexLinesFromStart(recycler, layoutState)
        }
    }

    private fun recycleFlexLinesFromStart(recycler: RecyclerView.Recycler, layoutState: LayoutState) {
        if (layoutState.mScrollingOffset < 0) return
        val indexToFlexLine = mFlexboxHelper.mIndexToFlexLine ?: return
        val childCount = childCount
        if (childCount == 0) return
        val firstView = getChildAt(0) ?: return
        var currentLineIndex = indexToFlexLine[getPosition(firstView)]
        if (currentLineIndex == RecyclerView.NO_POSITION) return
        var flexLine = mFlexLines[currentLineIndex]
        var recycleTo = -1

        for (i in 0 until childCount) {
            val view = getChildAt(i) ?: continue
            if (canViewBeRecycledFromStart(view, layoutState.mScrollingOffset)) {
                if (flexLine.mLastIndex == getPosition(view)) {
                    recycleTo = i
                    if (currentLineIndex >= mFlexLines.size - 1) break
                    currentLineIndex += layoutState.mLayoutDirection
                    flexLine = mFlexLines[currentLineIndex]
                }
            } else {
                break
            }
        }
        recycleChildren(recycler, 0, recycleTo)
    }

    private fun canViewBeRecycledFromStart(view: View, scrollingOffset: Int): Boolean {
        return if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            mOrientationHelper!!.end - mOrientationHelper!!.getDecoratedStart(view) <= scrollingOffset
        } else {
            mOrientationHelper!!.getDecoratedEnd(view) <= scrollingOffset
        }
    }

    private fun recycleFlexLinesFromEnd(recycler: RecyclerView.Recycler, layoutState: LayoutState) {
        if (layoutState.mScrollingOffset < 0) return
        val indexToFlexLine = mFlexboxHelper.mIndexToFlexLine ?: return
        val childCount = childCount
        if (childCount == 0) return
        val lastView = getChildAt(childCount - 1) ?: return
        var currentLineIndex = indexToFlexLine[getPosition(lastView)]
        if (currentLineIndex == RecyclerView.NO_POSITION) return
        var recycleFrom = childCount
        var flexLine = mFlexLines[currentLineIndex]

        for (i in childCount - 1 downTo 0) {
            val view = getChildAt(i) ?: continue
            if (canViewBeRecycledFromEnd(view, layoutState.mScrollingOffset)) {
                if (flexLine.mFirstIndex == getPosition(view)) {
                    recycleFrom = i
                    if (currentLineIndex <= 0) break
                    currentLineIndex += layoutState.mLayoutDirection
                    flexLine = mFlexLines[currentLineIndex]
                }
            } else {
                break
            }
        }
        recycleChildren(recycler, recycleFrom, childCount - 1)
    }

    private fun canViewBeRecycledFromEnd(view: View, scrollingOffset: Int): Boolean {
        return if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            mOrientationHelper!!.getDecoratedEnd(view) <= scrollingOffset
        } else {
            mOrientationHelper!!.getDecoratedStart(view) >= mOrientationHelper!!.end - scrollingOffset
        }
    }

    private fun recycleChildren(recycler: RecyclerView.Recycler, startIndex: Int, endIndex: Int) {
        for (i in endIndex downTo startIndex) {
            removeAndRecycleViewAt(i, recycler)
        }
    }

    private fun layoutFlexLine(flexLine: FlexLine, layoutState: LayoutState): Int {
        return if (isMainAxisDirectionHorizontal()) {
            layoutFlexLineMainAxisHorizontal(flexLine, layoutState)
        } else {
            layoutFlexLineMainAxisVertical(flexLine, layoutState)
        }
    }

    private fun layoutFlexLineMainAxisHorizontal(flexLine: FlexLine, layoutState: LayoutState): Int {
        val measureSpecCache = mFlexboxHelper.mMeasureSpecCache!!
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val parentWidth = width

        var childTop = layoutState.mOffset
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            childTop -= flexLine.mCrossSize
        }
        val startPosition = layoutState.mPosition

        var childLeft: Float
        var childRight: Float
        var spaceBetweenItem = 0f

        when (mJustifyContent) {
            JustifyContent.FLEX_START -> {
                childLeft = paddingLeft.toFloat()
                childRight = (parentWidth - paddingRight).toFloat()
            }
            JustifyContent.FLEX_END -> {
                childLeft = (parentWidth - flexLine.mMainSize + paddingRight).toFloat()
                childRight = (flexLine.mMainSize - paddingLeft).toFloat()
            }
            JustifyContent.CENTER -> {
                childLeft = paddingLeft + (parentWidth - flexLine.mMainSize) / 2f
                childRight = parentWidth - paddingRight - (parentWidth - flexLine.mMainSize) / 2f
            }
            JustifyContent.SPACE_AROUND -> {
                if (flexLine.mItemCount != 0) {
                    spaceBetweenItem = (parentWidth - flexLine.mMainSize) / flexLine.mItemCount.toFloat()
                }
                childLeft = paddingLeft + spaceBetweenItem / 2f
                childRight = parentWidth - paddingRight - spaceBetweenItem / 2f
            }
            JustifyContent.SPACE_BETWEEN -> {
                childLeft = paddingLeft.toFloat()
                val denominator = if (flexLine.mItemCount != 1) flexLine.mItemCount - 1 else 1
                spaceBetweenItem = (parentWidth - flexLine.mMainSize) / denominator.toFloat()
                childRight = (parentWidth - paddingRight).toFloat()
            }
            JustifyContent.SPACE_EVENLY -> {
                if (flexLine.mItemCount != 0) {
                    spaceBetweenItem = (parentWidth - flexLine.mMainSize) / (flexLine.mItemCount + 1).toFloat()
                }
                childLeft = paddingLeft + spaceBetweenItem
                childRight = parentWidth - paddingRight - spaceBetweenItem
            }
            else -> throw IllegalStateException("Invalid justifyContent: $mJustifyContent")
        }
        childLeft -= mAnchorInfo.mPerpendicularCoordinate
        childRight -= mAnchorInfo.mPerpendicularCoordinate
        spaceBetweenItem = max(spaceBetweenItem, 0f)

        var indexInFlexLine = 0
        for (i in startPosition until startPosition + flexLine.itemCount) {
            val view = getFlexItemAt(i) ?: continue

            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                calculateItemDecorationsForChild(view, TEMP_RECT)
                addView(view)
            } else {
                calculateItemDecorationsForChild(view, TEMP_RECT)
                addView(view, indexInFlexLine)
                indexInFlexLine++
            }

            val measureSpec = measureSpecCache[i]
            val widthSpec = mFlexboxHelper.extractLowerInt(measureSpec)
            val heightSpec = mFlexboxHelper.extractHigherInt(measureSpec)
            val lp = view.layoutParams as LayoutParams
            if (shouldMeasureChild(view, widthSpec, heightSpec, lp)) {
                view.measure(widthSpec, heightSpec)
            }

            childLeft += (lp.leftMargin + getLeftDecorationWidth(view))
            childRight -= (lp.rightMargin + getRightDecorationWidth(view))

            val topWithDecoration = childTop + getTopDecorationHeight(view)
            if (mIsRtl) {
                mFlexboxHelper.layoutSingleChildHorizontal(view, flexLine,
                    childRight.roundToInt() - view.measuredWidth,
                    topWithDecoration, childRight.roundToInt(),
                    topWithDecoration + view.measuredHeight)
            } else {
                mFlexboxHelper.layoutSingleChildHorizontal(view, flexLine,
                    childLeft.roundToInt(), topWithDecoration,
                    childLeft.roundToInt() + view.measuredWidth,
                    topWithDecoration + view.measuredHeight)
            }
            childLeft += view.measuredWidth + lp.rightMargin + getRightDecorationWidth(view) + spaceBetweenItem
            childRight -= view.measuredWidth + lp.leftMargin + getLeftDecorationWidth(view) + spaceBetweenItem
        }
        layoutState.mFlexLinePosition += mLayoutState!!.mLayoutDirection
        return flexLine.crossSize
    }

    private fun layoutFlexLineMainAxisVertical(flexLine: FlexLine, layoutState: LayoutState): Int {
        val measureSpecCache = mFlexboxHelper.mMeasureSpecCache!!
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom
        val parentHeight = height

        var childLeft = layoutState.mOffset
        var childRight = layoutState.mOffset
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            childLeft -= flexLine.mCrossSize
            childRight += flexLine.mCrossSize
        }
        val startPosition = layoutState.mPosition

        var childTop: Float
        var childBottom: Float
        var spaceBetweenItem = 0f

        when (mJustifyContent) {
            JustifyContent.FLEX_START -> {
                childTop = paddingTop.toFloat()
                childBottom = (parentHeight - paddingBottom).toFloat()
            }
            JustifyContent.FLEX_END -> {
                childTop = (parentHeight - flexLine.mMainSize + paddingBottom).toFloat()
                childBottom = (flexLine.mMainSize - paddingTop).toFloat()
            }
            JustifyContent.CENTER -> {
                childTop = paddingTop + (parentHeight - flexLine.mMainSize) / 2f
                childBottom = parentHeight - paddingBottom - (parentHeight - flexLine.mMainSize) / 2f
            }
            JustifyContent.SPACE_AROUND -> {
                if (flexLine.mItemCount != 0) {
                    spaceBetweenItem = (parentHeight - flexLine.mMainSize) / flexLine.mItemCount.toFloat()
                }
                childTop = paddingTop + spaceBetweenItem / 2f
                childBottom = parentHeight - paddingBottom - spaceBetweenItem / 2f
            }
            JustifyContent.SPACE_BETWEEN -> {
                childTop = paddingTop.toFloat()
                val denominator = if (flexLine.mItemCount != 1) flexLine.mItemCount - 1 else 1
                spaceBetweenItem = (parentHeight - flexLine.mMainSize) / denominator.toFloat()
                childBottom = (parentHeight - paddingBottom).toFloat()
            }
            JustifyContent.SPACE_EVENLY -> {
                if (flexLine.mItemCount != 0) {
                    spaceBetweenItem = (parentHeight - flexLine.mMainSize) / (flexLine.mItemCount + 1).toFloat()
                }
                childTop = paddingTop + spaceBetweenItem
                childBottom = parentHeight - paddingBottom - spaceBetweenItem
            }
            else -> throw IllegalStateException("Invalid justifyContent: $mJustifyContent")
        }
        childTop -= mAnchorInfo.mPerpendicularCoordinate
        childBottom -= mAnchorInfo.mPerpendicularCoordinate
        spaceBetweenItem = max(spaceBetweenItem, 0f)

        var indexInFlexLine = 0
        for (i in startPosition until startPosition + flexLine.itemCount) {
            val view = getFlexItemAt(i) ?: continue

            val measureSpec = measureSpecCache[i]
            val widthSpec = mFlexboxHelper.extractLowerInt(measureSpec)
            val heightSpec = mFlexboxHelper.extractHigherInt(measureSpec)
            val lp = view.layoutParams as LayoutParams
            if (shouldMeasureChild(view, widthSpec, heightSpec, lp)) {
                view.measure(widthSpec, heightSpec)
            }

            childTop += (lp.topMargin + getTopDecorationHeight(view))
            childBottom -= (lp.bottomMargin + getBottomDecorationHeight(view))

            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                calculateItemDecorationsForChild(view, TEMP_RECT)
                addView(view)
            } else {
                calculateItemDecorationsForChild(view, TEMP_RECT)
                addView(view, indexInFlexLine)
                indexInFlexLine++
            }

            val leftWithDecoration = childLeft + getLeftDecorationWidth(view)
            val rightWithDecoration = childRight - getRightDecorationWidth(view)
            if (mIsRtl) {
                if (mFromBottomToTop) {
                    mFlexboxHelper.layoutSingleChildVertical(view, flexLine, mIsRtl,
                        rightWithDecoration - view.measuredWidth,
                        childBottom.roundToInt() - view.measuredHeight,
                        rightWithDecoration, childBottom.roundToInt())
                } else {
                    mFlexboxHelper.layoutSingleChildVertical(view, flexLine, mIsRtl,
                        rightWithDecoration - view.measuredWidth,
                        childTop.roundToInt(), rightWithDecoration,
                        childTop.roundToInt() + view.measuredHeight)
                }
            } else {
                if (mFromBottomToTop) {
                    mFlexboxHelper.layoutSingleChildVertical(view, flexLine, mIsRtl,
                        leftWithDecoration, childBottom.roundToInt() - view.measuredHeight,
                        leftWithDecoration + view.measuredWidth, childBottom.roundToInt())
                } else {
                    mFlexboxHelper.layoutSingleChildVertical(view, flexLine, mIsRtl,
                        leftWithDecoration, childTop.roundToInt(),
                        leftWithDecoration + view.measuredWidth,
                        childTop.roundToInt() + view.measuredHeight)
                }
            }
            childTop += view.measuredHeight + lp.bottomMargin + getBottomDecorationHeight(view) + spaceBetweenItem
            childBottom -= view.measuredHeight + lp.topMargin + getTopDecorationHeight(view) + spaceBetweenItem
        }
        layoutState.mFlexLinePosition += mLayoutState!!.mLayoutDirection
        return flexLine.crossSize
    }

    override fun isMainAxisDirectionHorizontal(): Boolean {
        return mFlexDirection == FlexDirection.ROW || mFlexDirection == FlexDirection.ROW_REVERSE
    }

    private fun updateLayoutStateToFillEnd(anchorInfo: AnchorInfo, fromNextLine: Boolean, considerInfinite: Boolean) {
        if (considerInfinite) resolveInfiniteAmount() else mLayoutState!!.mInfinite = false
        mLayoutState!!.mAvailable = if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            anchorInfo.mCoordinate - paddingRight
        } else {
            mOrientationHelper!!.endAfterPadding - anchorInfo.mCoordinate
        }
        mLayoutState!!.mPosition = anchorInfo.mPosition
        mLayoutState!!.mItemDirection = LayoutState.ITEM_DIRECTION_TAIL
        mLayoutState!!.mLayoutDirection = LayoutState.LAYOUT_END
        mLayoutState!!.mOffset = anchorInfo.mCoordinate
        mLayoutState!!.mScrollingOffset = LayoutState.SCROLLING_OFFSET_NaN
        mLayoutState!!.mFlexLinePosition = anchorInfo.mFlexLinePosition

        if (fromNextLine && mFlexLines.size > 1 && anchorInfo.mFlexLinePosition >= 0 &&
            anchorInfo.mFlexLinePosition < mFlexLines.size - 1) {
            val currentLine = mFlexLines[anchorInfo.mFlexLinePosition]
            mLayoutState!!.mFlexLinePosition++
            mLayoutState!!.mPosition += currentLine.itemCount
        }
    }

    private fun updateLayoutStateToFillStart(anchorInfo: AnchorInfo, fromPreviousLine: Boolean, considerInfinite: Boolean) {
        if (considerInfinite) resolveInfiniteAmount() else mLayoutState!!.mInfinite = false
        mLayoutState!!.mAvailable = if (!isMainAxisDirectionHorizontal() && mIsRtl) {
            mParent!!.width - anchorInfo.mCoordinate - mOrientationHelper!!.startAfterPadding
        } else {
            anchorInfo.mCoordinate - mOrientationHelper!!.startAfterPadding
        }
        mLayoutState!!.mPosition = anchorInfo.mPosition
        mLayoutState!!.mItemDirection = LayoutState.ITEM_DIRECTION_TAIL
        mLayoutState!!.mLayoutDirection = LayoutState.LAYOUT_START
        mLayoutState!!.mOffset = anchorInfo.mCoordinate
        mLayoutState!!.mScrollingOffset = LayoutState.SCROLLING_OFFSET_NaN
        mLayoutState!!.mFlexLinePosition = anchorInfo.mFlexLinePosition

        if (fromPreviousLine && anchorInfo.mFlexLinePosition > 0 && mFlexLines.size > anchorInfo.mFlexLinePosition) {
            val currentLine = mFlexLines[anchorInfo.mFlexLinePosition]
            mLayoutState!!.mFlexLinePosition--
            mLayoutState!!.mPosition -= currentLine.itemCount
        }
    }

    private fun resolveInfiniteAmount() {
        val crossMode = if (isMainAxisDirectionHorizontal()) heightMode else widthMode
        mLayoutState!!.mInfinite = crossMode == View.MeasureSpec.UNSPECIFIED || crossMode == View.MeasureSpec.AT_MOST
    }

    private fun ensureOrientationHelper() {
        if (mOrientationHelper != null) return
        if (isMainAxisDirectionHorizontal()) {
            if (mFlexWrap == FlexWrap.NOWRAP) {
                mOrientationHelper = OrientationHelper.createHorizontalHelper(this)
                mSubOrientationHelper = OrientationHelper.createVerticalHelper(this)
            } else {
                mOrientationHelper = OrientationHelper.createVerticalHelper(this)
                mSubOrientationHelper = OrientationHelper.createHorizontalHelper(this)
            }
        } else {
            if (mFlexWrap == FlexWrap.NOWRAP) {
                mOrientationHelper = OrientationHelper.createVerticalHelper(this)
                mSubOrientationHelper = OrientationHelper.createHorizontalHelper(this)
            } else {
                mOrientationHelper = OrientationHelper.createHorizontalHelper(this)
                mSubOrientationHelper = OrientationHelper.createVerticalHelper(this)
            }
        }
    }

    private fun ensureLayoutState() {
        if (mLayoutState == null) mLayoutState = LayoutState()
    }

    override fun scrollToPosition(position: Int) {
        mPendingScrollPosition = position
        mPendingScrollPositionOffset = LinearLayoutManager.INVALID_OFFSET
        mPendingSavedState?.invalidateAnchor()
        requestLayout()
    }

    override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
        val smoothScroller = LinearSmoothScroller(recyclerView.context)
        smoothScroller.targetPosition = position
        startSmoothScroll(smoothScroller)
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        mParent = view.parent as? View
    }

    override fun onDetachedFromWindow(view: RecyclerView, recycler: RecyclerView.Recycler) {
        super.onDetachedFromWindow(view, recycler)
        if (recycleChildrenOnDetach) {
            removeAndRecycleAllViews(recycler)
            recycler.clear()
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return if (mFlexWrap == FlexWrap.NOWRAP) {
            isMainAxisDirectionHorizontal()
        } else {
            !isMainAxisDirectionHorizontal() || width > (mParent?.width ?: 0)
        }
    }

    override fun canScrollVertically(): Boolean {
        return if (mFlexWrap == FlexWrap.NOWRAP) {
            !isMainAxisDirectionHorizontal()
        } else {
            isMainAxisDirectionHorizontal() || height > (mParent?.height ?: 0)
        }
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        return if (!isMainAxisDirectionHorizontal() || mFlexWrap == FlexWrap.NOWRAP) {
            val scrolled = handleScrollingMainOrientation(dx, recycler, state)
            mViewCache.clear()
            scrolled
        } else {
            val scrolled = handleScrollingSubOrientation(dx)
            mAnchorInfo.mPerpendicularCoordinate += scrolled
            mSubOrientationHelper!!.offsetChildren(-scrolled)
            scrolled
        }
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        return if (isMainAxisDirectionHorizontal() || (mFlexWrap == FlexWrap.NOWRAP && !isMainAxisDirectionHorizontal())) {
            val scrolled = handleScrollingMainOrientation(dy, recycler, state)
            mViewCache.clear()
            scrolled
        } else {
            val scrolled = handleScrollingSubOrientation(dy)
            mAnchorInfo.mPerpendicularCoordinate += scrolled
            mSubOrientationHelper!!.offsetChildren(-scrolled)
            scrolled
        }
    }

    private fun handleScrollingMainOrientation(delta: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (childCount == 0 || delta == 0) return 0
        ensureOrientationHelper()
        mLayoutState!!.mShouldRecycle = true
        val columnAndRtl = !isMainAxisDirectionHorizontal() && mIsRtl
        val layoutDirection = if (columnAndRtl) {
            if (delta < 0) LayoutState.LAYOUT_END else LayoutState.LAYOUT_START
        } else {
            if (delta > 0) LayoutState.LAYOUT_END else LayoutState.LAYOUT_START
        }
        val absDelta = abs(delta)
        updateLayoutState(layoutDirection, absDelta)
        val freeScroll = mLayoutState!!.mScrollingOffset
        val consumed = freeScroll + fill(recycler, state, mLayoutState!!)
        if (consumed < 0) return 0
        val scrolled = if (columnAndRtl) {
            if (absDelta > consumed) -layoutDirection * consumed else delta
        } else {
            if (absDelta > consumed) layoutDirection * consumed else delta
        }
        mOrientationHelper!!.offsetChildren(-scrolled)
        mLayoutState!!.mLastScrollDelta = scrolled
        return scrolled
    }

    private fun handleScrollingSubOrientation(delta: Int): Int {
        if (childCount == 0 || delta == 0) return 0
        ensureOrientationHelper()
        val isMainAxisHorizontal = isMainAxisDirectionHorizontal()
        val parentLength = if (isMainAxisHorizontal) mParent!!.width else mParent!!.height
        val mainAxisLength = if (isMainAxisHorizontal) width else height
        val layoutRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        return if (layoutRtl) {
            val absDelta = abs(delta)
            if (delta < 0) {
                -minOf(mainAxisLength + mAnchorInfo.mPerpendicularCoordinate - parentLength, absDelta)
            } else {
                if (mAnchorInfo.mPerpendicularCoordinate + delta > 0) -mAnchorInfo.mPerpendicularCoordinate else delta
            }
        } else {
            if (delta > 0) {
                minOf(mainAxisLength - mAnchorInfo.mPerpendicularCoordinate - parentLength, delta)
            } else {
                if (mAnchorInfo.mPerpendicularCoordinate + delta >= 0) delta else -mAnchorInfo.mPerpendicularCoordinate
            }
        }
    }

    private fun updateLayoutState(layoutDirection: Int, absDelta: Int) {
        val indexToFlexLine = mFlexboxHelper.mIndexToFlexLine!!
        mLayoutState!!.mLayoutDirection = layoutDirection
        val mainAxisHorizontal = isMainAxisDirectionHorizontal()
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, widthMode)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, heightMode)
        val columnAndRtl = !mainAxisHorizontal && mIsRtl

        if (layoutDirection == LayoutState.LAYOUT_END) {
            val lastVisible = getChildAt(childCount - 1) ?: return
            mLayoutState!!.mOffset = mOrientationHelper!!.getDecoratedEnd(lastVisible)
            val lastVisiblePosition = getPosition(lastVisible)
            val lastVisibleLinePosition = indexToFlexLine[lastVisiblePosition]
            val lastVisibleLine = mFlexLines[lastVisibleLinePosition]
            val referenceView = findLastReferenceViewInLine(lastVisible, lastVisibleLine)
            mLayoutState!!.mItemDirection = LayoutState.ITEM_DIRECTION_TAIL
            mLayoutState!!.mPosition = lastVisiblePosition + mLayoutState!!.mItemDirection
            mLayoutState!!.mFlexLinePosition = if (indexToFlexLine.size <= mLayoutState!!.mPosition) {
                RecyclerView.NO_POSITION
            } else {
                indexToFlexLine[mLayoutState!!.mPosition]
            }

            if (columnAndRtl) {
                mLayoutState!!.mOffset = mOrientationHelper!!.getDecoratedStart(referenceView)
                mLayoutState!!.mScrollingOffset = -mOrientationHelper!!.getDecoratedStart(referenceView) +
                        mOrientationHelper!!.startAfterPadding
                mLayoutState!!.mScrollingOffset = max(mLayoutState!!.mScrollingOffset, 0)
            } else {
                mLayoutState!!.mOffset = mOrientationHelper!!.getDecoratedEnd(referenceView)
                mLayoutState!!.mScrollingOffset = mOrientationHelper!!.getDecoratedEnd(referenceView) -
                        mOrientationHelper!!.endAfterPadding
            }

            if ((mLayoutState!!.mFlexLinePosition == RecyclerView.NO_POSITION ||
                        mLayoutState!!.mFlexLinePosition > mFlexLines.size - 1) &&
                mLayoutState!!.mPosition <= flexItemCount) {
                val needsToFill = absDelta - mLayoutState!!.mScrollingOffset
                mFlexLinesResult.reset()
                if (needsToFill > 0) {
                    if (mainAxisHorizontal) {
                        mFlexboxHelper.calculateHorizontalFlexLines(mFlexLinesResult,
                            widthMeasureSpec, heightMeasureSpec, needsToFill,
                            mLayoutState!!.mPosition, mFlexLines)
                    } else {
                        mFlexboxHelper.calculateVerticalFlexLines(mFlexLinesResult,
                            widthMeasureSpec, heightMeasureSpec, needsToFill,
                            mLayoutState!!.mPosition, mFlexLines)
                    }
                    mFlexboxHelper.determineMainSize(widthMeasureSpec, heightMeasureSpec, mLayoutState!!.mPosition)
                    mFlexboxHelper.stretchViews(mLayoutState!!.mPosition)
                }
            }
        } else {
            val firstVisible = getChildAt(0) ?: return
            mLayoutState!!.mOffset = mOrientationHelper!!.getDecoratedStart(firstVisible)
            val firstVisiblePosition = getPosition(firstVisible)
            var firstVisibleLinePosition = indexToFlexLine[firstVisiblePosition]
            val firstVisibleLine = mFlexLines[firstVisibleLinePosition]
            val referenceView = findFirstReferenceViewInLine(firstVisible, firstVisibleLine)
            mLayoutState!!.mItemDirection = LayoutState.ITEM_DIRECTION_TAIL

            var flexLinePosition = indexToFlexLine[firstVisiblePosition]
            if (flexLinePosition == RecyclerView.NO_POSITION) flexLinePosition = 0
            mLayoutState!!.mPosition = if (flexLinePosition > 0) {
                val previousLine = mFlexLines[flexLinePosition - 1]
                firstVisiblePosition - previousLine.itemCount
            } else {
                RecyclerView.NO_POSITION
            }
            mLayoutState!!.mFlexLinePosition = if (flexLinePosition > 0) flexLinePosition - 1 else 0

            if (columnAndRtl) {
                mLayoutState!!.mOffset = mOrientationHelper!!.getDecoratedEnd(referenceView)
                mLayoutState!!.mScrollingOffset = mOrientationHelper!!.getDecoratedEnd(referenceView) -
                        mOrientationHelper!!.endAfterPadding
                mLayoutState!!.mScrollingOffset = max(mLayoutState!!.mScrollingOffset, 0)
            } else {
                mLayoutState!!.mOffset = mOrientationHelper!!.getDecoratedStart(referenceView)
                mLayoutState!!.mScrollingOffset = -mOrientationHelper!!.getDecoratedStart(referenceView) +
                        mOrientationHelper!!.startAfterPadding
            }
        }
        mLayoutState!!.mAvailable = absDelta - mLayoutState!!.mScrollingOffset
    }

    private fun findFirstReferenceViewInLine(firstView: View, firstVisibleLine: FlexLine): View {
        val mainAxisHorizontal = isMainAxisDirectionHorizontal()
        var referenceView = firstView
        for (i in 1 until firstVisibleLine.mItemCount) {
            val viewInSameLine = getChildAt(i) ?: continue
            if (viewInSameLine.visibility == View.GONE) continue
            if (mIsRtl && !mainAxisHorizontal) {
                if (mOrientationHelper!!.getDecoratedEnd(referenceView) < mOrientationHelper!!.getDecoratedEnd(viewInSameLine)) {
                    referenceView = viewInSameLine
                }
            } else {
                if (mOrientationHelper!!.getDecoratedStart(referenceView) > mOrientationHelper!!.getDecoratedStart(viewInSameLine)) {
                    referenceView = viewInSameLine
                }
            }
        }
        return referenceView
    }

    private fun findLastReferenceViewInLine(lastView: View, lastVisibleLine: FlexLine): View {
        val mainAxisHorizontal = isMainAxisDirectionHorizontal()
        var referenceView = lastView
        for (i in childCount - 2 downTo childCount - lastVisibleLine.mItemCount) {
            val viewInSameLine = getChildAt(i) ?: continue
            if (viewInSameLine.visibility == View.GONE) continue
            if (mIsRtl && !mainAxisHorizontal) {
                if (mOrientationHelper!!.getDecoratedStart(referenceView) > mOrientationHelper!!.getDecoratedStart(viewInSameLine)) {
                    referenceView = viewInSameLine
                }
            } else {
                if (mOrientationHelper!!.getDecoratedEnd(referenceView) < mOrientationHelper!!.getDecoratedEnd(viewInSameLine)) {
                    referenceView = viewInSameLine
                }
            }
        }
        return referenceView
    }

    override fun computeHorizontalScrollExtent(state: RecyclerView.State) = computeScrollExtent(state)
    override fun computeVerticalScrollExtent(state: RecyclerView.State) = computeScrollExtent(state)

    private fun computeScrollExtent(state: RecyclerView.State): Int {
        if (childCount == 0) return 0
        ensureOrientationHelper()
        val firstReferenceView = findFirstReferenceChild(state.itemCount)
        val lastReferenceView = findLastReferenceChild(state.itemCount)
        if (state.itemCount == 0 || firstReferenceView == null || lastReferenceView == null) return 0
        val extend = mOrientationHelper!!.getDecoratedEnd(lastReferenceView) -
                mOrientationHelper!!.getDecoratedStart(firstReferenceView)
        return minOf(mOrientationHelper!!.totalSpace, extend)
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State) = computeScrollOffset(state)
    override fun computeVerticalScrollOffset(state: RecyclerView.State) = computeScrollOffset(state)

    private fun computeScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) return 0
        val firstReferenceView = findFirstReferenceChild(state.itemCount)
        val lastReferenceView = findLastReferenceChild(state.itemCount)
        if (state.itemCount == 0 || firstReferenceView == null || lastReferenceView == null) return 0
        val indexToFlexLine = mFlexboxHelper.mIndexToFlexLine!!
        val minPosition = getPosition(firstReferenceView)
        val maxPosition = getPosition(lastReferenceView)
        val laidOutArea = abs(mOrientationHelper!!.getDecoratedEnd(lastReferenceView) -
                mOrientationHelper!!.getDecoratedStart(firstReferenceView))
        val firstLinePosition = indexToFlexLine[minPosition]
        if (firstLinePosition == 0 || firstLinePosition == RecyclerView.NO_POSITION) return 0
        val lastLinePosition = indexToFlexLine[maxPosition]
        val lineRange = lastLinePosition - firstLinePosition + 1
        val averageSizePerLine = laidOutArea.toFloat() / lineRange
        return (firstLinePosition * averageSizePerLine + (mOrientationHelper!!.startAfterPadding -
                mOrientationHelper!!.getDecoratedStart(firstReferenceView))).roundToInt()
    }

    override fun computeHorizontalScrollRange(state: RecyclerView.State) = computeScrollRange(state)
    override fun computeVerticalScrollRange(state: RecyclerView.State) = computeScrollRange(state)

    private fun computeScrollRange(state: RecyclerView.State): Int {
        if (childCount == 0) return 0
        val firstReferenceView = findFirstReferenceChild(state.itemCount)
        val lastReferenceView = findLastReferenceChild(state.itemCount)
        if (state.itemCount == 0 || firstReferenceView == null || lastReferenceView == null) return 0
        val laidOutArea = abs(mOrientationHelper!!.getDecoratedEnd(lastReferenceView) -
                mOrientationHelper!!.getDecoratedStart(firstReferenceView))
        val firstVisiblePosition = findFirstVisibleItemPosition()
        val lastVisiblePosition = findLastVisibleItemPosition()
        val laidOutRange = lastVisiblePosition - firstVisiblePosition + 1
        return (laidOutArea.toFloat() / laidOutRange * state.itemCount).toInt()
    }

    private fun shouldMeasureChild(child: View, widthSpec: Int, heightSpec: Int, lp: RecyclerView.LayoutParams): Boolean {
        return child.isLayoutRequested || !isMeasurementCacheEnabled ||
                !isMeasurementUpToDate(child.width, widthSpec, lp.width) ||
                !isMeasurementUpToDate(child.height, heightSpec, lp.height)
    }

    private fun clearFlexLines() {
        mFlexLines.clear()
        mAnchorInfo.reset()
        mAnchorInfo.mPerpendicularCoordinate = 0
    }

    private fun getChildLeft(view: View): Int {
        val params = view.layoutParams as RecyclerView.LayoutParams
        return getDecoratedLeft(view) - params.leftMargin
    }

    private fun getChildRight(view: View): Int {
        val params = view.layoutParams as RecyclerView.LayoutParams
        return getDecoratedRight(view) + params.rightMargin
    }

    private fun getChildTop(view: View): Int {
        val params = view.layoutParams as RecyclerView.LayoutParams
        return getDecoratedTop(view) - params.topMargin
    }

    private fun getChildBottom(view: View): Int {
        val params = view.layoutParams as RecyclerView.LayoutParams
        return getDecoratedBottom(view) + params.bottomMargin
    }

    private fun isViewVisible(view: View, completelyVisible: Boolean): Boolean {
        val left = paddingLeft
        val top = paddingTop
        val right = width - paddingRight
        val bottom = height - paddingBottom
        val childLeft = getChildLeft(view)
        val childTop = getChildTop(view)
        val childRight = getChildRight(view)
        val childBottom = getChildBottom(view)

        val horizontalCompletelyVisible = left <= childLeft && right >= childRight
        val horizontalPartiallyVisible = childLeft >= right || childRight >= left
        val verticalCompletelyVisible = top <= childTop && bottom >= childBottom
        val verticalPartiallyVisible = childTop >= bottom || childBottom >= top

        return if (completelyVisible) {
            horizontalCompletelyVisible && verticalCompletelyVisible
        } else {
            horizontalPartiallyVisible && verticalPartiallyVisible
        }
    }

    fun findFirstVisibleItemPosition(): Int {
        val child = findOneVisibleChild(0, childCount, false)
        return if (child == null) RecyclerView.NO_POSITION else getPosition(child)
    }

    fun findFirstCompletelyVisibleItemPosition(): Int {
        val child = findOneVisibleChild(0, childCount, true)
        return if (child == null) RecyclerView.NO_POSITION else getPosition(child)
    }

    fun findLastVisibleItemPosition(): Int {
        val child = findOneVisibleChild(childCount - 1, -1, false)
        return if (child == null) RecyclerView.NO_POSITION else getPosition(child)
    }

    fun findLastCompletelyVisibleItemPosition(): Int {
        val child = findOneVisibleChild(childCount - 1, -1, true)
        return if (child == null) RecyclerView.NO_POSITION else getPosition(child)
    }

    private fun findOneVisibleChild(fromIndex: Int, toIndex: Int, completelyVisible: Boolean): View? {
        val next = if (toIndex > fromIndex) 1 else -1
        var i = fromIndex
        while (i != toIndex) {
            val view = getChildAt(i)
            if (view != null && isViewVisible(view, completelyVisible)) return view
            i += next
        }
        return null
    }

    internal fun getPositionToFlexLineIndex(position: Int): Int = mFlexboxHelper.mIndexToFlexLine!![position]

    @Suppress("PROPERTY_HIDES_JAVA_FIELD")
    class LayoutParams : RecyclerView.LayoutParams, FlexItem {
        private var mFlexGrow = FlexItem.FLEX_GROW_DEFAULT
        private var mFlexShrink = FlexItem.FLEX_SHRINK_DEFAULT
        private var mAlignSelf = AlignSelf.AUTO
        private var mFlexBasisPercent = FlexItem.FLEX_BASIS_PERCENT_DEFAULT
        private var mMinWidth = 0
        private var mMinHeight = 0
        private var mMaxWidth = FlexItem.MAX_SIZE
        private var mMaxHeight = FlexItem.MAX_SIZE
        private var mWrapBefore = false

        override var width: Int
            get() = super.width
            set(value) { super.width = value }
        override var height: Int
            get() = super.height
            set(value) { super.height = value }
        override var flexGrow: Float get() = mFlexGrow; set(value) { mFlexGrow = value }
        override var flexShrink: Float get() = mFlexShrink; set(value) { mFlexShrink = value }
        @get:AlignSelf
        override var alignSelf: Int get() = mAlignSelf; set(value) { mAlignSelf = value }
        override var minWidth: Int get() = mMinWidth; set(value) { mMinWidth = value }
        override var minHeight: Int get() = mMinHeight; set(value) { mMinHeight = value }
        override var maxWidth: Int get() = mMaxWidth; set(value) { mMaxWidth = value }
        override var maxHeight: Int get() = mMaxHeight; set(value) { mMaxHeight = value }
        override var isWrapBefore: Boolean get() = mWrapBefore; set(value) { mWrapBefore = value }
        override var flexBasisPercent: Float get() = mFlexBasisPercent; set(value) { mFlexBasisPercent = value }
        override val marginLeft: Int get() = leftMargin
        override val marginTop: Int get() = topMargin
        override val marginRight: Int get() = rightMargin
        override val marginBottom: Int get() = bottomMargin
        override val marginStarts: Int get() = marginStart
        override val marginEnds: Int get() = marginEnd
        override var order: Int
            get() = FlexItem.ORDER_DEFAULT
            set(_) { throw UnsupportedOperationException("Setting order in FlexboxLayoutManager is not supported.") }

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)
        constructor(source: RecyclerView.LayoutParams) : super(source)
        constructor(source: LayoutParams) : super(source) {
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
        private constructor(parcel: Parcel) : super(WRAP_CONTENT, WRAP_CONTENT) {
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

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
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
            val CREATOR: Parcelable.Creator<LayoutParams> = object : Parcelable.Creator<LayoutParams> {
                override fun createFromParcel(source: Parcel) = LayoutParams(source)
                override fun newArray(size: Int) = arrayOfNulls<LayoutParams>(size)
            }
        }
    }

    private inner class AnchorInfo {
        var mPosition: Int = RecyclerView.NO_POSITION
        var mFlexLinePosition: Int = RecyclerView.NO_POSITION
        var mCoordinate: Int = LinearLayoutManager.INVALID_OFFSET
        var mPerpendicularCoordinate: Int = 0
        var mLayoutFromEnd: Boolean = false
        var mValid: Boolean = false
        var mAssignedFromSavedState: Boolean = false

        fun reset() {
            mPosition = RecyclerView.NO_POSITION
            mFlexLinePosition = RecyclerView.NO_POSITION
            mCoordinate = LinearLayoutManager.INVALID_OFFSET
            mValid = false
            mAssignedFromSavedState = false
            mLayoutFromEnd = if (isMainAxisDirectionHorizontal()) {
                if (mFlexWrap == FlexWrap.NOWRAP) mFlexDirection == FlexDirection.ROW_REVERSE
                else mFlexWrap == FlexWrap.WRAP_REVERSE
            } else {
                if (mFlexWrap == FlexWrap.NOWRAP) mFlexDirection == FlexDirection.COLUMN_REVERSE
                else mFlexWrap == FlexWrap.WRAP_REVERSE
            }
        }
        fun assignCoordinateFromPadding() {
            mCoordinate = if (!isMainAxisDirectionHorizontal() && mIsRtl) {
                if (mLayoutFromEnd) mOrientationHelper!!.endAfterPadding
                else width - mOrientationHelper!!.startAfterPadding
            } else {
                if (mLayoutFromEnd) mOrientationHelper!!.endAfterPadding
                else mOrientationHelper!!.startAfterPadding
            }
        }

        fun assignFromView(anchor: View) {
            val orientationHelper = if (mFlexWrap == FlexWrap.NOWRAP) mSubOrientationHelper else mOrientationHelper
            mCoordinate = if (!isMainAxisDirectionHorizontal() && mIsRtl) {
                if (mLayoutFromEnd) orientationHelper!!.getDecoratedStart(anchor) + orientationHelper.totalSpaceChange
                else orientationHelper!!.getDecoratedEnd(anchor)
            } else {
                if (mLayoutFromEnd) orientationHelper!!.getDecoratedEnd(anchor) + orientationHelper.totalSpaceChange
                else orientationHelper!!.getDecoratedStart(anchor)
            }
            mPosition = getPosition(anchor)
            mAssignedFromSavedState = false
            val indexToFlexLine = mFlexboxHelper.mIndexToFlexLine!!
            var flexLinePosition = indexToFlexLine[if (mPosition != RecyclerView.NO_POSITION) mPosition else 0]
            mFlexLinePosition = if (flexLinePosition != RecyclerView.NO_POSITION) flexLinePosition else 0
            if (mFlexLines.size > mFlexLinePosition) {
                mPosition = mFlexLines[mFlexLinePosition].mFirstIndex
            }
        }

        override fun toString(): String = "AnchorInfo{mPosition=$mPosition, mFlexLinePosition=$mFlexLinePosition, " +
                "mCoordinate=$mCoordinate, mPerpendicularCoordinate=$mPerpendicularCoordinate, " +
                "mLayoutFromEnd=$mLayoutFromEnd, mValid=$mValid, mAssignedFromSavedState=$mAssignedFromSavedState}"
    }

    private class LayoutState {
        var mAvailable: Int = 0
        var mInfinite: Boolean = false
        var mFlexLinePosition: Int = 0
        var mPosition: Int = 0
        var mOffset: Int = 0
        var mScrollingOffset: Int = 0
        var mLastScrollDelta: Int = 0
        var mItemDirection: Int = ITEM_DIRECTION_TAIL
        var mLayoutDirection: Int = LAYOUT_END
        var mShouldRecycle: Boolean = false

        fun hasMore(state: RecyclerView.State, flexLines: List<FlexLine>): Boolean {
            return mPosition >= 0 && mPosition < state.itemCount &&
                    mFlexLinePosition >= 0 && mFlexLinePosition < flexLines.size
        }

        override fun toString(): String = "LayoutState{mAvailable=$mAvailable, mFlexLinePosition=$mFlexLinePosition, " +
                "mPosition=$mPosition, mOffset=$mOffset, mScrollingOffset=$mScrollingOffset, " +
                "mLastScrollDelta=$mLastScrollDelta, mItemDirection=$mItemDirection, mLayoutDirection=$mLayoutDirection}"

        companion object {
            const val SCROLLING_OFFSET_NaN = Int.MIN_VALUE
            const val LAYOUT_START = -1
            const val LAYOUT_END = 1
            const val ITEM_DIRECTION_TAIL = 1
        }
    }

    private class SavedState() : Parcelable {
        var mAnchorPosition: Int = 0
        var mAnchorOffset: Int = 0

        constructor(savedState: SavedState) : this() {
            mAnchorPosition = savedState.mAnchorPosition
            mAnchorOffset = savedState.mAnchorOffset
        }

        private constructor(parcel: Parcel) : this() {
            mAnchorPosition = parcel.readInt()
            mAnchorOffset = parcel.readInt()
        }

        fun invalidateAnchor() { mAnchorPosition = RecyclerView.NO_POSITION }
        fun hasValidAnchor(itemCount: Int): Boolean = mAnchorPosition in 0 until itemCount

        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(mAnchorPosition)
            dest.writeInt(mAnchorOffset)
        }
        override fun toString(): String = "SavedState{mAnchorPosition=$mAnchorPosition, mAnchorOffset=$mAnchorOffset}"

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel) = SavedState(source)
            override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
        }
    }

    companion object {
        private const val TAG = "FlexboxLayoutManager"
        private val TEMP_RECT = Rect()
        private const val DEBUG = false

        private fun isMeasurementUpToDate(childSize: Int, spec: Int, dimension: Int): Boolean {
            val specMode = View.MeasureSpec.getMode(spec)
            val specSize = View.MeasureSpec.getSize(spec)
            if (dimension > 0 && childSize != dimension) return false
            return when (specMode) {
                View.MeasureSpec.UNSPECIFIED -> true
                View.MeasureSpec.AT_MOST -> specSize >= childSize
                View.MeasureSpec.EXACTLY -> specSize == childSize
                else -> false
            }
        }
    }
}
