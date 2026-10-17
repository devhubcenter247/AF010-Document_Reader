package com.af010.creator.docreader.ui.main.premium

import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
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
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.setText(R.string.manage_subscription)

        tvPlan.setText(R.string.plan_free)
        tvPlanState.setText(R.string.subscription_none)
        tvStatus.setText(R.string.subscription_not_subscribed)
        tvAmount.setText(R.string.subscription_dash)
        tvRenews.setText(R.string.subscription_dash)
        tvBilledThrough.setText(R.string.google_play)
        btnPrimary.setText(R.string.see_premium_plans)
    }

    override fun ActivitySubscriptionBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        btnPrimary.clickSingle {
            PaywallActivity.start(this@SubscriptionActivity)
            finish()
        }
        btnGooglePlay.clickSingle { openUrl(PLAY_SUBSCRIPTIONS) }
    }

    private companion object {
        const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"
    }
}
