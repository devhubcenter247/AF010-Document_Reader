package com.af010.creator.docreader.ui.main.premium

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.d010.pdf.reader.base.Aso
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityPaywallBinding
import com.af010.creator.docreader.databinding.ItemPaywallBenefitBinding
import com.af010.creator.docreader.databinding.LayoutPlanBinding
import com.af010.creator.docreader.databinding.LayoutTrialStepBinding
import com.af010.creator.docreader.model.PremiumPlan
import com.af010.creator.docreader.model.Tool
import com.d010.pdf.reader.base.openUrl
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visibleOrGone
import com.ui.baselib.utils.dpToPx
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The paywall: what Premium gives, the three plans and what the chosen one will charge. Opened
 * from a Premium tool it names that tool in the header.
 *
 * TODO: this is the screen only. Play Billing is not wired: prices come from [PremiumPlan] and
 * the purchase and restore buttons just say so.
 */
class PaywallActivity : BaseAppActivity<ActivityPaywallBinding>(ActivityPaywallBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private class Benefit(@DrawableRes val icon: Int, @StringRes val text: Int)

    private val tool: Tool? by lazy {
        intent.getStringExtra(EXTRA_TOOL)?.let { name -> Tool.entries.firstOrNull { it.name == name } }
    }
    private var plan = PremiumPlan.Yearly

    /** The day the free trial ends and the first charge is made. */
    private val trialEnd: Date by lazy {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, PremiumPlan.TRIAL_DAYS) }.time
    }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityPaywallBinding.setData() {
        layoutHeader.updatePadding(top = statusBarHeight + resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._11sdp))
        val feature = tool
        if (feature == null) {
            ivFeature.setImageResource(R.drawable.ic_app_crown)
            tvTitle.setText(R.string.paywall_title)
            tvSubtitle.setText(R.string.paywall_subtitle)
        } else {
            ivFeature.setImageResource(feature.glyph)
            tvTitle.text = getString(R.string.paywall_feature_title, getString(feature.title))
            tvSubtitle.setText(feature.desc)
        }
        BENEFITS.forEach { benefit ->
            ItemPaywallBenefitBinding.inflate(layoutInflater, layoutBenefits, true).apply {
                ivIcon.setImageResource(benefit.icon)
                tvText.setText(benefit.text)
            }
        }
        showPlans()
        showTrialSteps()
        render()
    }

    override fun ActivityPaywallBinding.onClick() {
        btnClose.clickSingle { finish() }
        plans().forEach { (binding, binds) ->
            binding.card.click {
                plan = binds
                render()
            }
        }
        // TODO: start the Play Billing flow for [plan]; restore through Play Billing as well.
        btnPurchase.clickSingle { toast(R.string.purchase_unavailable) }
        btnRestore.clickSingle { toast(R.string.restore_nothing) }
        btnManage.clickSingle { launchActivity<SubscriptionActivity>() }
        btnTerms.clickSingle { openUrl(Aso.TEAM_SERVICE) }
        btnPrivacy.clickSingle { openUrl(Aso.POLICY_LINK) }
    }

    private fun ActivityPaywallBinding.plans(): Map<LayoutPlanBinding, PremiumPlan> = mapOf(
        planYearly to PremiumPlan.Yearly,
        planMonthly to PremiumPlan.Monthly,
        planLifetime to PremiumPlan.Lifetime
    )

    private fun ActivityPaywallBinding.showPlans() {
        plans().forEach { (binding, binds) ->
            binding.tvName.setText(binds.title)
            binding.tvPrice.text = binds.price
            binding.tvPeriod.setText(binds.period)
        }
        planYearly.tvLine1.text = getString(R.string.plan_yearly_line1, PremiumPlan.TRIAL_DAYS, PremiumPlan.Yearly.price)
        planYearly.tvLine2.text = getString(R.string.plan_yearly_line2, PremiumPlan.YEARLY_PER_MONTH)
        planYearly.tvBadge.text = getString(R.string.plan_save, PremiumPlan.YEARLY_SAVING_PERCENT)
        planYearly.tvBadge.visibleOrGone(true)
        planMonthly.tvLine1.text = getString(R.string.plan_monthly_line1, PremiumPlan.Monthly.price)
        planMonthly.tvLine2.setText(R.string.plan_monthly_line2)
        planLifetime.tvLine1.setText(R.string.plan_lifetime_line1)
        planLifetime.tvLine2.setText(R.string.plan_lifetime_line2)
    }

    private fun ActivityPaywallBinding.showTrialSteps() {
        val price = PremiumPlan.Yearly.price
        stepToday.show(
            R.drawable.ic_app_crown, R.color.primary,
            getString(R.string.trial_today_title, PremiumPlan.FREE), getString(R.string.trial_today_desc)
        )
        stepEnd.show(
            R.drawable.ic_app_clock, R.color.color_trial_end,
            getString(R.string.trial_end_title, longDate(trialEnd)), getString(R.string.trial_end_desc, price)
        )
        stepRenew.show(
            R.drawable.ic_app_refresh, R.color.text_secondary,
            getString(R.string.trial_renew_title, shortDate(trialEnd)), getString(R.string.trial_renew_desc, price)
        )
        stepRenew.line.gone()
        stepRenew.root.getChildAt(1).updatePadding(bottom = 0)
    }

    private fun LayoutTrialStepBinding.show(@DrawableRes icon: Int, color: Int, title: String, desc: String) {
        ivIcon.setImageResource(icon)
        ivIcon.helper.bgColor = ContextCompat.getColor(this@PaywallActivity, color)
        ivIcon.invalidate()
        tvTitle.text = title
        tvDesc.text = desc
    }

    private fun ActivityPaywallBinding.render() {
        plans().forEach { (binding, binds) ->
            val selected = binds == plan
            binding.ivRadio.setImageResource(if (selected) R.drawable.ic_app_radio_on else R.drawable.ic_app_radio_off)
            binding.card.helper.stColor = ContextCompat.getColor(
                this@PaywallActivity, if (selected) R.color.primary else R.color.color_stroke
            )
            binding.card.helper.stWidth = dpToPx(if (selected) 2 else 1).toFloat()
            binding.card.helper.bgColor = ContextCompat.getColor(
                this@PaywallActivity, if (selected) R.color.color_plan_selected else R.color.color_surface_card
            )
            binding.card.invalidate()
        }
        cardTrial.visibleOrGone(plan == PremiumPlan.Yearly)

        val price = plan.price
        val end = longDate(trialEnd)
        when (plan) {
            PremiumPlan.Yearly -> {
                btnPurchase.text = getString(R.string.purchase_trial, PremiumPlan.TRIAL_DAYS)
                tvPriceNote.text = getString(R.string.price_note_yearly, PremiumPlan.FREE, price, end)
                tvLegal.text = getString(R.string.legal_yearly, price, PremiumPlan.TRIAL_DAYS, end)
            }
            PremiumPlan.Monthly -> {
                btnPurchase.text = getString(R.string.purchase_monthly, price)
                tvPriceNote.text = getString(R.string.price_note_monthly, price)
                tvLegal.text = getString(R.string.legal_monthly, price)
            }
            PremiumPlan.Lifetime -> {
                btnPurchase.text = getString(R.string.purchase_lifetime, price)
                tvPriceNote.text = getString(R.string.price_note_lifetime, price)
                tvLegal.text = getString(R.string.legal_lifetime, price)
            }
        }
    }

    private fun longDate(date: Date): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)

    /** Month and day only, for "every Oct 3". */
    private fun shortDate(date: Date): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(date)

    companion object {
        private const val EXTRA_TOOL = "extra_tool"

        private val BENEFITS = listOf(
            Benefit(R.drawable.ic_app_eyeoff, R.string.benefit_no_ads),
            Benefit(R.drawable.ic_app_compress, R.string.benefit_unlimited),
            Benefit(R.drawable.ic_app_ocr, R.string.benefit_ocr),
            Benefit(R.drawable.ic_app_lock, R.string.benefit_protect),
            Benefit(R.drawable.ic_app_translate, R.string.benefit_translate)
        )

        /** [tool] is the Premium tool the user came from, named in the header. */
        fun start(context: Context, tool: Tool? = null) {
            if (!remoteConfig.paywallEnabled) return
            context.startActivity(
                Intent(context, PaywallActivity::class.java).putExtra(EXTRA_TOOL, tool?.name)
            )
        }
    }
}
