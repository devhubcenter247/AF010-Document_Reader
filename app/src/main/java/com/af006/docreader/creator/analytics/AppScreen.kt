package com.af010.creator.docreader.analytics

/** Stable event names: keep these unchanged so historical reports remain comparable. */
enum class AppScreen(val eventName: String) {
    SPLASH("ufo_splash"),
    LANGUAGE_1("ufo_language_1"),
    LANGUAGE_2("ufo_language_2"),
    ONBOARDING("ufo_onboarding"),
    PERSONALIZATION("ufo_personalization"),
    MAIN("ufo_main"),

}
