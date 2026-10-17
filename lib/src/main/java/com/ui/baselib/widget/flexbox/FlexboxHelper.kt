package com.ui.baselib.widget.flexbox

import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.annotation.VisibleForTesting
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.view.isGone

internal class FlexboxHelper(private val mFlexContainer: FlexContainer) {
    private val paddingTop: Int
        get() = when (mFlexContainer) {
            is View -> mFlexContainer.paddingTop
            is RecyclerView.LayoutManager -> mFlexContainer.paddingTop
            else -> 0
        }
    private val paddingBottom: Int
        get() = when (mFlexContainer) {
            is View -> mFlexContainer.paddingBottom
            is RecyclerView.LayoutManager -> mFlexContainer.paddingBottom
            else -> 0
        }
    private val paddingLeft: Int
        get() = when (mFlexContainer) {
            is View -> mFlexContainer.paddingLeft
            is RecyclerView.LayoutManager -> mFlexContainer.paddingLeft
            else -> 0
        }
    private val paddingRight: Int
        get() = when (mFlexContainer) {
            is View -> mFlexContainer.paddingRight
            is RecyclerView.LayoutManager -> mFlexContainer.paddingRight
            else -> 0
        }
    private val paddingStarts: Int
        get() = when (mFlexContainer) {
            is View -> mFlexContainer.paddingStart
            is RecyclerView.LayoutManager -> mFlexContainer.paddingStart
            else -> 0
        }
    private val paddingEnd: Int
        get() = when (mFlexContainer) {
            is View -> mFlexContainer.paddingEnd
            is RecyclerView.LayoutManager -> mFlexContainer.paddingEnd
            else -> 0
        }
    private var mChildrenFrozen: BooleanArray? = null
    /**
     * Map the view index to the flex line which contains the view represented by the index to
     * look for a flex line from a given view index in a constant time.
     * Key: index of the view
     * Value: index of the flex line that contains the given view
     *
     * E.g. if we have following flex lines,
     *
     * FlexLine(0): itemCount 3
     * FlexLine(1): itemCount 2
     *
     * this instance should have following entries
     *
     * [0, 0, 0, 1, 1, ...]
     */
    @JvmField
    var mIndexToFlexLine: IntArray? = null
    /**
     * Cache the measured spec. The first 32 bit represents the height measure spec, the last
     * 32 bit represents the width measure spec of each flex item.
     * E.g. an entry is created like `(long) heightMeasureSpec << 32 | widthMeasureSpec`
     *
     * To retrieve a widthMeasureSpec, call [extractLowerInt] or
     * [extractHigherInt] for a heightMeasureSpec.
     */
    @JvmField
    var mMeasureSpecCache: LongArray? = null
    /**
     * Cache a flex item's measured width and height. The first 32 bit represents the height, the
     * last 32 bit represents the width of each flex item.
     * E.g. an entry is created like the following code.
     * `(long) view.getMeasuredHeight() << 32 | view.getMeasuredWidth()`
     *
     * To retrieve a width value, call [extractLowerInt] or
     * [extractHigherInt] for a height value.
     */
    private var mMeasuredSizeCache: LongArray? = null
    /**
     * Create an array, which indicates the reordered indices that
     * [FlexItem.order] attributes are taken into account.
     * This method takes a View before that is added as the parent ViewGroup's children.
     *
     * @param viewBeforeAdded          the View instance before added to the array of children
     *                                 Views of the parent ViewGroup
     * @param indexForViewBeforeAdded  the index for the View before added to the array of the
     *                                 parent ViewGroup
     * @param paramsForViewBeforeAdded the layout parameters for the View before added to the array
     *                                 of the parent ViewGroup
     * @return an array which have the reordered indices
     */
    fun createReorderedIndices(
        viewBeforeAdded: View?,
        indexForViewBeforeAdded: Int,
        paramsForViewBeforeAdded: ViewGroup.LayoutParams?,
        orderCache: SparseIntArray
    ): IntArray {
        val childCount = mFlexContainer.flexItemCount
        val orders = createOrders(childCount)
        val orderForViewToBeAdded = Order()
        orderForViewToBeAdded.order =
            if (viewBeforeAdded != null && paramsForViewBeforeAdded is FlexItem) {
                paramsForViewBeforeAdded.order
            } else {
                FlexItem.ORDER_DEFAULT
            }

        when {
            indexForViewBeforeAdded == -1 || indexForViewBeforeAdded == childCount -> {
                orderForViewToBeAdded.index = childCount
            }
            indexForViewBeforeAdded < mFlexContainer.flexItemCount -> {
                orderForViewToBeAdded.index = indexForViewBeforeAdded
                for (i in indexForViewBeforeAdded until childCount) {
                    orders[i].index++
                }
            }
            else -> {
                // This path is not expected since OutOfBoundException will be thrown in the ViewGroup
                // But setting the index for fail-safe
                orderForViewToBeAdded.index = childCount
            }
        }
        orders.add(orderForViewToBeAdded)

        return sortOrdersIntoReorderedIndices(childCount + 1, orders, orderCache)
    }
    /**
     * Create an array, which indicates the reordered indices that
     * [FlexItem.order] attributes are taken into account.
     *
     * @return an array which have the reordered indices
     */
    fun createReorderedIndices(orderCache: SparseIntArray): IntArray {
        val childCount = mFlexContainer.flexItemCount
        val orders = createOrders(childCount)
        return sortOrdersIntoReorderedIndices(childCount, orders, orderCache)
    }

    private fun createOrders(childCount: Int): MutableList<Order> {
        val orders = ArrayList<Order>(childCount)
        for (i in 0 until childCount) {
            val child = mFlexContainer.getFlexItemAt(i)
            val flexItem = child?.layoutParams as? FlexItem
            val order = Order()
            order.order = flexItem?.order ?: FlexItem.ORDER_DEFAULT
            order.index = i
            orders.add(order)
        }
        return orders
    }
    /**
     * Returns if any of the children's [FlexItem.order] attributes are
     * changed from the last measurement.
     *
     * @return `true` if changed from the last measurement, `false` otherwise.
     */
    fun isOrderChangedFromLastMeasurement(orderCache: SparseIntArray): Boolean {
        val childCount = mFlexContainer.flexItemCount
        if (orderCache.size() != childCount) {
            return true
        }
        for (i in 0 until childCount) {
            val view = mFlexContainer.getFlexItemAt(i) ?: continue
            val flexItem = view.layoutParams as FlexItem
            if (flexItem.order != orderCache.get(i)) {
                return true
            }
        }
        return false
    }

