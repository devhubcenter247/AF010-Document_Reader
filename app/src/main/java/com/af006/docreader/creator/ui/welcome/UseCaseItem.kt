package com.af010.creator.docreader.ui.welcome

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class UseCaseItem(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:ColorRes val color: Int,
    val isSelected: Boolean = false
)
