package com.af010.creator.docreader.ui.welcome

import android.graphics.Color
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityWelcomeBinding
import com.af010.creator.docreader.ui.welcome.PermissionActivity
import com.ui.baselib.extensions.clickSingle

class WelcomeActivity : BaseAppActivity<ActivityWelcomeBinding>(ActivityWelcomeBinding::inflate) {

    override fun backPressed() {
        finish()
    }

    private val useCaseAdapter by lazy {
        UseCaseAdapter()
    }

    override fun initialize() {

    }

    override fun ActivityWelcomeBinding.setData() {
        rcvUseCase.adapter = useCaseAdapter

        val savedUseCases = preferenceHelper.useCases
        val defaultList = listOf(
            UseCaseItem(
                id = "work",
                titleRes = R.string.use_case_work,
                descRes = R.string.use_case_work_desc,
                iconRes = R.drawable.ic_app_briefcase,
                iconBgColor = Color.parseColor("#3582FF"),
                isSelected = "work" in savedUseCases
            ),
            UseCaseItem(
                id = "study",
                titleRes = R.string.use_case_study,
                descRes = R.string.use_case_study_desc,
                iconRes = R.drawable.ic_app_grad,
                iconBgColor = Color.parseColor("#8E52F5"),
                isSelected = "study" in savedUseCases
            ),
            UseCaseItem(
                id = "personal",
                titleRes = R.string.use_case_personal,
                descRes = R.string.use_case_personal_desc,
                iconRes = R.drawable.ic_app_id,
                iconBgColor = Color.parseColor("#FF784F"),
                isSelected = "personal" in savedUseCases
            ),
            UseCaseItem(
                id = "reading",
                titleRes = R.string.use_case_reading,
                descRes = R.string.use_case_reading_desc,
                iconRes = R.drawable.ic_app_book,
                iconBgColor = Color.parseColor("#14B8A6"),
                isSelected = "reading" in savedUseCases
            )
        )

        useCaseAdapter.submitList(defaultList)
    }

    override fun ActivityWelcomeBinding.onClick() {
        btnContinue.clickSingle {
            val selected = useCaseAdapter.selectedItems.map { it.id }
            if (selected.isEmpty()) return@clickSingle
            preferenceHelper.useCases = selected
            // Opened again from Settings: just go back.
            if (preferenceHelper.isFinishFirstFlow) finish()
            else launchAndFinish<PermissionActivity>()
        }
    }
}
