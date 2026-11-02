package com.af010.creator.docreader.ui.language

import android.content.Intent
import android.content.res.Resources
import androidx.recyclerview.widget.SimpleItemAnimator
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
        // Opened again from Settings, the first-run steps make no sense.
        steps.root.isVisible = !preferenceHelper.isFinishFirstFlow
        steps.show(current = 1, total = 3)
        (rcvLangue.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        rcvLangue.adapter = languageAdapter
        rcvLangue.addItemDecoration(CardGroupDecoration())
        val savedCode =
            preferenceHelper.languageSelected.takeIf { preferenceHelper.isFinishFirstFlow }
        val deviceLocale = Resources.getSystem().configuration.locales[0]
        languageAdapter.submitList(listLanguage.toUiModels(savedCode, deviceLocale))
    }

    override fun ActivityLanguageBinding.onClick() {

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
