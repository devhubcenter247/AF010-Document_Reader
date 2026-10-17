package com.af010.creator.docreader.ui.splash

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySplashBinding
import com.af010.creator.docreader.ui.entry.AppShortcut
import com.af010.creator.docreader.ui.entry.PendingLaunch
import com.af010.creator.docreader.ui.language.LanguageActivity
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.main.files.OpenedFiles
import com.ui.baselib.lifecycle.runAfter
import kotlinx.coroutines.launch

/**
 * The only way into the app: the launcher icon, the launcher shortcuts ([AppShortcut]) and
 * documents opened from other apps (`ACTION_VIEW`) all start here.
 */
class SplashActivity : BaseAppActivity<ActivitySplashBinding>(ActivitySplashBinding::inflate) {
    override val fullStatus: Boolean
        get() = true
    override fun backPressed() {
      
    }

    override fun initialize() {
        val shortcut = AppShortcut.from(intent)
        val document = intent?.data?.takeIf { intent.action == Intent.ACTION_VIEW }
        when {
            document != null -> lifecycleScope.launch {
                // Copied here: the permission to read the uri ends with this activity.
                val file = OpenedFiles.resolve(applicationContext, document, intent.type)
                when {
                    file == null -> {
                        toast(R.string.error_file_open_generic)
                        finish()
                    }
                    // Straight to the reader, so that back returns to the app the file came from.
                    preferenceHelper.isFinishFirstFlow -> {
                        if (!OpenedFiles.open(this@SplashActivity, file)) toast(R.string.file_open_with_failed)
                        finish()
                    }
                    else -> {
                        PendingLaunch.file = file
                        launchAndFinish<LanguageActivity>()
                    }
                }
            }
            shortcut != null -> {
                PendingLaunch.shortcut = shortcut
                openNext()
            }
            else -> {
                // A plain start drops a request left by a first run that was never finished.
                PendingLaunch.clear()
                runAfter(1000) { openNext() }
            }
        }
    }

    private fun openNext() {
        if (preferenceHelper.isFinishFirstFlow) launchAndFinish<MainActivity>()
        else launchAndFinish<LanguageActivity>()
    }

    override fun ActivitySplashBinding.setData() {
      
    }

    override fun ActivitySplashBinding.onClick() {
      
    }

}
