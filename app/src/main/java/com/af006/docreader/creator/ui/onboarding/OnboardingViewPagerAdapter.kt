package com.af010.creator.docreader.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingViewPagerAdapter(
    activity: FragmentActivity,
    private val pageCount: Int = 4
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = pageCount

    override fun createFragment(position: Int): Fragment {
        return OnboardingFragment.newInstance(position)
    }
}
