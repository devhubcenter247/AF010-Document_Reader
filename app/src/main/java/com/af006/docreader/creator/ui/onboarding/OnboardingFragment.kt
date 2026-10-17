package com.af010.creator.docreader.ui.onboarding

import android.os.Bundle
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentOnboardingBinding

class OnboardingFragment : BaseAppFragment<FragmentOnboardingBinding>(FragmentOnboardingBinding::inflate) {
    override val isFullSc: Boolean
        get() = true
    companion object {
        private const val ARG_POSITION = "ARG_POSITION"

        fun newInstance(position: Int): OnboardingFragment {
            return OnboardingFragment().apply {
                arguments = Bundle().apply { putInt(ARG_POSITION, position) }
            }
        }
    }

    private val position: Int
        get() = arguments?.getInt(ARG_POSITION) ?: 0

    override fun FragmentOnboardingBinding.initView() {
        val imageRes = when (position) {
            0 -> R.drawable.img_onboard_1
            1 -> R.drawable.img_onboard_2
            2 -> R.drawable.img_onboard_3
            else -> R.drawable.img_onboard_4
        }
        imgOnbBackground.setImageResource(imageRes)
    }

    override fun FragmentOnboardingBinding.onClick() {

    }
}
