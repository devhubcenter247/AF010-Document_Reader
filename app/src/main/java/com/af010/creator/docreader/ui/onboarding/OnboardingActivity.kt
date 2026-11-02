package com.af010.creator.docreader.ui.onboarding

import android.os.Bundle
import android.widget.LinearLayout
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityOnboardingBinding
import com.af010.creator.docreader.ui.welcome.WelcomeActivity
import com.ui.baselib.extensions.clickSingle

class OnboardingActivity : BaseAppActivity<ActivityOnboardingBinding>(ActivityOnboardingBinding::inflate) {

    private val slides = OnboardingSlide.entries
    private val isLast get() = binding.viewPager.currentItem == slides.lastIndex

    // The pictures reach under the status bar; only the top bar is padded.
    override val fullStatus = true

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
    }

    override fun initialize() = Unit

    override fun backPressed() {
        if (binding.viewPager.currentItem > 0) binding.viewPager.currentItem -= 1 else finish()
    }

    override fun ActivityOnboardingBinding.setData() {
        layoutTop.updatePadding(top = statusBarHeight)
        viewPager.adapter = OnboardingPictures()
        viewPager.offscreenPageLimit = 1
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = showSlide(position)
        })
        showSlide(0)
    }

    override fun ActivityOnboardingBinding.onClick() {
        btnNext.clickSingle {
            if (isLast) openWelcome() else viewPager.setCurrentItem(viewPager.currentItem + 1, true)
        }
    }

    private fun showSlide(position: Int) = with(binding) {
        val slide = slides[position]
        tvKicker.setText(slide.kicker)
        tvTitle.setText(slide.title)
        tvSubtitle.setText(slide.body)
        btnNext.setText(if (position == slides.lastIndex) R.string.fo_lets_get_started else R.string.next)
        drawBars(position)
    }

    /** One bar per slide: a long one for this slide, blue up to it and grey after. */
    private fun drawBars(position: Int) = with(binding.layoutBars) {
        val short = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
        val long = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._20sdp)
        val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._5sdp)
        removeAllViews()
        slides.indices.forEach { index ->
            addView(View(context).apply {
                setBackgroundResource(if (index <= position) R.drawable.bg_step_done else R.drawable.bg_step_todo)
                layoutParams = LinearLayout.LayoutParams(if (index == position) long else short, short).apply {
                    if (index > 0) marginStart = gap
                }
            })
        }
    }

    private fun openWelcome() = launchAndFinish<WelcomeActivity>()
}
