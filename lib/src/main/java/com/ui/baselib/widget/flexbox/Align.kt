package com.ui.baselib.widget.flexbox

import androidx.annotation.IntDef


@IntDef(
    AlignItems.FLEX_START,
    AlignItems.FLEX_END,
    AlignItems.CENTER,
    AlignItems.BASELINE,
    AlignItems.STRETCH,
    AlignSelf.AUTO
)
@Retention(AnnotationRetention.SOURCE)
annotation class AlignSelf {
    companion object {

        const val AUTO = -1
        const val FLEX_START = AlignItems.FLEX_START
        const val FLEX_END = AlignItems.FLEX_END
        const val CENTER = AlignItems.CENTER
        const val BASELINE = AlignItems.BASELINE
        const val STRETCH = AlignItems.STRETCH
    }
}
@IntDef(
    AlignContent.FLEX_START,
    AlignContent.FLEX_END,
    AlignContent.CENTER,
    AlignContent.SPACE_BETWEEN,
    AlignContent.SPACE_AROUND,
    AlignContent.STRETCH,
    AlignContent.SPACE_EVENLY
)
@Retention(AnnotationRetention.SOURCE)
annotation class AlignContent {
    companion object {
        const val FLEX_START = 0
        const val FLEX_END = 1
        const val CENTER = 2

        const val SPACE_BETWEEN = 3

        const val SPACE_AROUND = 4
        const val STRETCH = 5

        const val SPACE_EVENLY =6
    }
}
@IntDef(
    AlignItems.FLEX_START,
    AlignItems.FLEX_END,
    AlignItems.CENTER,
    AlignItems.BASELINE,
    AlignItems.STRETCH
)
@Retention(AnnotationRetention.SOURCE)
annotation class AlignItems {
    companion object {
        const val FLEX_START = 0
        const val FLEX_END = 1
        const val CENTER = 2
        const val BASELINE = 3
        const val STRETCH = 4
    }
}

