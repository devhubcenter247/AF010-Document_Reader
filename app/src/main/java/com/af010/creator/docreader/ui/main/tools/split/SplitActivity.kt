package com.af010.creator.docreader.ui.main.tools.split

import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySplitBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.FileChanger
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.setChecked
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.showSetupStep
import com.af010.creator.docreader.ui.main.tools.sizeText
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject
import java.io.File

/**
 * Splits a PDF: one file per page, one file per typed range ("1-4, 5-12"), or a single file
 * with the typed pages. A strip below shows the files that will come out.
 */
class SplitActivity : BaseAppActivity<ActivitySplitBinding>(ActivitySplitBinding::inflate) {

    private enum class Mode { EachPage, Ranges, Extract }

    private val toolbox: PdfToolbox by inject()
    private var source: File = File("")
    private var mode = Mode.Ranges
    private var pageCount = 0

    private val changer = FileChanger(this, Tool.Split) {
        source = it
        loadSource()
    }

    override fun backPressed() = finish()

    override fun initialize() {
        source = File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first())
    }

    override fun ActivitySplitBinding.setData() {
        topBar.show(Tool.Split, R.string.split_pdf)
        steps.showSetupStep(R.string.fo_step_choose_how)
        file.btnChange.isVisible = true
        optionEachPage.show(R.string.split_each_page, getString(R.string.split_each_page_desc))
        optionRanges.show(R.string.split_by_range, getString(R.string.split_by_range_desc))
        optionPick.show(R.string.split_pick_pages, getString(R.string.split_pick_pages_desc))
        loadSource()
    }

    override fun ActivitySplitBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        file.btnChange.clickSingle { changer.launch() }
        mapOf(optionEachPage to Mode.EachPage, optionRanges to Mode.Ranges, optionPick to Mode.Extract)
            .forEach { (option, optionMode) ->
                option.root.click {
                    mode = optionMode
                    render()
                }
            }
        edtPages.afterTextChanged { render() }
        action.btnAction.clickSingle {
            val parts = parts() ?: return@clickSingle
            hideKeyboard()
            ProcessingActivity.start(this@SplitActivity, ToolRequest.Split(source.path, parts))
            finish()
        }
    }

    private fun loadSource() = with(binding) {
        file.show(source, source.sizeText(this@SplitActivity))
        pageCount = 0
        render()
        val shown = source
        launchMain {
            val pages = runCatching { toolbox.pageCount(shown) }.getOrDefault(0)
            if (shown != source) return@launchMain
            if (pages <= 0) {
                toast(R.string.split_open_failed)
                finish()
                return@launchMain
            }
            pageCount = pages
            file.tvMeta.text = getString(R.string.document_meta, pageCountText(pages), source.sizeText(this@SplitActivity))
            if (edtPages.text.isNullOrEmpty()) edtPages.setText(suggestedRanges(pages))
            render()
        }
    }

    /** Two halves to start from, so the field shows what it expects. */
    private fun suggestedRanges(pages: Int): String =
        if (pages < 2) "1" else "1-${pages / 2}, ${pages / 2 + 1}-$pages"

    /** The typed ranges, one-based, or null when the text has a mistake in it. */
    private fun typedRanges(): List<IntRange>? {
        val tokens = binding.edtPages.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.map { token ->
            val bounds = token.split('-').map { it.trim().toIntOrNull() ?: return null }
            val range = when (bounds.size) {
                1 -> bounds[0]..bounds[0]
                2 -> bounds[0]..bounds[1]
                else -> return null
            }
            if (range.first < 1 || range.last > pageCount || range.isEmpty()) return null
            range
        }
    }

    /** The zero-based pages of each file to create, or null while the choice is not complete. */
    private fun parts(): List<List<Int>>? {
        if (pageCount <= 0) return null
        return when (mode) {
            Mode.EachPage -> (0 until pageCount).map { listOf(it) }
            Mode.Ranges -> typedRanges()?.map { range -> range.map { it - 1 } }
            Mode.Extract -> typedRanges()?.flatMap { range -> range.map { it - 1 } }?.distinct()?.sorted()?.let { listOf(it) }
        }
    }

    private fun ActivitySplitBinding.render() {
        optionEachPage.setChecked(mode == Mode.EachPage)
        optionRanges.setChecked(mode == Mode.Ranges)
        optionPick.setChecked(mode == Mode.Extract)
        layoutPages.isVisible = mode != Mode.EachPage
        tvPagesLabel.setText(if (mode == Mode.Extract) R.string.split_pages_label else R.string.split_ranges_label)

        val parts = parts()
        tvPreview.text = when {
            parts != null -> resources.getQuantityString(R.plurals.split_youll_get, parts.size, parts.size)
            pageCount > 0 -> getString(R.string.split_check_pages, pageCount)
            else -> ""
        }
        showParts(parts.orEmpty())
        action.show(
            if (mode == Mode.Extract) getString(R.string.split_extract_action)
            else resources.getQuantityString(R.plurals.split_action, parts?.size ?: 0, parts?.size ?: 0),
            R.drawable.ic_fo_split
        )
        action.btnAction.isEnabled = parts != null
    }

    /** One chip per file, as wide as its share of pages; long lists keep the ends and a "…". */
    private fun ActivitySplitBinding.showParts(parts: List<List<Int>>) {
        val chips: List<Pair<String, Int>> = parts.map { pages ->
            val contiguous = pages.zipWithNext().all { (a, b) -> b == a + 1 }
            val label = when {
                pages.size == 1 -> "${pages[0] + 1}"
                contiguous -> "${pages.first() + 1}–${pages.last() + 1}"
                else -> resources.getQuantityString(R.plurals.translate_page_count, pages.size, pages.size)
            }
            label to pages.size
        }.let { if (it.size > MAX_CHIPS) it.take(MAX_CHIPS - 2) + ("…" to 1) + it.last() else it }

        layoutParts.removeAllViews()
        val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._5sdp)
        val weights = chips.map { it.second.toFloat() }
        // Keep thin chips readable next to a big one.
        val floor = (weights.maxOrNull() ?: 1f) / 6f
        chips.forEachIndexed { index, (label, _) ->
            layoutParts.addView(TextView(this@SplitActivity).apply {
                text = label
                gravity = Gravity.CENTER
                maxLines = 1
                setBackgroundResource(R.drawable.bg_part_chip)
                setTextColor(getColor(R.color.text_on_selected))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(com.intuit.sdp.R.dimen._10sdp))
                typeface = ResourcesCompat.getFont(this@SplitActivity, R.font.nunito_700)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, maxOf(weights[index], floor)).apply {
                    if (index > 0) marginStart = gap
                }
            })
        }
    }

    private companion object {
        const val MAX_CHIPS = 5
    }
}
