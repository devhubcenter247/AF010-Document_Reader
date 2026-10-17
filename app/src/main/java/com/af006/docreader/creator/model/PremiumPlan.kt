package com.af010.creator.docreader.model

import androidx.annotation.StringRes
import com.af010.creator.docreader.R

/**
 * The plans on the paywall. Prices are the design's, in USD, and stand in until Play Billing
 * supplies the store's own: nothing can be bought with them yet.
 */
enum class PremiumPlan(
    @StringRes val title: Int,
    @StringRes val period: Int,
    val price: String
) {
    Yearly(R.string.plan_yearly, R.string.plan_per_year, "$29.99"),
    Monthly(R.string.plan_monthly, R.string.plan_per_month, "$5.99"),
    Lifetime(R.string.plan_lifetime, R.string.plan_one_time, "$49.99");

    companion object {
        const val TRIAL_DAYS = 3
        const val FREE = "$0.00"

        /** What the yearly plan costs per month, and how much that saves against paying monthly. */
        const val YEARLY_PER_MONTH = "$2.50"
        const val YEARLY_SAVING_PERCENT = 58
    }
}
