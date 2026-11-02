package com.af010.creator.docreader.ui.language

import android.content.Intent
import android.content.res.Resources
import androidx.recyclerview.widget.SimpleItemAnimator
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityLanguageBinding
import com.af010.creator.docreader.ui.main.common.CardGroupDecoration
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.onboarding.OnboardingActivity
import com.ui.baselib.extensions.clickSingle
import androidx.core.view.isVisible
import com.af010.creator.docreader.ui.common.show

class LanguageActivity :
    BaseAppActivity<ActivityLanguageBinding>(ActivityLanguageBinding::inflate) {
    override fun backPressed() {
        // Opened from Settings: back returns there. On first run there is nothing behind.
        if (preferenceHelper.isFinishFirstFlow) finish() else finishAffinity()
    }

    val languageAdapter by lazy {
        LanguageAdapter()
    }

    override fun initialize() = Unit

    override fun ActivityLanguageBinding.setData() {
        // Opened again from Settings: no greeting, and the choice is applied rather than a step.
        val fromSettings = preferenceHelper.isFinishFirstFlow
        btnBack.isVisible = fromSettings
        if (fromSettings) {
            tvTitle.setText(R.string.language)
            tvSubtitle.setText(R.string.fo_language_change_subtitle)
            btnContinue.setText(R.string.apply)
        }
        (rcvLangue.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rcvLangue.adapter = languageAdapter
        rcvLangue.addItemDecoration(CardGroupDecoration())
        val savedCode =
            preferenceHelper.languageSelected.takeIf { preferenceHelper.isFinishFirstFlow }
        val deviceLocale = Resources.getSystem().configuration.locales[0]
        languageAdapter.submitList(listLanguage.toUiModels(savedCode, deviceLocale))
    }

    override fun ActivityLanguageBinding.onClick() {
        btnBack.clickSingle { onBackPressedDispatcher.onBackPressed() }

        btnContinue.clickSingle {
            val selected = languageAdapter.selectedItem ?: return@clickSingle
            preferenceHelper.languageSelected = selected.item.code
            if (!preferenceHelper.isFinishFirstFlow) {
                launchAndFinish<OnboardingActivity>()
            } else {
                startActivity(
                    Intent(this@LanguageActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
            finishAffinity()
        }
    }

}