    private fun sortOrdersIntoReorderedIndices(
        childCount: Int,
        orders: MutableList<Order>,
        orderCache: SparseIntArray
    ): IntArray {
        orders.sort()
        orderCache.clear()
        val reorderedIndices = IntArray(childCount)
        var i = 0
        for (order in orders) {
            reorderedIndices[i] = order.index
            orderCache.append(order.index, order.order)
            i++
        }
        return reorderedIndices
    }
    /**
     * Calculate how many flex lines are needed in the flex container.
     * This method should calculate all the flex lines from the existing flex items.
     *
     * @see [calculateFlexLines]
     */
    fun calculateHorizontalFlexLines(
        result: FlexLinesResult,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        calculateFlexLines(
            result, widthMeasureSpec, heightMeasureSpec, Int.MAX_VALUE,
            0, RecyclerView.NO_POSITION, null
        )
    }
    /**
     * Calculate how many flex lines are needed in the flex container.
     * Stop calculating it if the calculated amount along the cross size reaches the argument
     * as the needsCalcAmount.
     */
    fun calculateHorizontalFlexLines(
        result: FlexLinesResult,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        needsCalcAmount: Int,
        fromIndex: Int,
        existingLines: MutableList<FlexLine>?
    ) {
        calculateFlexLines(
            result, widthMeasureSpec, heightMeasureSpec, needsCalcAmount,
            fromIndex, RecyclerView.NO_POSITION, existingLines
        )
    }
    /**
     * Calculate how many flex lines are needed in the flex container.
     * This method calculates the amount of pixels as the [needsCalcAmount] in addition to the
     * flex lines which includes the view who has the index as the [toIndex] argument.
     */
    fun calculateHorizontalFlexLinesToIndex(
        result: FlexLinesResult,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        needsCalcAmount: Int,
        toIndex: Int,
        existingLines: MutableList<FlexLine>?
    ) {
        calculateFlexLines(
            result, widthMeasureSpec, heightMeasureSpec, needsCalcAmount,
            0, toIndex, existingLines
        )
    }
    /**
     * Calculate how many flex lines are needed in the flex container.
     * This method should calculate all the flex lines from the existing flex items.
     */
    fun calculateVerticalFlexLines(
        result: FlexLinesResult,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        calculateFlexLines(
            result, heightMeasureSpec, widthMeasureSpec, Int.MAX_VALUE,
            0, RecyclerView.NO_POSITION, null
        )
    }
    /**
     * Calculate how many flex lines are needed in the flex container.
     * Stop calculating it if the calculated amount along the cross size reaches the argument
     * as the needsCalcAmount.
     */
    fun calculateVerticalFlexLines(
        result: FlexLinesResult,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        needsCalcAmount: Int,
        fromIndex: Int,
        existingLines: MutableList<FlexLine>?
    ) {
        calculateFlexLines(
            result, heightMeasureSpec, widthMeasureSpec, needsCalcAmount,
            fromIndex, RecyclerView.NO_POSITION, existingLines
        )
    }
    /**
     * Calculate how many flex lines are needed in the flex container.
     * This method calculates the amount of pixels as the [needsCalcAmount] in addition to the
     * flex lines which includes the view who has the index as the [toIndex] argument.
     */
    fun calculateVerticalFlexLinesToIndex(
        result: FlexLinesResult,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        needsCalcAmount: Int,
        toIndex: Int,
        existingLines: MutableList<FlexLine>?
    ) {
        calculateFlexLines(
            result, heightMeasureSpec, widthMeasureSpec, needsCalcAmount,
            0, toIndex, existingLines
        )
    }
    /**
     * Calculates how many flex lines are needed in the flex container layout by measuring each
     * child. Expanding or shrinking the flex items depending on the flex grow and flex shrink
     * attributes are done in a later procedure, so the views' measured width and measured
     * height may be changed in a later process.
     */
    fun calculateFlexLines(
        result: FlexLinesResult,
        mainMeasureSpec: Int,
        crossMeasureSpec: Int,
        needsCalcAmount: Int,
        fromIndex: Int,
        toIndex: Int,
        existingLines: MutableList<FlexLine>?
    ) {
        val isMainHorizontal = mFlexContainer.isMainAxisDirectionHorizontal()
        val mainMode = View.MeasureSpec.getMode(mainMeasureSpec)
        val mainSize = View.MeasureSpec.getSize(mainMeasureSpec)
        var childState = 0
        val flexLines: MutableList<FlexLine> = existingLines ?: ArrayList()
        result.mFlexLines = flexLines
        var reachedToIndex = toIndex == RecyclerView.NO_POSITION
        val mainPaddingStart = getPaddingStartMain(isMainHorizontal)
        val mainPaddingEnd = getPaddingEndMain(isMainHorizontal)
        val crossPaddingStart = getPaddingStartCross(isMainHorizontal)
        val crossPaddingEnd = getPaddingEndCross(isMainHorizontal)
        var largestSizeInCross = Int.MIN_VALUE
        var sumCrossSize = 0
        var indexInFlexLine = 0
        var flexLine = FlexLine()
        flexLine.mFirstIndex = fromIndex
        flexLine.mMainSize = mainPaddingStart + mainPaddingEnd
        val childCount = mFlexContainer.flexItemCount
        for (i in fromIndex until childCount) {
            val child = mFlexContainer.getReorderedFlexItemAt(i)

            if (child == null) {
                if (isLastFlexItem(i, childCount, flexLine)) {
                    addFlexLine(flexLines, flexLine, i, sumCrossSize)
                }
                continue
            } else if (child.visibility == View.GONE) {
                flexLine.mGoneItemCount++
                flexLine.mItemCount++
                if (isLastFlexItem(i, childCount, flexLine)) {
                    addFlexLine(flexLines, flexLine, i, sumCrossSize)
                }
                continue
            } else if (child is CompoundButton) {
                evaluateMinimumSizeForCompoundButton(child)
            }
            val flexItem = child.layoutParams as FlexItem

            if (flexItem.alignSelf == AlignItems.STRETCH) {
                flexLine.mIndicesAlignSelfStretch.add(i)
            }
            var childMainSize = getFlexItemSizeMain(flexItem, isMainHorizontal)

            if (flexItem.flexBasisPercent != FlexItem.FLEX_BASIS_PERCENT_DEFAULT &&
                mainMode == View.MeasureSpec.EXACTLY
            ) {
                childMainSize = (mainSize * flexItem.flexBasisPercent).roundToInt()
            }
            var childMainMeasureSpec: Int
            var childCrossMeasureSpec: Int
            if (isMainHorizontal) {
                childMainMeasureSpec = mFlexContainer.getChildWidthMeasureSpec(
                    mainMeasureSpec,
                    mainPaddingStart + mainPaddingEnd +
                            getFlexItemMarginStartMain(flexItem, true) +
                            getFlexItemMarginEndMain(flexItem, true),
                    childMainSize
                )
                childCrossMeasureSpec = mFlexContainer.getChildHeightMeasureSpec(
                    crossMeasureSpec,
                    crossPaddingStart + crossPaddingEnd +
                            getFlexItemMarginStartCross(flexItem, true) +
                            getFlexItemMarginEndCross(flexItem, true) + sumCrossSize,
                    getFlexItemSizeCross(flexItem, true)
                )
                child.measure(childMainMeasureSpec, childCrossMeasureSpec)
                updateMeasureCache(i, childMainMeasureSpec, childCrossMeasureSpec, child)
            } else {
                childCrossMeasureSpec = mFlexContainer.getChildWidthMeasureSpec(
                    crossMeasureSpec,
                    crossPaddingStart + crossPaddingEnd +
                            getFlexItemMarginStartCross(flexItem, false) +
                            getFlexItemMarginEndCross(flexItem, false) + sumCrossSize,
                    getFlexItemSizeCross(flexItem, false)
                )
                childMainMeasureSpec = mFlexContainer.getChildHeightMeasureSpec(
                    mainMeasureSpec,
                    mainPaddingStart + mainPaddingEnd +
                            getFlexItemMarginStartMain(flexItem, false) +
                            getFlexItemMarginEndMain(flexItem, false),
                    childMainSize
                )
                child.measure(childCrossMeasureSpec, childMainMeasureSpec)
                updateMeasureCache(i, childCrossMeasureSpec, childMainMeasureSpec, child)
            }
            mFlexContainer.updateViewCache(i, child)

            checkSizeConstraints(child, i)

            childState = View.combineMeasuredStates(childState, child.measuredState)

            if (isWrapRequired(
                    child, mainMode, mainSize, flexLine.mMainSize,
                    getViewMeasuredSizeMain(child, isMainHorizontal) +
                            getFlexItemMarginStartMain(flexItem, isMainHorizontal) +
                            getFlexItemMarginEndMain(flexItem, isMainHorizontal),
                    flexItem, i, indexInFlexLine, flexLines.size
                )
            ) {
                if (flexLine.itemCountNotGone > 0) {
                    addFlexLine(flexLines, flexLine, if (i > 0) i - 1 else 0, sumCrossSize)
                    sumCrossSize += flexLine.mCrossSize
                }

                if (isMainHorizontal) {
                    if (flexItem.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                        childCrossMeasureSpec = mFlexContainer.getChildHeightMeasureSpec(
                            crossMeasureSpec,
                            paddingTop + paddingBottom +
                                    flexItem.marginTop + flexItem.marginBottom + sumCrossSize,
                            flexItem.height
                        )
                        child.measure(childMainMeasureSpec, childCrossMeasureSpec)
                        checkSizeConstraints(child, i)
                    }
                } else {
                    if (flexItem.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                        childCrossMeasureSpec = mFlexContainer.getChildWidthMeasureSpec(
                            crossMeasureSpec,
                            paddingLeft + paddingRight +
                                    flexItem.marginLeft + flexItem.marginRight + sumCrossSize,
                            flexItem.width
                        )
                        child.measure(childCrossMeasureSpec, childMainMeasureSpec)
                        checkSizeConstraints(child, i)
                    }
                }

                flexLine = FlexLine()
                flexLine.mItemCount = 1
                flexLine.mMainSize = mainPaddingStart + mainPaddingEnd
                flexLine.mFirstIndex = i
                indexInFlexLine = 0
                largestSizeInCross = Int.MIN_VALUE
            } else {
                flexLine.mItemCount++
                indexInFlexLine++
            }
            flexLine.mAnyItemsHaveFlexGrow = flexLine.mAnyItemsHaveFlexGrow ||
                    flexItem.flexGrow != FlexItem.FLEX_GROW_DEFAULT
            flexLine.mAnyItemsHaveFlexShrink = flexLine.mAnyItemsHaveFlexShrink ||
                    flexItem.flexShrink != FlexItem.FLEX_SHRINK_NOT_SET

            mIndexToFlexLine?.let { it[i] = flexLines.size }

            flexLine.mMainSize += getViewMeasuredSizeMain(child, isMainHorizontal) +
                    getFlexItemMarginStartMain(flexItem, isMainHorizontal) +
                    getFlexItemMarginEndMain(flexItem, isMainHorizontal)
            flexLine.mTotalFlexGrow += flexItem.flexGrow
            flexLine.mTotalFlexShrink += flexItem.flexShrink

            mFlexContainer.onNewFlexItemAdded(child, i, indexInFlexLine, flexLine)

            largestSizeInCross = max(
                largestSizeInCross,
                getViewMeasuredSizeCross(child, isMainHorizontal) +
                        getFlexItemMarginStartCross(flexItem, isMainHorizontal) +
                        getFlexItemMarginEndCross(flexItem, isMainHorizontal) +
                        mFlexContainer.getDecorationLengthCrossAxis(child)
            )
            flexLine.mCrossSize = max(flexLine.mCrossSize, largestSizeInCross)

            if (isMainHorizontal) {
                if (mFlexContainer.flexWrap != FlexWrap.WRAP_REVERSE) {
                    flexLine.mMaxBaseline = max(
                        flexLine.mMaxBaseline,
                        child.baseline + flexItem.marginTop
                    )
                } else {
                    flexLine.mMaxBaseline = max(
                        flexLine.mMaxBaseline,
                        child.measuredHeight - child.baseline + flexItem.marginBottom
                    )
                }
            }

            if (isLastFlexItem(i, childCount, flexLine)) {
                addFlexLine(flexLines, flexLine, i, sumCrossSize)
                sumCrossSize += flexLine.mCrossSize
            }

            if (toIndex != RecyclerView.NO_POSITION &&
                flexLines.size > 0 &&
                flexLines[flexLines.size - 1].mLastIndex >= toIndex &&
                i >= toIndex &&
                !reachedToIndex
            ) {
                sumCrossSize = -flexLine.crossSize
                reachedToIndex = true
            }
            if (sumCrossSize > needsCalcAmount && reachedToIndex) {
                break
            }
        }

        result.mChildState = childState
    }

