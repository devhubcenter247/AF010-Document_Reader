package com.af010.creator.docreader.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object ScreenAnalytics {
    fun track(context: Context, screen: AppScreen) {
        FirebaseAnalytics.getInstance(context.applicationContext).logEvent(
            screen.eventName,
            Bundle().apply { putString("screen_name", screen.eventName.removePrefix("screen_")) }
        )
    }
}