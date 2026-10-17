package com.af010.creator.docreader.ui.main.premium

import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetQuotaBinding
import com.af010.creator.docreader.model.PremiumPlan
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.common.showToolTile
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.clickSingle

/**
 * Shown when the free uses of [tool] for the day are spent: start the Premium trial, or watch
 * an ad for one more use.
 *
 * TODO: nothing counts free uses yet, so no screen shows this sheet. Show it from a tool's
 * action button once the daily quota exists; [onWatchAd] should then load a rewarded ad.
 */
class QuotaBottomSheet(
    private val activity: FragmentActivity,
    private val tool: Tool,
    private val onWatchAd: () -> Unit
) : BaseBottomSheet<SheetQuotaBinding>(activity, SheetQuotaBinding::inflate) {

    override fun SheetQuotaBinding.onBind() {
        ivTool.showToolTile(tool)
        tvTitle.text = activity.getString(R.string.quota_title, activity.getString(tool.title))
        tvTrialTerms.text = activity.getString(
            R.string.quota_trial_terms, PremiumPlan.FREE, PremiumPlan.Yearly.price, PremiumPlan.TRIAL_DAYS
        )

        btnTrial.clickSingle {
            dismissSafe()
            PaywallActivity.start(activity, tool)
        }
        btnWatchAd.clickSingle {
            dismissSafe()
            onWatchAd()
        }
    }
}
