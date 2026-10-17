package com.af010.creator.docreader.ui.main.tools.result

import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityProcessingBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.pdf.ToolState
import com.af010.creator.docreader.ui.main.tools.TOOL_STEPS
import com.af010.creator.docreader.databinding.LayoutProcessStepBinding
import com.af010.creator.docreader.ui.main.tools.translate.TranslateResultActivity
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject

/** Shows the progress of the tool job started with [EXTRA_REQUEST], then moves on to its result. */
class ProcessingActivity :
    BaseAppActivity<ActivityProcessingBinding>(ActivityProcessingBinding::inflate) {

    private val runner: ToolRunner by inject()

    /** The result screen is on its way: a Done seen again (screen recreated) does not schedule it twice. */
    private var leaving = false

    override fun backPressed() {
        // The file is written: the result screen follows in a moment.
        if (!leaving) cancel()
    }

    override fun initialize() {
        val request = IntentCompat.getParcelableExtra(intent, EXTRA_REQUEST, ToolRequest::class.java)
        // A recreated screen follows the job already running instead of starting it again.
        if (request != null && runner.state.value !is ToolState.Running) runner.start(request)
    }

    override fun ActivityProcessingBinding.setData() {
        runner.state.mainCollect { state ->
            when (state) {
                is ToolState.Running -> show(state)
                // 100% stays on screen a moment, so a quick job does not flash by. Reset only
                // when leaving: Idle closes the screen.
                is ToolState.Done -> afterFinished {
                    runner.reset()
                    if (state.result.tool == Tool.Translate) {
                        TranslateResultActivity.start(this@ProcessingActivity, state.result.outputs.first())
                    } else {
                        SuccessActivity.start(this@ProcessingActivity, state.result)
                    }
                    finish()
                }
                is ToolState.Failed -> {
                    runner.reset()
                    toastLong(state.message)
                    finish()
                }
                ToolState.Idle -> if (!isFinishing) finish()
            }
        }
    }

    override fun ActivityProcessingBinding.onClick() {
        btnCancel.clickSingle { cancel() }
    }

    private fun ActivityProcessingBinding.show(state: ToolState.Running) {
        val request = state.request
        tvTool.setText(request.tool.title)
        tvStepCount.text = getString(R.string.fo_step_of, TOOL_STEPS, TOOL_STEPS)
        tvTitle.setText(titles[request.tool] ?: R.string.processing_generic)

        val files = request.inputCount
        stepRead.show(resources.getQuantityString(R.plurals.fo_step_read_files, files, files), Step.Done)
        stepWork.show(getString(if (state.step != 0) state.step else R.string.fo_step_working), Step.Now)
        stepSave.show(getString(R.string.fo_step_save), Step.Later)

        val counted = state.total > 0
        progress.isIndeterminate = !counted
        tvPercent.isInvisible = !counted
        if (counted) {
            val percent = state.done * 100 / state.total
            progress.setProgressCompat(percent, true)
            tvPercent.text = getString(R.string.translate_percent, percent)
        }
    }

    private enum class Step { Done, Now, Later }

    private fun LayoutProcessStepBinding.show(text: String, step: Step) {
        tvStep.text = text
        ivDone.isVisible = step == Step.Done
        spinner.isVisible = step == Step.Now
        viewTodo.isVisible = step == Step.Later
        tvStep.setTextColor(getColor(if (step == Step.Now) R.color.text_primary else R.color.text_tertiary))
        tvStep.typeface = ResourcesCompat.getFont(this@ProcessingActivity, if (step == Step.Now) R.font.nunito_800 else R.font.nunito_700)
    }

    /** How many files the job reads, for "Read 3 files". */
    private val ToolRequest.inputCount: Int
        get() = when (this) {
            is ToolRequest.Merge -> paths.size
            is ToolRequest.Zip -> paths.size
            is ToolRequest.ImagesToPdf -> paths.size
            else -> 1
        }

    /** Shows 100%, then runs [then] [DONE_HOLD_MS] later; not when the screen was left meanwhile (Cancel). */
    private fun afterFinished(then: () -> Unit) {
        if (leaving) return
        leaving = true
        with(binding) {
            progress.isIndeterminate = false
            progress.setProgressCompat(100, true)
            tvPercent.isInvisible = false
            tvPercent.text = getString(R.string.translate_percent, 100)
            stepWork.show(stepWork.tvStep.text.toString(), Step.Done)
            stepSave.show(stepSave.tvStep.text.toString(), Step.Done)
            btnCancel.isEnabled = false
        }
        postDelayed(DONE_HOLD_MS) {
            if (isFinishing || isDestroyed) runner.reset() else then()
        }
    }

    private fun cancel() {
        runner.cancel()
        finish()
    }

    companion object {
        private const val EXTRA_REQUEST = "EXTRA_REQUEST"
        private const val DONE_HOLD_MS = 1_500L

        private val titles = mapOf(
            Tool.Merge to R.string.processing_merge,
            Tool.Split to R.string.processing_split,
            Tool.Compress to R.string.processing_compress,
            Tool.Protect to R.string.processing_protect,
            Tool.Watermark to R.string.processing_watermark,
            Tool.Redact to R.string.processing_redact,
            Tool.Ocr to R.string.processing_ocr,
            Tool.Translate to R.string.translate_progress_title,
            Tool.Zip to R.string.processing_zip,
            Tool.ImagesToPdf to R.string.processing_images,
            Tool.OfficeToPdf to R.string.processing_images,
            Tool.Scan to R.string.processing_images
        )

        fun start(context: Context, request: ToolRequest) {
            context.startActivity(
                Intent(context, ProcessingActivity::class.java).putExtra(EXTRA_REQUEST, request)
            )
        }
    }
}
