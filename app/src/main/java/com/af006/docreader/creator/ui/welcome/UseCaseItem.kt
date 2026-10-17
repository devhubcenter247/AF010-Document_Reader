package com.af010.creator.docreader.ui.welcome

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class UseCaseItem(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorInt val iconBgColor: Int,
    val isSelected: Boolean = false
)
