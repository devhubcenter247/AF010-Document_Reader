package com.ui.baselib.widget.flexbox

import android.view.View

internal interface FlexContainer {
    val flexItemCount: Int

    fun getFlexItemAt(index: Int): View?

    fun getReorderedFlexItemAt(index: Int): View?

    fun addView(view: View)

    fun addView(view: View, index: Int)

    fun removeAllViews()

    fun removeViewAt(index: Int)
    @get:FlexDirection
    @setparam:FlexDirection
    var flexDirection: Int
    @get:FlexWrap
    @setparam:FlexWrap
    var flexWrap: Int
    @get:JustifyContent
    @setparam:JustifyContent
    var justifyContent: Int
    @get:AlignContent
    @setparam:AlignContent
    var alignContent: Int
    @get:AlignItems
    @setparam:AlignItems
    var alignItems: Int
    val flexLines: List<FlexLine>

    fun isMainAxisDirectionHorizontal(): Boolean

    fun getDecorationLengthMainAxis(view: View, index: Int, indexInFlexLine: Int): Int

    fun getDecorationLengthCrossAxis(view: View): Int

    fun getChildWidthMeasureSpec(widthSpec: Int, padding: Int, childDimension: Int): Int

    fun getChildHeightMeasureSpec(heightSpec: Int, padding: Int, childDimension: Int): Int

    val largestMainSize: Int
    val sumOfCrossSize: Int

    fun onNewFlexItemAdded(view: View, index: Int, indexInFlexLine: Int, flexLine: FlexLine)

    fun onNewFlexLineAdded(flexLine: FlexLine)

    fun setFlexLines(flexLines: List<FlexLine>)

    var maxLine: Int
    val flexLinesInternal: List<FlexLine>

    fun updateViewCache(position: Int, view: View)

    companion object {
        const val NOT_SET = -1
    }
}