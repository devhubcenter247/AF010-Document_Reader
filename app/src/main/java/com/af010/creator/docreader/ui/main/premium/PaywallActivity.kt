package com.af010.creator.docreader.ui.main.premium

import android.content.Context
import android.content.Intent
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityPaywallBinding
import com.af010.creator.docreader.model.PremiumPlan
import com.af010.creator.docreader.model.Tool
import com.d010.pdf.reader.base.Aso
import com.d010.pdf.reader.base.openUrl
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * The paywall: a Pro tool's promise (or Pro's in general), the yearly and monthly plans side by
 * side, and the lifetime purchase as a link under the main button.
 *
 * TODO: this is the screen only. Play Billing is not wired: prices come from [PremiumPlan] and
 * the purchase and restore buttons just say so.
 */
class PaywallActivity : BaseAppActivity<ActivityPaywallBinding>(ActivityPaywallBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val tool: Tool? by lazy {
        intent.getStringExtra(EXTRA_TOOL)?.let { name -> Tool.entries.firstOrNull { it.name == name } }
    }
    private var plan = PremiumPlan.Yearly

    /** The day the free trial ends and the first charge is made. */
    private val trialEnd: Date by lazy {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, PremiumPlan.TRIAL_DAYS) }.time
    }

    override fun backPressed() = finish()

    override fun initialize() = Unit

    override fun ActivityPaywallBinding.setData() {
        layoutHeader.updatePadding(top = statusBarHeight)
        val feature = tool
        tvProTool.isVisible = feature != null
        if (feature == null) {
            tvTitle.setText(R.string.fo_paywall_title)
            tvSubtitle.setText(R.string.fo_paywall_subtitle)
        } else {
            val name = getString(feature.title)
            ivFeature.setImageResource(feature.glyph)
            tvTitle.text = getString(R.string.fo_paywall_tool_title, name)
            tvSubtitle.text = getString(R.string.fo_paywall_tool_subtitle, name)
        }
        planYearly.tvName.setText(PremiumPlan.Yearly.title)
        planYearly.tvPrice.text = PremiumPlan.Yearly.price
        planYearly.tvLine.text = getString(R.string.fo_yearly_line, PremiumPlan.YEARLY_PER_MONTH, PremiumPlan.TRIAL_DAYS)
        planYearly.tvBadge.isVisible = true
        planYearly.tvBadge.text = getString(R.string.plan_save, PremiumPlan.YEARLY_SAVING_PERCENT)
        planMonthly.tvName.setText(PremiumPlan.Monthly.title)
        planMonthly.tvPrice.text = PremiumPlan.Monthly.price
        planMonthly.tvLine.setText(R.string.fo_monthly_line)
        btnLifetime.text = getString(R.string.fo_lifetime_price, PremiumPlan.Lifetime.price)
        render()
    }

    override fun ActivityPaywallBinding.onClick() {
        btnClose.clickSingle { finish() }
        planYearly.card.click { pick(PremiumPlan.Yearly) }
        planMonthly.card.click { pick(PremiumPlan.Monthly) }
        btnLifetime.click { pick(PremiumPlan.Lifetime) }
        // TODO: start the Play Billing flow for [plan]; restore through Play Billing as well.
        btnPurchase.clickSingle { toast(R.string.purchase_unavailable) }
        btnRestore.clickSingle { toast(R.string.restore_nothing) }
        btnTerms.clickSingle { openUrl(Aso.TEAM_SERVICE) }
    }

    private fun pick(value: PremiumPlan) {
        plan = value
        binding.render()
    }

    private fun ActivityPaywallBinding.render() {
        planYearly.card.isSelected = plan == PremiumPlan.Yearly
        planMonthly.card.isSelected = plan == PremiumPlan.Monthly
        btnLifetime.setTextColor(getColor(if (plan == PremiumPlan.Lifetime) R.color.primary else R.color.text_tertiary))

        val price = plan.price
        val end = DateFormat.getDateInstance(DateFormat.MEDIUM).format(trialEnd)
        when (plan) {
            PremiumPlan.Yearly -> {
                btnPurchase.text = getString(R.string.fo_try_days_free, PremiumPlan.TRIAL_DAYS)
                tvLegal.text = getString(R.string.legal_yearly, price, PremiumPlan.TRIAL_DAYS, end)
            }
            PremiumPlan.Monthly -> {
                btnPurchase.text = getString(R.string.fo_subscribe_monthly, price)
                tvLegal.text = getString(R.string.legal_monthly, price)
            }
            PremiumPlan.Lifetime -> {
                btnPurchase.text = getString(R.string.purchase_lifetime, price)
                tvLegal.text = getString(R.string.legal_lifetime, price)
            }
        }
    }

    companion object {
        private const val EXTRA_TOOL = "extra_tool"

        /** [tool] is the Premium tool the user came from, named in the header. */
        fun start(context: Context, tool: Tool? = null) {
            if (!remoteConfig.paywallEnabled) return
            context.startActivity(
                Intent(context, PaywallActivity::class.java).putExtra(EXTRA_TOOL, tool?.name)
            )
        }
    }
}
