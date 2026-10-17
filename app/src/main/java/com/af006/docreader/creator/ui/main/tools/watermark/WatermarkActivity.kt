package com.af010.creator.docreader.ui.main.tools.watermark

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.graphics.ColorUtils
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityWatermarkBinding
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.utils.extensions.setCircle
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import com.ui.baselib.utils.dpToPx
import java.io.File
import org.koin.android.ext.android.inject

/** Text watermark across every page: the first page previews the text, colour, opacity and angle. */
class WatermarkActivity : BaseAppActivity<ActivityWatermarkBinding>(ActivityWatermarkBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val toolbox: PdfToolbox by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }

    private var color = COLORS.first()
    private var opacity = DEFAULT_OPACITY

    /** As shown on screen: negative turns the text counter-clockwise. */
    private var rotation = DEFAULT_ROTATION

    private val text: String get() = binding.edtText.text.toString().trim()

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityWatermarkBinding.setData() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.btnBack.setImageResource(R.drawable.ic_app_close)
        toolbar.tvTitle.setText(R.string.tool_watermark)
        toolbar.btnAction.setText(R.string.apply)
        toolbar.btnAction.visible()

        edtText.setText(R.string.watermark_default)
        seekOpacity.progress = opacity
        seekRotation.progress = rotation + ROTATION_RANGE
        addColorDots()

        launchMain {
            val page = runCatching { toolbox.renderPage(source, 0, PREVIEW_WIDTH) }.getOrNull()
            if (page == null) {
                toast(R.string.error_file_open_generic)
                finish()
                return@launchMain
            }
            ivPage.setImageBitmap(page)
        }
        // The text is sized from the page as laid out. Resizing it inside a layout pass can be
        // dropped, so the preview is redrawn on the next frame instead.
        ivPage.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) ivPage.post { render() }
        }
        render()
    }

    override fun ActivityWatermarkBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        edtText.afterTextChanged { render() }
        seekOpacity.onChanged {
            opacity = it
            render()
        }
        seekRotation.onChanged {
            rotation = it - ROTATION_RANGE
            render()
        }
        toolbar.btnAction.clickSingle {
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
        val size = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._23sdp)
        val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._9sdp)
        COLORS.forEach { dotColor ->
            val dot = View(this@WatermarkActivity).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = gap }
                setCircle(dotColor)
                tag = dotColor
                setOnClickListener {
                    color = dotColor
                    render()
                }
            }
            layoutColors.addView(dot)
        }
    }

    private fun ActivityWatermarkBinding.render() {
        tvOpacity.text = getString(R.string.translate_percent, opacity)
        tvRotation.text = getString(R.string.degrees, rotation.toString().replace('-', '−'))

        // The selected colour gets a ring.
        for (index in 0 until layoutColors.childCount) {
            val dot = layoutColors.getChildAt(index)
            val selected = dot.tag == color
            dot.setStroke(if (selected) dpToPx(2) else 0, if (selected) getColor(R.color.text_primary) else Color.TRANSPARENT)
            dot.invalidate()
        }

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
        toolbar.btnAction.alpha = if (text.isEmpty()) 0.5f else 1f
    }

    private fun colorWithOpacity(): Int = ColorUtils.setAlphaComponent(color, opacity * 255 / 100)

    private fun SeekBar.onChanged(listener: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) listener(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private companion object {
        const val PREVIEW_WIDTH = 900
        const val DEFAULT_OPACITY = 22
        const val DEFAULT_ROTATION = -32

        /** The rotation slider covers -90°..90°. */
        const val ROTATION_RANGE = 90

        val COLORS = listOf(
            0xFFD93A2F.toInt(), 0xFF20243A.toInt(), 0xFF4850F4.toInt(), 0xFF3984FF.toInt(), 0xFF9AA1B8.toInt()
        )
    }
}
