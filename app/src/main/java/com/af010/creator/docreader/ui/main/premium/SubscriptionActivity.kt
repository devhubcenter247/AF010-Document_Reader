package com.af010.creator.docreader.ui.main.premium

import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.PremiumPlan
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySubscriptionBinding
import com.d010.pdf.reader.base.openUrl
import com.ui.baselib.extensions.clickSingle

/**
 * The plan the user is on and where to change it. Subscriptions are billed and cancelled in
 * Google Play, so the screen only shows the state and links there.
 *
 * TODO: Play Billing is not wired, so every user is on the free plan here. Once it is, fill the
 * card and the rows from the purchase (plan, trial days left, next charge, amount).
 */
class SubscriptionActivity : BaseAppActivity<ActivitySubscriptionBinding>(ActivitySubscriptionBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivitySubscriptionBinding.setData() {
        topBar.root.updatePadding(top = statusBarHeight)
        topBar.tvTitle.setText(R.string.fo_your_plan)
        tvPlan.setText(R.string.plan_free)
        tvUpgradePrice.text = getString(R.string.fo_upgrade_price, PremiumPlan.Yearly.price, PremiumPlan.TRIAL_DAYS)
    }

    override fun ActivitySubscriptionBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        cardUpgrade.clickSingle { PaywallActivity.start(this@SubscriptionActivity) }
        // TODO: restore through Play Billing.
        rowRestore.clickSingle { toast(R.string.restore_nothing) }
        rowGooglePlay.clickSingle { openUrl(PLAY_SUBSCRIPTIONS) }
    }

    private companion object {
        const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"
    }
}
