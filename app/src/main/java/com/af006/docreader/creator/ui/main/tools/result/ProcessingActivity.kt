package com.af010.creator.docreader.ui.main.tools.result

import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityProcessingBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.pdf.ToolState
import com.af010.creator.docreader.ui.common.showToolTile
import com.af010.creator.docreader.ui.main.tools.translate.TranslateResultActivity
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrInvisible
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
        ivTool.showToolTile(state.request.tool)
        tvTitle.setText(titles[state.request.tool] ?: R.string.processing_generic)
        if (state.step != 0) tvSubtitle.setText(state.step)

        val counted = state.total > 0
        progress.isIndeterminate = !counted
        tvPercent.visibleOrInvisible(counted)
        tvCount.visibleOrInvisible(counted)
        if (counted) {
            val percent = state.done * 100 / state.total
            progress.setProgressCompat(percent, true)
            tvPercent.text = getString(R.string.translate_percent, percent)
            tvCount.text = getString(R.string.progress_count, state.done, state.total)
        }
    }

    /** Shows 100%, then runs [then] [DONE_HOLD_MS] later; not when the screen was left meanwhile (Cancel). */
    private fun afterFinished(then: () -> Unit) {
        if (leaving) return
        leaving = true
        with(binding) {
            progress.isIndeterminate = false
            progress.setProgressCompat(100, true)
            tvPercent.visibleOrInvisible(true)
            tvPercent.text = getString(R.string.translate_percent, 100)
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
