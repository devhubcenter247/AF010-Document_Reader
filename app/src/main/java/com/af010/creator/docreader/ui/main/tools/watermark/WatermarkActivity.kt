package com.af010.creator.docreader.ui.main.tools.watermark

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityWatermarkBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.common.fillSegment
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.selectSegment
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.show
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.clickSingle
import java.io.File
import org.koin.android.ext.android.inject

/** Text stamped across every page; the first page previews the text, colour, angle and opacity. */
class WatermarkActivity : BaseAppActivity<ActivityWatermarkBinding>(ActivityWatermarkBinding::inflate) {

    private enum class Ink(val color: Int, @StringRes val label: Int) {
        Terracotta(0xFFC2492A.toInt(), R.string.fo_color_terracotta),
        Blue(0xFF1F6FEB.toInt(), R.string.fo_color_blue),
        InkBlack(0xFF1F2433.toInt(), R.string.fo_color_ink),
        Stone(0xFF878E9F.toInt(), R.string.fo_color_stone)
    }

    private val toolbox: PdfToolbox by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }

    private var ink = Ink.Terracotta
    private var opacity = OPACITIES[2]
    private var diagonal = true
    private var pageCount = 0

    private val text: String get() = binding.edtText.text.toString().trim()

    /** As shown on screen: negative turns the text counter-clockwise. */
    private val rotation: Int get() = if (diagonal) DIAGONAL else 0

    override fun backPressed() = finish()

    override fun initialize() = Unit

    override fun ActivityWatermarkBinding.setData() {
        topBar.show(Tool.Watermark, R.string.tool_watermark, source.name)
        edtText.setText(R.string.watermark_default)
        segmentAngle.setUp(R.string.fo_diagonal, R.string.fo_flat) {
            diagonal = it == 0
            render()
        }
        // The row is shared with the colours: let the words use the whole option.
        val tight = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._3sdp)
        listOf(segmentAngle.tvFirst, segmentAngle.tvSecond).forEach { it.setPadding(tight, 0, tight, 0) }
        layoutOpacity.fillSegment(OPACITIES.map { getString(R.string.translate_percent, it) }) {
            opacity = OPACITIES[it]
            render()
        }
        addColorDots()

        launchMain {
            val page = runCatching { toolbox.renderPage(source, 0, PREVIEW_WIDTH) }.getOrNull()
            if (page == null) {
                toast(R.string.error_file_open_generic)
                finish()
                return@launchMain
            }
            ivPage.setImageBitmap(page)
            pageCount = runCatching { toolbox.pageCount(source) }.getOrDefault(0)
            topBar.tvSubtitle.text = getString(R.string.document_meta, source.name, pageCountText(pageCount))
            render()
        }
        // The text is sized from the page as laid out. Resizing it inside a layout pass can be
        // dropped, so the preview is redrawn on the next frame instead.
        ivPage.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) ivPage.post { render() }
        }
        render()
    }

    override fun ActivityWatermarkBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        edtText.afterTextChanged { render() }
        action.btnAction.clickSingle {
            if (text.isEmpty()) return@clickSingle
            hideKeyboard()
            ProcessingActivity.start(
                this@WatermarkActivity,
                // PDF angles run counter-clockwise, the screen's clockwise.
                ToolRequest.Watermark(source.path, text, colorWithOpacity(), -rotation.toFloat())
            )
            finish()
        }
    }

    private fun ActivityWatermarkBinding.addColorDots() {
        val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._22sdp)
        val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
        Ink.entries.forEachIndexed { index, dotInk ->
            layoutColors.addView(View(this@WatermarkActivity).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { if (index > 0) marginStart = gap }
                tag = dotInk
                contentDescription = getString(dotInk.label)
                setOnClickListener {
                    ink = dotInk
                    render()
                }
            })
        }
    }

    /** A colour dot; the picked one sits inside a ring of its colour with a gap of page colour. */
    private fun dot(color: Int, picked: Boolean): LayerDrawable {
        val ring = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
        val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
        val outer = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(getColor(R.color.color_bg_app))
            if (picked) setStroke(ring, color)
        }
        val inner = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        return LayerDrawable(arrayOf(outer, inner)).apply {
            val inset = if (picked) ring + gap else 0
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun ActivityWatermarkBinding.render() {
        for (index in 0 until layoutColors.childCount) {
            val view = layoutColors.getChildAt(index)
            val dotInk = view.tag as Ink
            view.background = dot(dotInk.color, dotInk == ink)
            view.isSelected = dotInk == ink
        }
        segmentAngle.select(if (diagonal) 0 else 1)
        layoutOpacity.selectSegment(OPACITIES.indexOf(opacity))

        tvWatermark.text = text
        tvWatermark.setTextColor(colorWithOpacity())
        tvWatermark.rotation = rotation.toFloat()
        // Same sizing rule as the PDF writer, applied to the page as drawn on screen.
        if (ivPage.width > 0 && text.isNotEmpty()) {
            tvWatermark.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                PdfToolbox.watermarkTextSize(text, ivPage.width.toFloat())
            )
        }
        action.show(
            if (pageCount > 0) resources.getQuantityString(R.plurals.fo_stamp_pages, pageCount, pageCount)
            else getString(R.string.apply),
            R.drawable.ic_fo_watermark
        )
        action.btnAction.isEnabled = text.isNotEmpty()
    }

    private fun colorWithOpacity(): Int = ColorUtils.setAlphaComponent(ink.color, opacity * 255 / 100)

    private companion object {
        const val PREVIEW_WIDTH = 900
        const val DIAGONAL = -38
        val OPACITIES = listOf(10, 20, 30, 50, 80)
    }
}
