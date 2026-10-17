package com.af010.creator.docreader.ui.onboarding

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityOnboardingBinding
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.welcome.WelcomeActivity
import com.ui.baselib.extensions.clickSingle

class OnboardingActivity : BaseAppActivity<ActivityOnboardingBinding>(ActivityOnboardingBinding::inflate) {
    override val fullStatus: Boolean
        get() = true
    override fun backPressed() {
        if (binding.viewPager.currentItem > 0) {
            binding.viewPager.currentItem -= 1
        } else {
            finish()
        }
    }

    private val pagerAdapter by lazy { OnboardingViewPagerAdapter(this, 4) }

    override fun initialize() {

    }

    override fun ActivityOnboardingBinding.setData() {
        viewPager.adapter = pagerAdapter

        indicatorView.attachViewPager(viewPager)
        indicatorView.setIndicatorMaxCount(4)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageUi(position)
            }
        })

        updatePageUi(0)
    }

    override fun ActivityOnboardingBinding.onClick() {
        btnNext.clickSingle {
            val current = viewPager.currentItem
            if (current < 3) {
                viewPager.setCurrentItem(current + 1, true)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updatePageUi(position: Int) {
        binding.indicatorView.setIndicatorActive(position)
        when (position) {
            0 -> {
                binding.tvTitle.setText(R.string.title_onboarding_1)
                binding.tvSubtitle.setText(R.string.content_onboarding_1)
            }
            1 -> {
                binding.tvTitle.setText(R.string.title_onboarding_2)
                binding.tvSubtitle.setText(R.string.content_onboarding_2)
            }
            2 -> {
                binding.tvTitle.setText(R.string.title_onboarding_3)
                binding.tvSubtitle.setText(R.string.content_onboarding_3)
            }
            else -> {
                binding.tvTitle.setText(R.string.title_onboarding_4)
                binding.tvSubtitle.setText(R.string.content_onboarding_4)
            }
        }

        if (position == 3) {
            binding.btnNext.text = getString(R.string.get_started)
            binding.btnNext.icon = null
        } else {
            binding.btnNext.text = getString(R.string.next)
            binding.btnNext.setIconResource(R.drawable.ic_app_arrowr)
        }
    }

    private fun finishOnboarding() {
        launchAndFinish<WelcomeActivity>()
    }
}
