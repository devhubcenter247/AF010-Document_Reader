package com.af010.creator.docreader.model

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.af010.creator.docreader.R

enum class ThemeMode(@StringRes val title: Int, val nightMode: Int) {
    Light(R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO),
    Dark(R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES),
    System(R.string.theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
}
