package com.af010.creator.docreader.utils

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Closes a system settings screen by itself once the permission asked for is granted there, so
 * the user does not have to find the way back. The settings screen has to be started for a
 * result: that keeps it in the app's task, where the app is allowed to come back over it.
 */
object PermissionSettingsWatcher {
    private const val POLL_MS = 300L
    private const val OPEN_TIMEOUT_MS = 3_000L

    /** Call right after launching the settings screen from [context] (an activity). */
    fun watch(context: Context, isGranted: () -> Boolean) {
        val activity = context as? ComponentActivity ?: return
        activity.lifecycleScope.launch {
            var left = false
            var waited = 0L
            while (true) {
                delay(POLL_MS)
                waited += POLL_MS
                val resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!resumed) left = true
                // Back by hand, or the settings screen never opened.
                else if (left || waited >= OPEN_TIMEOUT_MS) return@launch
                if (left && isGranted()) {
                    // Clears the settings screen above the activity; its result is still delivered.
                    activity.startActivity(
                        Intent(activity, activity.javaClass).addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )
                    return@launch
                }
            }
        }
    }
}
