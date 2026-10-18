package com.af010.creator.docreader.ui.main.premium

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityIntroPaywallBinding
import com.af010.creator.docreader.databinding.LayoutBenefitRowBinding
import com.af010.creator.docreader.databinding.LayoutPlanOptionBinding
import com.af010.creator.docreader.model.PremiumPlan
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.main.tools.translate.TranslateActivity
import com.d010.pdf.reader.base.Aso
import com.d010.pdf.reader.base.openUrl
import com.ui.baselib.extensions.clickSingle

/**
 * The offer shown once, at the end of the first run. Closing it (or back) goes on to the app.
 *
 * TODO: like [PaywallActivity] this is the screen only; prices come from [PremiumPlan] until
 * Play Billing is wired.
 */
class IntroPaywallActivity : BaseAppActivity<ActivityIntroPaywallBinding>(ActivityIntroPaywallBinding::inflate) {

    private var plan = PremiumPlan.Yearly

    override fun initialize() = Unit

    override fun backPressed() = openApp()

    override fun ActivityIntroPaywallBinding.setData() {
        benefitOcr.show(R.drawable.ic_fo_sparkles, getString(R.string.fo_pro_benefit_ocr))
        benefitProtect.show(R.drawable.ic_fo_lock, getString(R.string.fo_pro_benefit_protect))
        benefitTranslate.show(R.drawable.ic_fo_translate, getString(R.string.fo_pro_benefit_translate, TranslateActivity.FREE_PAGES))
        benefitAds.show(R.drawable.ic_fo_heart, getString(R.string.fo_pro_benefit_ads))

        planYearly.describe(PremiumPlan.Yearly, getString(R.string.fo_plan_yearly_sub, PremiumPlan.TRIAL_DAYS, PremiumPlan.YEARLY_PER_MONTH), bestValue = true)
        planMonthly.describe(PremiumPlan.Monthly, getString(R.string.fo_plan_monthly_sub))
        planLifetime.describe(PremiumPlan.Lifetime, getString(R.string.fo_plan_lifetime_sub))

        tvLegal.text = legalLine()
        tvLegal.movementMethod = LinkMovementMethod.getInstance()
        showPlan()
    }

    override fun ActivityIntroPaywallBinding.onClick() {
        btnClose.clickSingle { openApp() }
        // TODO: Play Billing for [plan] and for restoring.
        btnPurchase.clickSingle { toast(R.string.purchase_unavailable) }
        btnRestore.clickSingle { toast(R.string.restore_nothing) }
    }

    private fun LayoutBenefitRowBinding.show(@DrawableRes icon: Int, text: String) {
        ivIcon.setImageResource(icon)
        tvText.text = text
    }

    private fun LayoutPlanOptionBinding.describe(option: PremiumPlan, detail: String, bestValue: Boolean = false) {
        tvName.setText(option.title)
        tvDetail.text = detail
        tvPrice.text = option.price
        tvBadge.isVisible = bestValue
        root.setOnClickListener {
            plan = option
            showPlan()
        }
    }

    private fun showPlan() = with(binding) {
        planYearly.root.isSelected = plan == PremiumPlan.Yearly
        planMonthly.root.isSelected = plan == PremiumPlan.Monthly
        planLifetime.root.isSelected = plan == PremiumPlan.Lifetime
        btnPurchase.text = when (plan) {
            PremiumPlan.Yearly -> getString(R.string.fo_cta_trial, PremiumPlan.TRIAL_DAYS)
            PremiumPlan.Monthly -> getString(R.string.fo_cta_monthly, plan.price)
            PremiumPlan.Lifetime -> getString(R.string.fo_cta_lifetime, plan.price)
        }
    }

    /** "No charge today… · Terms · Privacy", the two names bold and tappable. */
    private fun legalLine(): CharSequence = SpannableStringBuilder(getString(R.string.fo_no_charge_today)).apply {
        append(" · ")
        link(getString(R.string.fo_terms)) { openUrl(Aso.TEAM_SERVICE) }
        append(" · ")
        link(getString(R.string.fo_privacy)) { openUrl(Aso.POLICY_LINK) }
    }

    private fun SpannableStringBuilder.link(label: String, onTap: () -> Unit) {
        val start = length
        append(label)
        val bold = ResourcesCompat.getFont(this@IntroPaywallActivity, R.font.nunito_700)
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) = onTap()
            override fun updateDrawState(paint: TextPaint) {
                paint.color = getColor(R.color.text_tertiary)
                paint.typeface = bold
                paint.isUnderlineText = false
            }
        }, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun openApp() = launchAndFinish<MainActivity>()
}
