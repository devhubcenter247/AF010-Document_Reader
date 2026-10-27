package com.af010.creator.docreader.ui.main.settings

import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.af010.creator.docreader.BuildConfig
import com.af010.creator.docreader.R
import com.af010.creator.docreader.app.rateApp
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentSettingsBinding
import com.af010.creator.docreader.databinding.LayoutSettingsRowBinding
import com.af010.creator.docreader.databinding.LayoutThemeOptionBinding
import com.af010.creator.docreader.model.ThemeMode
import com.af010.creator.docreader.model.UseCase
import com.af010.creator.docreader.ui.language.LanguageActivity
import com.af010.creator.docreader.ui.language.listLanguage
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.ui.welcome.WelcomeActivity
import com.af010.creator.docreader.utils.PreferenceHelper
import com.d010.pdf.reader.base.Aso
import com.d010.pdf.reader.base.openUrl
import com.d010.pdf.reader.base.sendFeedBack
import com.d010.pdf.reader.base.shareApp
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject

class SettingsFragment : BaseAppFragment<FragmentSettingsBinding>(FragmentSettingsBinding::inflate) {

    private val preferenceHelper: PreferenceHelper by inject()

    /** The previews' order: Auto, Light, Dark. */
    private val themes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)

    private val FragmentSettingsBinding.themeOptions get() = listOf(themeAuto, themeLight, themeDark)

    override fun FragmentSettingsBinding.initView() {
        cardPremium.isVisible = remoteConfig.paywallEnabled
        rowLanguage.describe(R.drawable.ic_fo_globe, R.color.primary, R.string.language)
        rowUseCases.describe(R.drawable.ic_fo_heart, R.color.color_file_pdf, R.string.fo_i_use_for)
        rowPrivacy.describe(R.drawable.ic_fo_shield, R.color.color_file_excel, R.string.privacy_policy)
        rowShare.describe(R.drawable.ic_fo_share, R.color.color_file_powerpoint, R.string.fo_tell_a_friend)
        rowPrivacy.tvValue.isVisible = false
        rowShare.tvValue.isVisible = false
        themeAuto.describe(R.string.fo_theme_auto, light = true, dark = true)
        themeLight.describe(R.string.theme_light, light = true, dark = false)
        themeDark.describe(R.string.theme_dark, light = false, dark = true)
        themeOptions.forEachIndexed { index, option ->
            option.root.clickSingle {
                val mode = themes[index]
                if (mode == preferenceHelper.themeMode) return@clickSingle
                preferenceHelper.themeMode = mode
                selectTheme(mode)
                AppCompatDelegate.setDefaultNightMode(mode.nightMode)
            }
        }
        showStars(0)
        tvVersion.text = getString(R.string.fo_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    override fun FragmentSettingsBinding.onClick() {
        cardPremium.clickSingle { PaywallActivity.start(requireActivity()) }
        btnTrial.clickSingle { PaywallActivity.start(requireActivity()) }
        rowLanguage.root.clickSingle { launchActivity<LanguageActivity>() }
        rowUseCases.root.clickSingle { launchActivity<WelcomeActivity>() }
        rowPrivacy.root.clickSingle { appContext().openUrl(Aso.POLICY_LINK) }
        rowShare.root.clickSingle { appContext().shareApp() }
    }

    override fun onResume() {
        super.onResume()
        bindingOrNull?.showPreferences()
    }

    private fun FragmentSettingsBinding.showPreferences() {
        rowLanguage.tvValue.text = listLanguage.firstOrNull { it.code == preferenceHelper.languageSelected }?.name.orEmpty()
        rowUseCases.tvValue.text = UseCase.of(preferenceHelper.useCases).joinToString { getString(it.title) }
        selectTheme(preferenceHelper.themeMode)
    }

    private fun FragmentSettingsBinding.selectTheme(mode: ThemeMode) {
        themeOptions.forEachIndexed { index, option ->
            val picked = themes[index] == mode
            option.root.isSelected = picked
            option.ivCheck.isVisible = picked
        }
    }

    /** Auto shows half of each theme; Light and Dark show one. */
    private fun LayoutThemeOptionBinding.describe(@StringRes name: Int, light: Boolean, dark: Boolean) {
        tvName.setText(name)
        halfLight.isVisible = light
        halfDark.isVisible = dark
        root.contentDescription = getString(name)
    }

    private fun LayoutSettingsRowBinding.describe(@DrawableRes icon: Int, @ColorRes tint: Int, @StringRes label: Int) {
        ivIcon.setImageResource(icon)
        ivIcon.setBackgroundResource(R.drawable.bg_tool_glyph)
        ivIcon.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(tint))
        ImageViewCompat.setImageTintList(ivIcon, ColorStateList.valueOf(requireContext().getColor(R.color.on_primary)))
        tvLabel.setText(label)
    }

    /** Five stars, the first [rating] lit; tapping one rates the app (or asks what went wrong). */
    private fun FragmentSettingsBinding.showStars(rating: Int) {
        tvRate.setText(RATE_MESSAGES[rating])
        val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._34sdp)
        val inset = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._7sdp)
        layoutStars.removeAllViews()
        for (star in 1..5) {
            val lit = star <= rating
            layoutStars.addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                setPadding(inset, inset, inset, inset)
                setImageResource(if (lit) R.drawable.ic_fo_star_filled else R.drawable.ic_fo_star)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(context.getColor(if (lit) R.color.color_pro else R.color.star_idle)))
                contentDescription = resources.getQuantityString(R.plurals.fo_rate_stars, star, star)
                clickSingle {
                    showStars(star)
                    if (star >= REVIEW_RATING) requireActivity().rateApp {} else requireActivity().sendFeedBack()
                }
            })
        }
    }

    private companion object {
        const val REVIEW_RATING = 4
        val RATE_MESSAGES = intArrayOf(
            R.string.fo_rate_0, R.string.fo_rate_1, R.string.fo_rate_2,
            R.string.fo_rate_3, R.string.fo_rate_4, R.string.fo_rate_5
        )
    }
}
