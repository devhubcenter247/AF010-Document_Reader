package com.af010.creator.docreader.ui.welcome

import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityWelcomeBinding
import com.af010.creator.docreader.ui.common.show
import com.ui.baselib.extensions.clickSingle

class WelcomeActivity : BaseAppActivity<ActivityWelcomeBinding>(ActivityWelcomeBinding::inflate) {

    private val cards = UseCaseAdapter(onChange = ::showPicked)

    override fun initialize() = Unit

    override fun backPressed() = finish()

    override fun ActivityWelcomeBinding.setData() {
        // Opened again from Settings there are no first-run steps around it.
        steps.root.isVisible = !preferenceHelper.isFinishFirstFlow
        steps.show(current = 2, total = 3)
        rcvUseCase.adapter = cards
        val saved = preferenceHelper.useCases
        cards.submitList(
            listOf(
                UseCaseItem("work", R.string.use_case_work, R.string.fo_use_case_work_desc, R.drawable.ic_fo_work, R.color.primary),
                UseCaseItem("study", R.string.use_case_study, R.string.use_case_study_desc, R.drawable.ic_fo_study, R.color.color_file_image),
                UseCaseItem("personal", R.string.use_case_personal, R.string.fo_use_case_personal_desc, R.drawable.ic_fo_home, R.color.color_file_excel),
                UseCaseItem("reading", R.string.use_case_reading, R.string.fo_use_case_reading_desc, R.drawable.ic_fo_book, R.color.color_tool_amber)
            ).map { it.copy(isSelected = it.id in saved) }
        )
        showPicked(saved.size)
    }

    override fun ActivityWelcomeBinding.onClick() {
        // Picking nothing is fine: the Files tab then suggests the Work tools (UseCase.of).
        btnContinue.clickSingle {
            preferenceHelper.useCases = cards.picked.map { it.id }
            if (preferenceHelper.isFinishFirstFlow) finish() else launchAndFinish<PermissionActivity>()
        }
    }

    private fun showPicked(count: Int) {
        binding.tvPicked.text =
            if (count == 0) getString(R.string.fo_picked_none)
            else resources.getQuantityString(R.plurals.fo_picked, count, count)
    }
}
