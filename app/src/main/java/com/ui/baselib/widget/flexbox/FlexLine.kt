package com.ui.baselib.widget.flexbox

import android.view.View
import kotlin.math.max
import kotlin.math.min

class FlexLine internal constructor() {

    @JvmField
    internal var mLeft: Int = Int.MAX_VALUE

    @JvmField
    internal var mTop: Int = Int.MAX_VALUE

    @JvmField
    internal var mRight: Int = Int.MIN_VALUE

    @JvmField
    internal var mBottom: Int = Int.MIN_VALUE

    /** @see [mainSize] */
    @JvmField
    internal var mMainSize: Int = 0

    /**
     * The sum of the lengths of dividers along the main axis. This value should be lower
     * than the value of [mMainSize].
     */
    @JvmField
    internal var mDividerLengthInMainSize: Int = 0

    /** @see [crossSize] */
    @JvmField
    internal var mCrossSize: Int = 0

    /** @see [itemCount] */
    @JvmField
    internal var mItemCount: Int = 0

    /** Holds the count of the views whose visibilities are gone */
    @JvmField
    internal var mGoneItemCount: Int = 0

    /** @see [totalFlexGrow] */
    @JvmField
    internal var mTotalFlexGrow: Float = 0f

    /** @see [totalFlexShrink] */
    @JvmField
    internal var mTotalFlexShrink: Float = 0f

    /**
     * The largest value of the individual child's baseline (obtained by View#getBaseline()
     * if the [FlexContainer.alignItems] value is not [AlignItems.Companion.BASELINE]
     * or the flex direction is vertical, this value is not used.
     * If the alignment direction is from the bottom to top,
     * (e.g. flexWrap == WRAP_REVERSE and flexDirection == ROW)
     * store this value from the distance from the bottom of the view minus baseline.
     * (Calculated as view.getMeasuredHeight() - view.getBaseline - LayoutParams.bottomMargin)
     */
    @JvmField
    internal var mMaxBaseline: Int = 0

    /**
     * The sum of the cross size used before this flex line.
     */
    @JvmField
    internal var mSumCrossSizeBefore: Int = 0

    /**
     * Store the indices of the children views whose alignSelf property is stretch.
     * The stored indices are the absolute indices including all children in the Flexbox,
     * not the relative indices in this flex line.
     */
    @JvmField
    internal var mIndicesAlignSelfStretch: MutableList<Int> = ArrayList()

    @JvmField
    internal var mFirstIndex: Int = 0

    @JvmField
    internal var mLastIndex: Int = 0

    /**
     * Set to true if any [FlexItem]s in this line have [FlexItem.flexGrow]
     * attributes set (have the value other than [FlexItem.Companion.FLEX_GROW_DEFAULT])
     */
    @JvmField
    internal var mAnyItemsHaveFlexGrow: Boolean = false

    /**
     * Set to true if any [FlexItem]s in this line have [FlexItem.flexShrink]
     * attributes set (have the value other than [FlexItem.Companion.FLEX_SHRINK_NOT_SET])
     */
    @JvmField
    internal var mAnyItemsHaveFlexShrink: Boolean = false

    /**
     * The size of the flex line in pixels along the main axis of the flex container.
     */
    val mainSize: Int
        get() = mMainSize

    /**
     * The size of the flex line in pixels along the cross axis of the flex container.
     */
    val crossSize: Int
        get() = mCrossSize

    /**
     * The count of the views contained in this flex line.
     */
    val itemCount: Int
        get() = mItemCount

    /**
     * The count of the views whose visibilities are not gone in this flex line.
     */
    val itemCountNotGone: Int
        get() = mItemCount - mGoneItemCount

    /**
     * The sum of the flexGrow properties of the children included in this flex line
     */
    val totalFlexGrow: Float
        get() = mTotalFlexGrow

    /**
     * The sum of the flexShrink properties of the children included in this flex line
     */
    val totalFlexShrink: Float
        get() = mTotalFlexShrink

    /**
     * The first view's index included in this flex line.
     */
    val firstIndex: Int
        get() = mFirstIndex

    /**
     * Updates the position of the flex line from the contained view.
     *
     * @param view             the view contained in this flex line
     * @param leftDecoration   the length of the decoration on the left of the view
     * @param topDecoration    the length of the decoration on the top of the view
     * @param rightDecoration  the length of the decoration on the right of the view
     * @param bottomDecoration the length of the decoration on the bottom of the view
     */
    internal fun updatePositionFromView(
        view: View,
        leftDecoration: Int,
        topDecoration: Int,
        rightDecoration: Int,
        bottomDecoration: Int
    ) {
        val flexItem = view.layoutParams as FlexItem
        mLeft = min(mLeft, view.left - flexItem.marginLeft - leftDecoration)
        mTop = min(mTop, view.top - flexItem.marginTop - topDecoration)
        mRight = max(mRight, view.right + flexItem.marginRight + rightDecoration)
        mBottom = max(mBottom, view.bottom + flexItem.marginBottom + bottomDecoration)
    }
}
