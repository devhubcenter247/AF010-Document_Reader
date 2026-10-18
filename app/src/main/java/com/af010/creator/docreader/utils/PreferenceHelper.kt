package com.af010.creator.docreader.utils

import com.af010.creator.docreader.model.SortByData
import com.af010.creator.docreader.model.SortOrder
import com.af010.creator.docreader.model.ThemeMode
import com.af010.creator.docreader.model.UseCase

class PreferenceHelper(preference: SharedPreference) {

    var isRatedApp = preference.observable("isRatedApp", false)
    var languageSelected by preference.string("language_code", "en")
    var isFinishFirstFlow by preference.boolean("is_finish_first_flow", false)

    var countSessionApp by preference.int("count_session_app", 0)
    var countUserSession by preference.int("countUserSession", 0)
    var isShowTutorial by preference.boolean("is_show_tutorial", false)
    var themeMode by preference.enum("theme_mode", ThemeMode.System)
    var sortBy by preference.enum("sort_by", SortByData.SortByDate)
    var sortOrder by preference.enum("sort_order", SortOrder.Decrease)
    var useCases by preference.json("use_cases", listOf(UseCase.Work.id))
    var recentSearches by preference.json("recent_searches", emptyList<String>())
    /** ML Kit tags of the languages translated into lately, newest first. */
    var recentTranslateTargets by preference.json("recent_translate_targets", emptyList<String>())
    fun isUfo(): Boolean = countSessionApp == 0

}