package com.af010.creator.docreader.ui.main.settings

import androidx.appcompat.app.AppCompatDelegate
import com.af010.creator.docreader.BuildConfig
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentSettingsBinding
import com.af010.creator.docreader.model.UseCase
import com.af010.creator.docreader.ui.language.LanguageActivity
import com.af010.creator.docreader.ui.language.listLanguage
import com.af010.creator.docreader.ui.welcome.WelcomeActivity
import com.af010.creator.docreader.utils.PreferenceHelper
import com.d010.pdf.reader.base.Aso
import com.d010.pdf.reader.base.openUrl
import com.d010.pdf.reader.base.sendFeedBack
import com.d010.pdf.reader.base.shareApp
import com.af010.creator.docreader.app.rateApp
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.ui.main.premium.SubscriptionActivity
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import org.koin.android.ext.android.inject

class SettingsFragment : BaseAppFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    private val preferenceHelper: PreferenceHelper by inject()

    override fun FragmentSettingsBinding.initView() {
        val paywall = remoteConfig.paywallEnabled
        cardPremium.visibleOrGone(paywall)
        tvPremiumSection.visibleOrGone(paywall)
        cardPremiumRows.visibleOrGone(paywall)
        tvVersion.text = getString(
            R.string.version_info, getString(R.string.home_subtitle), BuildConfig.VERSION_NAME
        )
    }

    override fun FragmentSettingsBinding.onClick() {
        btnTrial.clickSingle { PaywallActivity.start(requireActivity()) }
        rowRestore.clickSingle { toast(R.string.restore_nothing) }
        rowSubscription.clickSingle { launchActivity<SubscriptionActivity>() }

        cardLanguage.clickSingle { launchActivity<LanguageActivity>() }
        cardTheme.clickSingle {
            ThemeBottomSheet(requireActivity(), preferenceHelper.themeMode) { mode ->
                preferenceHelper.themeMode = mode
                AppCompatDelegate.setDefaultNightMode(mode.nightMode)
            }.show()
        }
        val stars = listOf(btnStar1, btnStar2, btnStar3, btnStar4, btnStar5)
        stars.forEachIndexed { index, star ->
            star.clickSingle {
                stars.forEachIndexed { i, view ->
                    view.setImageResource(if (i <= index) R.drawable.ic_app_star_filled else R.drawable.ic_app_star)
                }
                if (index + 1 >= REVIEW_RATING) requireActivity().rateApp {}
                else requireActivity().sendFeedBack()
            }
        }
        rowShare.clickSingle { appContext().shareApp() }
        rowFeedback.clickSingle { requireActivity().sendFeedBack() }
        rowPrivacy.clickSingle { appContext().openUrl(Aso.POLICY_LINK) }
        rowTerms.clickSingle { appContext().openUrl(Aso.TEAM_SERVICE) }
    }

    override fun onResume() {
        super.onResume()
        bindingOrNull?.showPreferences()
    }

    private fun FragmentSettingsBinding.showPreferences() {
        tvLanguage.text = listLanguage
            .firstOrNull { it.code == preferenceHelper.languageSelected }?.name.orEmpty()
        tvTheme.setText(preferenceHelper.themeMode.title)
    }

    private companion object {
        const val REVIEW_RATING = 4
    }
}
