package com.af010.creator.docreader.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.databinding.ItemOnboardingBinding
import com.ui.baselib.base.BaseAdapter

class OnboardingAdapter : BaseAdapter<OnboardingItem, ItemOnboardingBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ItemOnboardingBinding = ItemOnboardingBinding.inflate(inflater, parent, false)

    override fun ItemOnboardingBinding.bind(item: OnboardingItem, position: Int) {
        imgOnboarding.setImageResource(item.imageRes)
        tvTitle.setText(item.titleRes)
        tvSubtitle.setText(item.subtitleRes)
    }
}