    private fun evaluateMinimumSizeForCompoundButton(compoundButton: CompoundButton) {
        val flexItem = compoundButton.layoutParams as FlexItem
        var minWidth = flexItem.minWidth
        var minHeight = flexItem.minHeight
        val drawable = CompoundButtonCompat.getButtonDrawable(compoundButton)
        val drawableMinWidth = drawable?.minimumWidth ?: 0
        val drawableMinHeight = drawable?.minimumHeight ?: 0
        flexItem.minWidth =
            if (minWidth == FlexContainer.Companion.NOT_SET) drawableMinWidth else minWidth
        flexItem.minHeight =
            if (minHeight == FlexContainer.Companion.NOT_SET) drawableMinHeight else minHeight
    }

    private fun getPaddingStartMain(isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) paddingStarts else paddingTop
    }

    private fun getPaddingEndMain(isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) paddingEnd else paddingBottom
    }

    private fun getPaddingStartCross(isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) paddingTop else paddingStarts
    }

    private fun getPaddingEndCross(isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) paddingBottom else paddingEnd
    }

    private fun getViewMeasuredSizeMain(view: View, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) view.measuredWidth else view.measuredHeight
    }

    private fun getViewMeasuredSizeCross(view: View, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) view.measuredHeight else view.measuredWidth
    }

    private fun getFlexItemSizeMain(flexItem: FlexItem, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) flexItem.width else flexItem.height
    }

    private fun getFlexItemSizeCross(flexItem: FlexItem, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) flexItem.height else flexItem.width
    }

    private fun getFlexItemMarginStartMain(flexItem: FlexItem, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) flexItem.marginLeft else flexItem.marginTop
    }

    private fun getFlexItemMarginEndMain(flexItem: FlexItem, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) flexItem.marginRight else flexItem.marginBottom
    }

    private fun getFlexItemMarginStartCross(flexItem: FlexItem, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) flexItem.marginTop else flexItem.marginLeft
    }

    private fun getFlexItemMarginEndCross(flexItem: FlexItem, isMainHorizontal: Boolean): Int {
        return if (isMainHorizontal) flexItem.marginBottom else flexItem.marginRight
    }

    private fun isWrapRequired(
        view: View,
        mode: Int,
        maxSize: Int,
        currentLength: Int,
        childLength: Int,
        flexItem: FlexItem,
        index: Int,
        indexInFlexLine: Int,
        flexLinesSize: Int
    ): Boolean {
        if (mFlexContainer.flexWrap == FlexWrap.NOWRAP) {
            return false
        }
        if (flexItem.isWrapBefore) {
            return true
        }
        if (mode == View.MeasureSpec.UNSPECIFIED) {
            return false
        }
        val maxLine = mFlexContainer.maxLine
        if (maxLine != FlexContainer.Companion.NOT_SET && maxLine <= flexLinesSize + 1) {
            return false
        }
        var adjustedChildLength = childLength
        val decorationLength =
            mFlexContainer.getDecorationLengthMainAxis(view, index, indexInFlexLine)
        if (decorationLength > 0) {
            adjustedChildLength += decorationLength
        }
        return maxSize < currentLength + adjustedChildLength
    }

    private fun isLastFlexItem(childIndex: Int, childCount: Int, flexLine: FlexLine): Boolean {
        return childIndex == childCount - 1 && flexLine.itemCountNotGone != 0
    }

    private fun addFlexLine(
        flexLines: MutableList<FlexLine>,
        flexLine: FlexLine,
        viewIndex: Int,
        usedCrossSizeSoFar: Int
    ) {
        flexLine.mSumCrossSizeBefore = usedCrossSizeSoFar
        mFlexContainer.onNewFlexLineAdded(flexLine)
        flexLine.mLastIndex = viewIndex
        flexLines.add(flexLine)
    }

    private fun checkSizeConstraints(view: View, index: Int) {
        var needsMeasure = false
        val flexItem = view.layoutParams as FlexItem
        var childWidth = view.measuredWidth
        var childHeight = view.measuredHeight

        if (childWidth < flexItem.minWidth) {
            needsMeasure = true
            childWidth = flexItem.minWidth
        } else if (childWidth > flexItem.maxWidth) {
            needsMeasure = true
            childWidth = flexItem.maxWidth
        }

        if (childHeight < flexItem.minHeight) {
            needsMeasure = true
            childHeight = flexItem.minHeight
        } else if (childHeight > flexItem.maxHeight) {
            needsMeasure = true
            childHeight = flexItem.maxHeight
        }
        if (needsMeasure) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(childWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            updateMeasureCache(index, widthSpec, heightSpec, view)
            mFlexContainer.updateViewCache(index, view)
        }
    }
    /**
     * @see [determineMainSize]
     */
    fun determineMainSize(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        determineMainSize(widthMeasureSpec, heightMeasureSpec, 0)
    }
    /**
     * Determine the main size by expanding (shrinking if negative remaining free space is given)
     * an individual child in each flex line if any children's mFlexGrow (or mFlexShrink if
     * remaining space is negative) properties are set to non-zero.
     */
    fun determineMainSize(widthMeasureSpec: Int, heightMeasureSpec: Int, fromIndex: Int) {
        ensureChildrenFrozen(mFlexContainer.flexItemCount)
        if (fromIndex >= mFlexContainer.flexItemCount) {
            return
        }
        val mainSize: Int
        val paddingAlongMainAxis: Int
        val flexDirection = mFlexContainer.flexDirection
        when (flexDirection) {
            FlexDirection.ROW, FlexDirection.ROW_REVERSE -> {
                val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
                val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
                val largestMainSize = mFlexContainer.largestMainSize
                mainSize = if (widthMode == View.MeasureSpec.EXACTLY) {
                    widthSize
                } else {
                    min(largestMainSize, widthSize)
                }
                paddingAlongMainAxis = paddingLeft + paddingRight
            }
            FlexDirection.COLUMN, FlexDirection.COLUMN_REVERSE -> {
                val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
                val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
                mainSize = if (heightMode == View.MeasureSpec.EXACTLY) {
                    heightSize
                } else {
                    mFlexContainer.largestMainSize
                }
                paddingAlongMainAxis = paddingTop + paddingBottom
            }
            else -> throw IllegalArgumentException("Invalid flex direction: $flexDirection")
        }
        var flexLineIndex = 0
        mIndexToFlexLine?.let { flexLineIndex = it[fromIndex] }
        val flexLines = mFlexContainer.flexLinesInternal
        for (i in flexLineIndex until flexLines.size) {
            val flexLine = flexLines[i]
            if (flexLine.mMainSize < mainSize && flexLine.mAnyItemsHaveFlexGrow) {
                expandFlexItems(
                    widthMeasureSpec, heightMeasureSpec, flexLine,
                    mainSize, paddingAlongMainAxis, false
                )
            } else if (flexLine.mMainSize > mainSize && flexLine.mAnyItemsHaveFlexShrink) {
                shrinkFlexItems(
                    widthMeasureSpec, heightMeasureSpec, flexLine,
                    mainSize, paddingAlongMainAxis, false
                )
            }
        }
    }

    private fun ensureChildrenFrozen(size: Int) {
        val childrenFrozen = mChildrenFrozen
        if (childrenFrozen == null) {
            mChildrenFrozen = BooleanArray(max(size, INITIAL_CAPACITY))
        } else if (childrenFrozen.size < size) {
            val newCapacity = childrenFrozen.size * 2
            mChildrenFrozen = BooleanArray(max(newCapacity, size))
        } else {
            Arrays.fill(childrenFrozen, false)
        }
    }

    private fun expandFlexItems(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        flexLine: FlexLine,
        maxMainSize: Int,
        paddingAlongMainAxis: Int,
        calledRecursively: Boolean
    ) {
        if (flexLine.mTotalFlexGrow <= 0 || maxMainSize < flexLine.mMainSize) {
            return
        }
        val sizeBeforeExpand = flexLine.mMainSize
        var needsReexpand = false
        val unitSpace = (maxMainSize - flexLine.mMainSize) / flexLine.mTotalFlexGrow
        flexLine.mMainSize = paddingAlongMainAxis + flexLine.mDividerLengthInMainSize
        var largestCrossSize = 0
        if (!calledRecursively) {
            flexLine.mCrossSize = Int.MIN_VALUE
        }
        var accumulatedRoundError = 0f
        for (i in 0 until flexLine.mItemCount) {
            val index = flexLine.mFirstIndex + i
            val child = mFlexContainer.getReorderedFlexItemAt(index)
            if (child == null || child.visibility == View.GONE) {
                continue
            }
            val flexItem = child.layoutParams as FlexItem
            val flexDirection = mFlexContainer.flexDirection
            if (flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE) {
                var childMeasuredWidth = child.measuredWidth
                mMeasuredSizeCache?.let { childMeasuredWidth = extractLowerInt(it[index]) }
                var childMeasuredHeight = child.measuredHeight
                mMeasuredSizeCache?.let { childMeasuredHeight = extractHigherInt(it[index]) }

                if (!mChildrenFrozen!![index] && flexItem.flexGrow > 0f) {
                    var rawCalculatedWidth = childMeasuredWidth + unitSpace * flexItem.flexGrow
                    if (rawCalculatedWidth < 0) {
                        rawCalculatedWidth = 0f
                    }
                    if (i == flexLine.mItemCount - 1) {
                        rawCalculatedWidth += accumulatedRoundError
                        accumulatedRoundError = 0f
                    }
                    var newWidth = rawCalculatedWidth.roundToInt()
                    if (newWidth > flexItem.maxWidth) {
                        needsReexpand = true
                        newWidth = flexItem.maxWidth
                        mChildrenFrozen!![index] = true
                        flexLine.mTotalFlexGrow -= flexItem.flexGrow
                    } else {
                        accumulatedRoundError += (rawCalculatedWidth - newWidth)
                        if (accumulatedRoundError > 1.0) {
                            newWidth += 1
                            accumulatedRoundError -= 1.0f
                        } else if (accumulatedRoundError < -1.0) {
                            newWidth -= 1
                            accumulatedRoundError += 1.0f
                        }
                    }
                    val childHeightMeasureSpec = getChildHeightMeasureSpecInternal(
                        heightMeasureSpec, flexItem, flexLine.mSumCrossSizeBefore
                    )
                    val childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                        newWidth, View.MeasureSpec.EXACTLY
                    )
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
                    childMeasuredWidth = child.measuredWidth
                    childMeasuredHeight = child.measuredHeight
                    updateMeasureCache(index, childWidthMeasureSpec, childHeightMeasureSpec, child)
                    mFlexContainer.updateViewCache(index, child)
                }
                largestCrossSize = max(
                    largestCrossSize, childMeasuredHeight +
                            flexItem.marginTop + flexItem.marginBottom +
                            mFlexContainer.getDecorationLengthCrossAxis(child)
                )
                flexLine.mMainSize += childMeasuredWidth + flexItem.marginLeft + flexItem.marginRight
            } else {
                var childMeasuredHeight = child.measuredHeight
                mMeasuredSizeCache?.let { childMeasuredHeight = extractHigherInt(it[index]) }
                var childMeasuredWidth = child.measuredWidth
                mMeasuredSizeCache?.let { childMeasuredWidth = extractLowerInt(it[index]) }

                if (!mChildrenFrozen!![index] && flexItem.flexGrow > 0f) {
                    var rawCalculatedHeight = childMeasuredHeight + unitSpace * flexItem.flexGrow
                    if (i == flexLine.mItemCount - 1) {
                        rawCalculatedHeight += accumulatedRoundError
                        accumulatedRoundError = 0f
                    }
                    var newHeight = rawCalculatedHeight.roundToInt()
                    if (newHeight > flexItem.maxHeight) {
                        needsReexpand = true
                        newHeight = flexItem.maxHeight
                        mChildrenFrozen!![index] = true
                        flexLine.mTotalFlexGrow -= flexItem.flexGrow
                    } else {
                        accumulatedRoundError += (rawCalculatedHeight - newHeight)
                        if (accumulatedRoundError > 1.0) {
                            newHeight += 1
                            accumulatedRoundError -= 1.0f
                        } else if (accumulatedRoundError < -1.0) {
                            newHeight -= 1
                            accumulatedRoundError += 1.0f
                        }
                    }
                    val childWidthMeasureSpec = getChildWidthMeasureSpecInternal(
                        widthMeasureSpec, flexItem, flexLine.mSumCrossSizeBefore
                    )
                    val childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                        newHeight, View.MeasureSpec.EXACTLY
                    )
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
                    childMeasuredWidth = child.measuredWidth
                    childMeasuredHeight = child.measuredHeight
                    updateMeasureCache(index, childWidthMeasureSpec, childHeightMeasureSpec, child)
                    mFlexContainer.updateViewCache(index, child)
                }
                largestCrossSize = max(
                    largestCrossSize, childMeasuredWidth +
                            flexItem.marginLeft + flexItem.marginRight +
                            mFlexContainer.getDecorationLengthCrossAxis(child)
                )
                flexLine.mMainSize += childMeasuredHeight + flexItem.marginTop + flexItem.marginBottom
            }
            flexLine.mCrossSize = max(flexLine.mCrossSize, largestCrossSize)
        }

        if (needsReexpand && sizeBeforeExpand != flexLine.mMainSize) {
            expandFlexItems(
                widthMeasureSpec, heightMeasureSpec, flexLine, maxMainSize,
                paddingAlongMainAxis, true
            )
        }
    }

    private fun shrinkFlexItems(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        flexLine: FlexLine,
        maxMainSize: Int,
        paddingAlongMainAxis: Int,
        calledRecursively: Boolean
    ) {
        val sizeBeforeShrink = flexLine.mMainSize
        if (flexLine.mTotalFlexShrink <= 0 || maxMainSize > flexLine.mMainSize) {
            return
        }
        var needsReshrink = false
        val unitShrink = (flexLine.mMainSize - maxMainSize) / flexLine.mTotalFlexShrink
        var accumulatedRoundError = 0f
        flexLine.mMainSize = paddingAlongMainAxis + flexLine.mDividerLengthInMainSize
        var largestCrossSize = 0
        if (!calledRecursively) {
            flexLine.mCrossSize = Int.MIN_VALUE
        }
        for (i in 0 until flexLine.mItemCount) {
            val index = flexLine.mFirstIndex + i
            val child = mFlexContainer.getReorderedFlexItemAt(index)
            if (child == null || child.visibility == View.GONE) {
                continue
            }
            val flexItem = child.layoutParams as FlexItem
            val flexDirection = mFlexContainer.flexDirection
            if (flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE) {
                var childMeasuredWidth = child.measuredWidth
                mMeasuredSizeCache?.let { childMeasuredWidth = extractLowerInt(it[index]) }
                var childMeasuredHeight = child.measuredHeight
                mMeasuredSizeCache?.let { childMeasuredHeight = extractHigherInt(it[index]) }

                if (!mChildrenFrozen!![index] && flexItem.flexShrink > 0f) {
                    var rawCalculatedWidth = childMeasuredWidth - unitShrink * flexItem.flexShrink
                    if (i == flexLine.mItemCount - 1) {
                        rawCalculatedWidth += accumulatedRoundError
                        accumulatedRoundError = 0f
                    }
                    var newWidth = rawCalculatedWidth.roundToInt()
                    if (newWidth < flexItem.minWidth) {
                        needsReshrink = true
                        newWidth = flexItem.minWidth
                        mChildrenFrozen!![index] = true
                        flexLine.mTotalFlexShrink -= flexItem.flexShrink
                    } else {
                        accumulatedRoundError += (rawCalculatedWidth - newWidth)
                        if (accumulatedRoundError > 1.0) {
                            newWidth += 1
                            accumulatedRoundError -= 1f
                        } else if (accumulatedRoundError < -1.0) {
                            newWidth -= 1
                            accumulatedRoundError += 1f
                        }
                    }
                    val childHeightMeasureSpec = getChildHeightMeasureSpecInternal(
                        heightMeasureSpec, flexItem, flexLine.mSumCrossSizeBefore
                    )
                    val childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                        newWidth, View.MeasureSpec.EXACTLY
                    )
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)

                    childMeasuredWidth = child.measuredWidth
                    childMeasuredHeight = child.measuredHeight
                    updateMeasureCache(index, childWidthMeasureSpec, childHeightMeasureSpec, child)
                    mFlexContainer.updateViewCache(index, child)
                }
                largestCrossSize = max(
                    largestCrossSize, childMeasuredHeight +
                            flexItem.marginTop + flexItem.marginBottom +
                            mFlexContainer.getDecorationLengthCrossAxis(child)
                )
                flexLine.mMainSize += childMeasuredWidth + flexItem.marginLeft + flexItem.marginRight
            } else {
                var childMeasuredHeight = child.measuredHeight
                mMeasuredSizeCache?.let { childMeasuredHeight = extractHigherInt(it[index]) }
                var childMeasuredWidth = child.measuredWidth
                mMeasuredSizeCache?.let { childMeasuredWidth = extractLowerInt(it[index]) }

                if (!mChildrenFrozen!![index] && flexItem.flexShrink > 0f) {
                    var rawCalculatedHeight = childMeasuredHeight - unitShrink * flexItem.flexShrink
                    if (i == flexLine.mItemCount - 1) {
                        rawCalculatedHeight += accumulatedRoundError
                        accumulatedRoundError = 0f
                    }
                    var newHeight = rawCalculatedHeight.roundToInt()
                    if (newHeight < flexItem.minHeight) {
                        needsReshrink = true
                        newHeight = flexItem.minHeight
                        mChildrenFrozen!![index] = true
                        flexLine.mTotalFlexShrink -= flexItem.flexShrink
                    } else {
                        accumulatedRoundError += (rawCalculatedHeight - newHeight)
                        if (accumulatedRoundError > 1.0) {
                            newHeight += 1
                            accumulatedRoundError -= 1f
                        } else if (accumulatedRoundError < -1.0) {
                            newHeight -= 1
                            accumulatedRoundError += 1f
                        }
                    }
                    val childWidthMeasureSpec = getChildWidthMeasureSpecInternal(
                        widthMeasureSpec, flexItem, flexLine.mSumCrossSizeBefore
                    )
                    val childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                        newHeight, View.MeasureSpec.EXACTLY
                    )
                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec)

                    childMeasuredWidth = child.measuredWidth
                    childMeasuredHeight = child.measuredHeight
                    updateMeasureCache(index, childWidthMeasureSpec, childHeightMeasureSpec, child)
                    mFlexContainer.updateViewCache(index, child)
                }
                largestCrossSize = max(
                    largestCrossSize, childMeasuredWidth +
                            flexItem.marginLeft + flexItem.marginRight +
                            mFlexContainer.getDecorationLengthCrossAxis(child)
                )
                flexLine.mMainSize += childMeasuredHeight + flexItem.marginTop + flexItem.marginBottom
            }
            flexLine.mCrossSize = max(flexLine.mCrossSize, largestCrossSize)
        }

        if (needsReshrink && sizeBeforeShrink != flexLine.mMainSize) {
            shrinkFlexItems(
                widthMeasureSpec, heightMeasureSpec, flexLine,
                maxMainSize, paddingAlongMainAxis, true
            )
        }
    }

    private fun getChildWidthMeasureSpecInternal(
        widthMeasureSpec: Int,
        flexItem: FlexItem,
        padding: Int
    ): Int {
        var childWidthMeasureSpec = mFlexContainer.getChildWidthMeasureSpec(
            widthMeasureSpec,
            paddingLeft + paddingRight +
                    flexItem.marginLeft + flexItem.marginRight + padding,
            flexItem.width
        )
        val childWidth = View.MeasureSpec.getSize(childWidthMeasureSpec)
        if (childWidth > flexItem.maxWidth) {
            childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                flexItem.maxWidth, View.MeasureSpec.getMode(childWidthMeasureSpec)
            )
        } else if (childWidth < flexItem.minWidth) {
            childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                flexItem.minWidth, View.MeasureSpec.getMode(childWidthMeasureSpec)
            )
        }
        return childWidthMeasureSpec
    }

    private fun getChildHeightMeasureSpecInternal(
        heightMeasureSpec: Int,
        flexItem: FlexItem,
        padding: Int
    ): Int {
        var childHeightMeasureSpec = mFlexContainer.getChildHeightMeasureSpec(
            heightMeasureSpec,
            paddingTop + paddingBottom +
                    flexItem.marginTop + flexItem.marginBottom + padding,
            flexItem.height
        )
        val childHeight = View.MeasureSpec.getSize(childHeightMeasureSpec)
        if (childHeight > flexItem.maxHeight) {
            childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                flexItem.maxHeight, View.MeasureSpec.getMode(childHeightMeasureSpec)
            )
        } else if (childHeight < flexItem.minHeight) {
            childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                flexItem.minHeight, View.MeasureSpec.getMode(childHeightMeasureSpec)
            )
        }
        return childHeightMeasureSpec
    }
    /**
     * Determines the cross size (Calculate the length along the cross axis).
     */
    fun determineCrossSize(
          widthMeasureSpec: Int,
          heightMeasureSpec: Int,
          paddingAlongCrossAxis: Int
    ) {
        val mode: Int
        val size: Int
        val flexDirection = mFlexContainer.flexDirection
        when (flexDirection) {
            FlexDirection.ROW, FlexDirection.ROW_REVERSE -> {
                mode = View.MeasureSpec.getMode(heightMeasureSpec)
                size = View.MeasureSpec.getSize(heightMeasureSpec)
            }
            FlexDirection.COLUMN, FlexDirection.COLUMN_REVERSE -> {
                mode = View.MeasureSpec.getMode(widthMeasureSpec)
                size = View.MeasureSpec.getSize(widthMeasureSpec)
            }
            else -> throw IllegalArgumentException("Invalid flex direction: $flexDirection")
        }
        val flexLines = mFlexContainer.flexLinesInternal
        if (mode == View.MeasureSpec.EXACTLY) {
            val totalCrossSize = mFlexContainer.sumOfCrossSize + paddingAlongCrossAxis
            if (flexLines.size == 1) {
                flexLines[0].mCrossSize = size - paddingAlongCrossAxis
            } else if (flexLines.size >= 2) {
                when (mFlexContainer.alignContent) {
                    AlignContent.STRETCH -> {
                        if (totalCrossSize >= size) {
                            return
                        }
                        val freeSpaceUnit = (size - totalCrossSize) / flexLines.size.toFloat()
                        var accumulatedError = 0f
                        for (i in flexLines.indices) {
                            val flexLine = flexLines[i]
                            var newCrossSizeAsFloat = flexLine.mCrossSize + freeSpaceUnit
                            if (i == flexLines.size - 1) {
                                newCrossSizeAsFloat += accumulatedError
                                accumulatedError = 0f
                            }
                            var newCrossSize = newCrossSizeAsFloat.roundToInt()
                            accumulatedError += (newCrossSizeAsFloat - newCrossSize)
                            if (accumulatedError > 1) {
                                newCrossSize += 1
                                accumulatedError -= 1f
                            } else if (accumulatedError < -1) {
                                newCrossSize -= 1
                                accumulatedError += 1f
                            }
                            flexLine.mCrossSize = newCrossSize
                        }
                    }
                    AlignContent.SPACE_AROUND -> {
                        if (totalCrossSize >= size) {
                            mFlexContainer.setFlexLines(
                                constructFlexLinesForAlignContentCenter(
                                    flexLines,
                                    size,
                                    totalCrossSize
                                )
                            )
                            return
                        }
                        val spaceTopAndBottom = (size - totalCrossSize) / (flexLines.size * 2)
                        val newFlexLines = ArrayList<FlexLine>()
                        val dummySpaceFlexLine = FlexLine()
                        dummySpaceFlexLine.mCrossSize = spaceTopAndBottom
                        for (flexLine in flexLines) {
                            newFlexLines.add(dummySpaceFlexLine)
                            newFlexLines.add(flexLine)
                            newFlexLines.add(dummySpaceFlexLine)
                        }
                        mFlexContainer.setFlexLines(newFlexLines)
                    }
                    AlignContent.SPACE_EVENLY -> {
                        if (totalCrossSize >= size) {
                            // If the size of the content is larger than the flex container, the
                            // Flex lines should be aligned center like ALIGN_CONTENT_CENTER
                            mFlexContainer.setFlexLines(
                                constructFlexLinesForAlignContentCenter(
                                    flexLines, size,
                                    totalCrossSize
                                )
                            );
                            return;
                        }
                        // The value of free space along the cross axis which needs to be put on top
                        // and below the bottom of each flex line.
                        var spaceTopAndBottom = size - totalCrossSize;
                        // The number of spaces along the cross axis
                        val numberOfSpaces = flexLines.size + 1
                        spaceTopAndBottom /= numberOfSpaces;
                        val newFlexLines: MutableList<FlexLine> = mutableListOf()
                        val dummySpaceFlexLine = FlexLine()
                        dummySpaceFlexLine.mCrossSize = spaceTopAndBottom;
                        var isFirstLine = true;
                        for (flexLine in flexLines) {
                            if (isFirstLine) {
                                newFlexLines.add(dummySpaceFlexLine);
                                isFirstLine = false;
                            }
                            newFlexLines.add(flexLine);
                            newFlexLines.add(dummySpaceFlexLine);
                        }
                        mFlexContainer.setFlexLines(newFlexLines);
                        return;
                    }
                    AlignContent.SPACE_BETWEEN -> {
                        if (totalCrossSize >= size) {
                            return
                        }
                        var spaceBetweenFlexLine = (size - totalCrossSize).toFloat()
                        val numberOfSpaces = flexLines.size - 1
                        spaceBetweenFlexLine /= numberOfSpaces.toFloat()
                        var accumulatedError = 0f
                        val newFlexLines = ArrayList<FlexLine>()
                        for (i in flexLines.indices) {
                            val flexLine = flexLines[i]
                            newFlexLines.add(flexLine)

                            if (i != flexLines.size - 1) {
                                val dummySpaceFlexLine = FlexLine()
                                if (i == flexLines.size - 2) {
                                    dummySpaceFlexLine.mCrossSize =
                                        (spaceBetweenFlexLine + accumulatedError).roundToInt()
                                    accumulatedError = 0f
                                } else {
                                    dummySpaceFlexLine.mCrossSize =
                                        spaceBetweenFlexLine.roundToInt()
                                }
                                accumulatedError += (spaceBetweenFlexLine - dummySpaceFlexLine.mCrossSize)
                                if (accumulatedError > 1) {
                                    dummySpaceFlexLine.mCrossSize += 1
                                    accumulatedError -= 1f
                                } else if (accumulatedError < -1) {
                                    dummySpaceFlexLine.mCrossSize -= 1
                                    accumulatedError += 1f
                                }
                                newFlexLines.add(dummySpaceFlexLine)
                            }
                        }
                        mFlexContainer.setFlexLines(newFlexLines)
                    }
                    AlignContent.CENTER -> {
                        mFlexContainer.setFlexLines(
                            constructFlexLinesForAlignContentCenter(flexLines, size, totalCrossSize)
                        )
                    }
                    AlignContent.FLEX_END -> {
                        val spaceTop = size - totalCrossSize
                        val dummySpaceFlexLine = FlexLine()
                        dummySpaceFlexLine.mCrossSize = spaceTop
                        (flexLines as MutableList).add(0, dummySpaceFlexLine)
                    }
                    AlignContent.FLEX_START -> {
                        // No op
                    }
                }
            }
        }
    }
    private fun constructFlexLinesForAlignContentCenter(
        flexLines: List<FlexLine>,
        size: Int,
        totalCrossSize: Int
    ): List<FlexLine> {
        val spaceAboveAndBottom = (size - totalCrossSize) / 2
        val newFlexLines = ArrayList<FlexLine>()
        val dummySpaceFlexLine = FlexLine()
        dummySpaceFlexLine.mCrossSize = spaceAboveAndBottom
        for (i in flexLines.indices) {
            if (i == 0) {
                newFlexLines.add(dummySpaceFlexLine)
            }
            val flexLine = flexLines[i]
            newFlexLines.add(flexLine)
            if (i == flexLines.size - 1) {
                newFlexLines.add(dummySpaceFlexLine)
            }
        }
        return newFlexLines
    }

    fun stretchViews() {
        stretchViews(0)
    }
    /**
     * Expand the view if the [FlexContainer.alignItems] attribute is set to
     * [AlignItems.STRETCH] or [FlexItem.alignSelf] is set as [AlignItems.STRETCH].
     */
    fun stretchViews(fromIndex: Int) {
        if (fromIndex >= mFlexContainer.flexItemCount) {
            return
        }
        val flexDirection = mFlexContainer.flexDirection
        if (mFlexContainer.alignItems == AlignItems.STRETCH) {
            var flexLineIndex = 0
            mIndexToFlexLine?.let { flexLineIndex = it[fromIndex] }
            val flexLines = mFlexContainer.flexLinesInternal
            for (i in flexLineIndex until flexLines.size) {
                val flexLine = flexLines[i]
                for (j in 0 until flexLine.mItemCount) {
                    val viewIndex = flexLine.mFirstIndex + j
                    if (j >= mFlexContainer.flexItemCount) {
                        continue
                    }
                    val view = mFlexContainer.getReorderedFlexItemAt(viewIndex)
                    if (view == null || view.isGone) {
                        continue
                    }
                    val flexItem = view.layoutParams as FlexItem
                    if (flexItem.alignSelf != AlignSelf.AUTO &&
                        flexItem.alignSelf != AlignItems.STRETCH
                    ) {
                        continue
                    }
                    when (flexDirection) {
                        FlexDirection.ROW, FlexDirection.ROW_REVERSE ->
                            stretchViewVertically(view, flexLine.mCrossSize, viewIndex)
                        FlexDirection.COLUMN, FlexDirection.COLUMN_REVERSE ->
                            stretchViewHorizontally(view, flexLine.mCrossSize, viewIndex)
                        else -> throw IllegalArgumentException("Invalid flex direction: $flexDirection")
                    }
                }
            }
        } else {
            for (flexLine in mFlexContainer.flexLinesInternal) {
                for (index in flexLine.mIndicesAlignSelfStretch) {
                    val view = mFlexContainer.getReorderedFlexItemAt(index) ?: continue
                    when (flexDirection) {
                        FlexDirection.ROW, FlexDirection.ROW_REVERSE ->
                            stretchViewVertically(view, flexLine.mCrossSize, index)
                        FlexDirection.COLUMN, FlexDirection.COLUMN_REVERSE ->
                            stretchViewHorizontally(view, flexLine.mCrossSize, index)
                        else -> throw IllegalArgumentException("Invalid flex direction: $flexDirection")
                    }
                }
            }
        }
    }

    private fun stretchViewVertically(view: View, crossSize: Int, index: Int) {
        val flexItem = view.layoutParams as FlexItem
        var newHeight = crossSize - flexItem.marginTop - flexItem.marginBottom -
                mFlexContainer.getDecorationLengthCrossAxis(view)
        newHeight = max(newHeight, flexItem.minHeight)
        newHeight = min(newHeight, flexItem.maxHeight)
        val measuredWidth =
            mMeasuredSizeCache?.let { extractLowerInt(it[index]) } ?: view.measuredWidth
        val childWidthSpec =
            View.MeasureSpec.makeMeasureSpec(measuredWidth, View.MeasureSpec.EXACTLY)
        val childHeightSpec = View.MeasureSpec.makeMeasureSpec(newHeight, View.MeasureSpec.EXACTLY)
        view.measure(childWidthSpec, childHeightSpec)

        updateMeasureCache(index, childWidthSpec, childHeightSpec, view)
        mFlexContainer.updateViewCache(index, view)
    }

    private fun stretchViewHorizontally(view: View, crossSize: Int, index: Int) {
        val flexItem = view.layoutParams as FlexItem
        var newWidth = crossSize - flexItem.marginLeft - flexItem.marginRight -
                mFlexContainer.getDecorationLengthCrossAxis(view)
        newWidth = max(newWidth, flexItem.minWidth)
        newWidth = min(newWidth, flexItem.maxWidth)
        val measuredHeight =
            mMeasuredSizeCache?.let { extractHigherInt(it[index]) } ?: view.measuredHeight
        val childHeightSpec =
            View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
        val childWidthSpec = View.MeasureSpec.makeMeasureSpec(newWidth, View.MeasureSpec.EXACTLY)
        view.measure(childWidthSpec, childHeightSpec)

        updateMeasureCache(index, childWidthSpec, childHeightSpec, view)
        mFlexContainer.updateViewCache(index, view)
    }
    /**
     * Place a single View when the layout direction is horizontal.
     */
    fun layoutSingleChildHorizontal(
        view: View,
        flexLine: FlexLine,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val flexItem = view.layoutParams as FlexItem
        var alignItems = mFlexContainer.alignItems
        if (flexItem.alignSelf != AlignSelf.AUTO) {
            alignItems = flexItem.alignSelf
        }
        val crossSize = flexLine.mCrossSize
        when (alignItems) {
            AlignItems.FLEX_START, AlignItems.STRETCH -> {
                if (mFlexContainer.flexWrap != FlexWrap.WRAP_REVERSE) {
                    view.layout(left, top + flexItem.marginTop, right, bottom + flexItem.marginTop)
                } else {
                    view.layout(
                        left,
                        top - flexItem.marginBottom,
                        right,
                        bottom - flexItem.marginBottom
                    )
                }
            }
            AlignItems.BASELINE -> {
                if (mFlexContainer.flexWrap != FlexWrap.WRAP_REVERSE) {
                    var marginTop = flexLine.mMaxBaseline - view.baseline
                    marginTop = max(marginTop, flexItem.marginTop)
                    view.layout(left, top + marginTop, right, bottom + marginTop)
                } else {
                    var marginBottom = flexLine.mMaxBaseline - view.measuredHeight + view.baseline
                    marginBottom = max(marginBottom, flexItem.marginBottom)
                    view.layout(left, top - marginBottom, right, bottom - marginBottom)
                }
            }
            AlignItems.FLEX_END -> {
                if (mFlexContainer.flexWrap != FlexWrap.WRAP_REVERSE) {
                    view.layout(
                        left,
                        top + crossSize - view.measuredHeight - flexItem.marginBottom,
                        right,
                        top + crossSize - flexItem.marginBottom
                    )
                } else {
                    view.layout(
                        left,
                        top - crossSize + view.measuredHeight + flexItem.marginTop,
                        right,
                        bottom - crossSize + view.measuredHeight + flexItem.marginTop
                    )
                }
            }
            AlignItems.CENTER -> {
                val topFromCrossAxis = (crossSize - view.measuredHeight +
                        flexItem.marginTop - flexItem.marginBottom) / 2
                if (mFlexContainer.flexWrap != FlexWrap.WRAP_REVERSE) {
                    view.layout(
                        left,
                        top + topFromCrossAxis,
                        right,
                        top + topFromCrossAxis + view.measuredHeight
                    )
                } else {
                    view.layout(
                        left,
                        top - topFromCrossAxis,
                        right,
                        top - topFromCrossAxis + view.measuredHeight
                    )
                }
            }
        }
    }
    /**
     * Place a single View when the layout direction is vertical.
     */
    fun layoutSingleChildVertical(
        view: View,
        flexLine: FlexLine,
        isRtl: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val flexItem = view.layoutParams as FlexItem
        var alignItems = mFlexContainer.alignItems
        if (flexItem.alignSelf != AlignSelf.AUTO) {
            alignItems = flexItem.alignSelf
        }
        val crossSize = flexLine.mCrossSize
        when (alignItems) {
            AlignItems.FLEX_START, AlignItems.STRETCH, AlignItems.BASELINE -> {
                if (!isRtl) {
                    view.layout(
                        left + flexItem.marginLeft,
                        top,
                        right + flexItem.marginLeft,
                        bottom
                    )
                } else {
                    view.layout(
                        left - flexItem.marginRight,
                        top,
                        right - flexItem.marginRight,
                        bottom
                    )
                }
            }
            AlignItems.FLEX_END -> {
                if (!isRtl) {
                    view.layout(
                        left + crossSize - view.measuredWidth - flexItem.marginRight,
                        top,
                        right + crossSize - view.measuredWidth - flexItem.marginRight,
                        bottom
                    )
                } else {
                    view.layout(
                        left - crossSize + view.measuredWidth + flexItem.marginLeft,
                        top,
                        right - crossSize + view.measuredWidth + flexItem.marginLeft,
                        bottom
                    )
                }
            }
            AlignItems.CENTER -> {
                val lp = view.layoutParams as ViewGroup.MarginLayoutParams
                val leftFromCrossAxis = (crossSize - view.measuredWidth +
                        lp.marginStart -
                        lp.marginEnd) / 2
                if (!isRtl) {
                    view.layout(left + leftFromCrossAxis, top, right + leftFromCrossAxis, bottom)
                } else {
                    view.layout(left - leftFromCrossAxis, top, right - leftFromCrossAxis, bottom)
                }
            }
        }
    }

    fun ensureMeasuredSizeCache(size: Int) {
        val measuredSizeCache = mMeasuredSizeCache
        if (measuredSizeCache == null) {
            mMeasuredSizeCache = LongArray(max(size, INITIAL_CAPACITY))
        } else if (measuredSizeCache.size < size) {
            var newCapacity = measuredSizeCache.size * 2
            newCapacity = max(newCapacity, size)
            mMeasuredSizeCache = Arrays.copyOf(measuredSizeCache, newCapacity)
        }
    }

    fun ensureMeasureSpecCache(size: Int) {
        val measureSpecCache = mMeasureSpecCache
        if (measureSpecCache == null) {
            mMeasureSpecCache = LongArray(max(size, INITIAL_CAPACITY))
        } else if (measureSpecCache.size < size) {
            var newCapacity = measureSpecCache.size * 2
            newCapacity = max(newCapacity, size)
            mMeasureSpecCache = Arrays.copyOf(measureSpecCache, newCapacity)
        }
    }

    fun extractLowerInt(longValue: Long): Int {
        return longValue.toInt()
    }

    fun extractHigherInt(longValue: Long): Int {
        return (longValue shr 32).toInt()
    }
    @VisibleForTesting
    fun makeCombinedLong(widthMeasureSpec: Int, heightMeasureSpec: Int): Long {
        return heightMeasureSpec.toLong() shl 32 or (widthMeasureSpec.toLong() and MEASURE_SPEC_WIDTH_MASK)
    }

    private fun updateMeasureCache(
        index: Int,
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        view: View
    ) {
        mMeasureSpecCache?.let { it[index] = makeCombinedLong(widthMeasureSpec, heightMeasureSpec) }
        mMeasuredSizeCache?.let {
            it[index] = makeCombinedLong(view.measuredWidth, view.measuredHeight)
        }
    }

    fun ensureIndexToFlexLine(size: Int) {
        val indexToFlexLine = mIndexToFlexLine
        if (indexToFlexLine == null) {
            mIndexToFlexLine = IntArray(max(size, INITIAL_CAPACITY))
        } else if (indexToFlexLine.size < size) {
            var newCapacity = indexToFlexLine.size * 2
            newCapacity = max(newCapacity, size)
            mIndexToFlexLine = Arrays.copyOf(indexToFlexLine, newCapacity)
        }
    }
    /**
     * Clear the from flex lines and the caches from the index passed as an argument.
     */
    fun clearFlexLines(flexLines: MutableList<FlexLine>, fromFlexItem: Int) {
        val indexToFlexLine = mIndexToFlexLine!!
        val measureSpecCache = mMeasureSpecCache!!
        var fromFlexLine = indexToFlexLine[fromFlexItem]
        if (fromFlexLine == RecyclerView.NO_POSITION) {
            fromFlexLine = 0
        }

        if (flexLines.size > fromFlexLine) {
            flexLines.subList(fromFlexLine, flexLines.size).clear()
        }
        val fillToIndex = indexToFlexLine.size - 1
        if (fromFlexItem > fillToIndex) {
            Arrays.fill(indexToFlexLine, RecyclerView.NO_POSITION)
        } else {
            Arrays.fill(indexToFlexLine, fromFlexItem, fillToIndex, RecyclerView.NO_POSITION)
        }
        val fillToSpec = measureSpecCache.size - 1
        if (fromFlexItem > fillToSpec) {
            Arrays.fill(measureSpecCache, 0)
        } else {
            Arrays.fill(measureSpecCache, fromFlexItem, fillToSpec, 0)
        }
    }
    /**
     * A class that is used for calculating the view order which view's indices and order
     * properties from Flexbox are taken into account.
     */
    private class Order : Comparable<Order> {
        /** [View]'s index */
        var index: Int = 0
        /** order property in the Flexbox */
        var order: Int = 0

        override fun compareTo(other: Order): Int {
            return if (order != other.order) {
                order - other.order
            } else {
                index - other.index
            }
        }

        override fun toString(): String {
            return "Order{order=$order, index=$index}"
        }
    }

    class FlexLinesResult {
        @JvmField
        var mFlexLines: MutableList<FlexLine> = mutableListOf()
        @JvmField
        var mChildState: Int = 0

        fun reset() {
            mFlexLines = mutableListOf()
            mChildState = 0
        }
    }

    companion object {
        private const val INITIAL_CAPACITY = 10
        private const val MEASURE_SPEC_WIDTH_MASK = 0xffffffffL
    }
}
