package com.ui.baselib.widget.flexbox

import android.os.Parcelable
import android.view.View
import androidx.annotation.IntDef

internal interface FlexItem : Parcelable {
    var width: Int
    var height: Int
    var order: Int
    var flexGrow: Float
    var flexShrink: Float
    @get:AlignSelf
    var alignSelf: Int
    var minWidth: Int
    var minHeight: Int
    var maxWidth: Int
    var maxHeight: Int
    var isWrapBefore: Boolean
    var flexBasisPercent: Float
    val marginLeft: Int
    val marginTop: Int
    val marginRight: Int
    val marginBottom: Int
    val marginStarts: Int
    val marginEnds: Int

    companion object {
        const val ORDER_DEFAULT = 1
        const val FLEX_GROW_DEFAULT = 0f
        const val FLEX_SHRINK_DEFAULT = 1f
        const val FLEX_SHRINK_NOT_SET = 0f
        const val FLEX_BASIS_PERCENT_DEFAULT = -1f
        const val MAX_SIZE = Int.MAX_VALUE and View.MEASURED_SIZE_MASK
    }
}
@IntDef(FlexWrap.NOWRAP, FlexWrap.WRAP, FlexWrap.WRAP_REVERSE)
@Retention(AnnotationRetention.SOURCE)
annotation class FlexWrap {
    companion object {
        const val NOWRAP = 0
        const val WRAP = 1
        const val WRAP_REVERSE = 2
    }
}
@IntDef(
    JustifyContent.FLEX_START,
    JustifyContent.FLEX_END,
    JustifyContent.CENTER,
    JustifyContent.SPACE_BETWEEN,
    JustifyContent.SPACE_AROUND,
    JustifyContent.SPACE_EVENLY
)
@Retention(AnnotationRetention.SOURCE)
annotation class JustifyContent {
    companion object {
        const val FLEX_START = 0
        const val FLEX_END = 1
        const val CENTER = 2
        const val SPACE_BETWEEN = 3
        const val SPACE_AROUND = 4
        const val SPACE_EVENLY = 5
    }
}
@IntDef(
    FlexDirection.ROW,
    FlexDirection.ROW_REVERSE,
    FlexDirection.COLUMN,
    FlexDirection.COLUMN_REVERSE
)
@Retention(AnnotationRetention.SOURCE)
annotation class FlexDirection {
    companion object {
        const val ROW = 0
        const val ROW_REVERSE = 1
        const val COLUMN = 2
        const val COLUMN_REVERSE = 3
    }
}
