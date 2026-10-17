package com.af010.creator.docreader.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class OnboardingItem(
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
)
