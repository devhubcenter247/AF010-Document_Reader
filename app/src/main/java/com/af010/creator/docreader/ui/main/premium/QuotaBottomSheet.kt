package com.af010.creator.docreader.ui.main.premium

import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetQuotaBinding
import com.af010.creator.docreader.model.PremiumPlan
import com.af010.creator.docreader.model.Tool
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.clickSingle

/**
 * Shown when the free uses of [tool] are spent ([used] of [limit]): what Pro adds and the trial.
 *
 * TODO: nothing counts free uses yet, so no screen shows this sheet. Show it from a tool's
 * action button once the quota exists.
 */
class QuotaBottomSheet(
    private val activity: FragmentActivity,
    private val tool: Tool,
    private val used: Int,
    private val limit: Int
) : BaseBottomSheet<SheetQuotaBinding>(activity, SheetQuotaBinding::inflate) {

    override fun SheetQuotaBinding.onBind() {
        val name = activity.getString(tool.title)
        tvUsed.text = activity.getString(R.string.fo_used_of, used, limit)
        progressUsed.progress = if (limit > 0) used * 100 / limit else 100
        tvTitle.text = activity.getString(R.string.fo_quota_title, name)
        tvMessage.text = activity.getString(R.string.fo_quota_message, name)
        btnTrial.text = activity.getString(R.string.fo_try_pro_free, PremiumPlan.TRIAL_DAYS)

        btnTrial.clickSingle {
            dismissSafe()
            PaywallActivity.start(activity, tool)
        }
        btnNotNow.clickSingle { dismissSafe() }
    }
}
