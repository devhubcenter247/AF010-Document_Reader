package com.af010.creator.docreader.ui.main.reader.pdf.reflow

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityPdfReflowBinding
import com.af010.creator.docreader.databinding.ItemReflowTextBinding
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfReadingPrefs
import com.af010.creator.docreader.ui.main.reader.pdf.onProgress
import com.reader.pdfviewer.pdfium.PdfPageText
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import org.koin.android.ext.android.inject
import java.io.File
import kotlin.math.abs

/** The text of a PDF as paragraphs that wrap to the screen, with a text size slider. */
class PdfReflowActivity : BaseAppActivity<ActivityPdfReflowBinding>(ActivityPdfReflowBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val toolbox: PdfToolbox by inject()
    private val prefs by lazy { PdfReadingPrefs(this) }
    private val source: File by lazy { File(intent.getStringExtra(EXTRA_PATH).orEmpty()) }
    private val password: String? by lazy { intent.getStringExtra(EXTRA_PASSWORD) }
    private val startPage: Int by lazy { intent.getIntExtra(EXTRA_PAGE, 0) }

    private val adapter by lazy { ReflowAdapter() }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityPdfReflowBinding.setData() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.setText(R.string.text_view)
        toolbar.tvSubtitle.setText(R.string.text_view_subtitle)
        toolbar.tvSubtitle.visible()

        adapter.sizeSp = prefs.reflowSize
        rcvText.adapter = adapter
        seekSize.progress = ((prefs.reflowSize - MIN_SIZE) / SIZE_STEP).toInt()
        read()
    }

    override fun ActivityPdfReflowBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        seekSize.onProgress { progress ->
            val size = MIN_SIZE + progress * SIZE_STEP
            prefs.reflowSize = size
            adapter.sizeSp = size
        }
    }

    /** Pages are added as they are read; the page the reader was on is scrolled to. */
    private fun read() = launchMain {
        val count = runCatching { toolbox.pageCount(source, password) }.getOrDefault(0)
        var scrolled = false
        for (page in 0 until count) {
            val text = runCatching { toolbox.pageLayout(source, page, password) }.getOrNull() ?: continue
            val paragraphs = paragraphsOf(text)
            if (paragraphs.isEmpty()) continue
            val first = adapter.itemCount
            adapter.addItems(
                listOf(ReflowBlock(getString(R.string.page_number, page + 1), true)) +
                        paragraphs.map { ReflowBlock(it, false) }
            )
            binding.progress.gone()
            if (!scrolled && page >= startPage) {
                scrolled = true
                (binding.rcvText.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(first, 0)
            }
        }
        binding.progress.gone()
        binding.tvEmpty.visibleOrGone(adapter.isEmpty())
    }

    companion object {
        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_PASSWORD = "extra_password"
        private const val EXTRA_PAGE = "extra_page"
        private const val MIN_SIZE = 12f
        private const val SIZE_STEP = 1.5f

        fun intent(context: Context, path: String, password: String?, page: Int): Intent =
            Intent(context, PdfReflowActivity::class.java)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_PASSWORD, password)
                .putExtra(EXTRA_PAGE, page)

        /**
         * The lines of a page joined into paragraphs: a new one after a wider gap than between
         * lines, or after a short line that ends a sentence. Words cut with a hyphen are joined.
         */
        internal fun paragraphsOf(text: PdfPageText): List<String> {
            val lines = text.lines.filter { it.text.isNotBlank() }
            if (lines.isEmpty()) return emptyList()
            val widest = lines.maxOf { it.bounds.width() }
            val gaps = lines.zipWithNext { a, b -> b.bounds.top - a.bounds.bottom }.filter { it >= 0 }.sorted()
            val usualGap = gaps.getOrNull(gaps.size / 2) ?: 0f
            val result = ArrayList<String>()
            val current = StringBuilder()
            lines.forEachIndexed { i, line ->
                val prev = lines.getOrNull(i - 1)
                if (prev != null && current.isNotEmpty()) {
                    val gap = line.bounds.top - prev.bounds.bottom
                    val height = abs(prev.bounds.height()).coerceAtLeast(1f)
                    val shortEnd = prev.bounds.width() < widest * 0.7f &&
                            prev.text.trimEnd().lastOrNull()?.let { it in ".!?:…\"”)" } == true
                    if (gap > usualGap + height * 0.6f || gap < -height * 2 || shortEnd) {
                        result += current.toString().trim(); current.clear()
                    }
                }
                val t = line.text.trim()
                if (current.endsWith("-") && t.firstOrNull()?.isLowerCase() == true) current.setLength(current.length - 1)
                else if (current.isNotEmpty()) current.append(' ')
                current.append(t)
            }
            if (current.isNotBlank()) result += current.toString().trim()
            return result
        }
    }
}

/** A paragraph, or the label of the page the next paragraphs come from ([isPage]). */
data class ReflowBlock(val text: String, val isPage: Boolean)

class ReflowAdapter : BaseAdapter<ReflowBlock, ItemReflowTextBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemReflowTextBinding.inflate(inflater, parent, false)

    var sizeSp: Float = 16f
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemReflowTextBinding.bind(item: ReflowBlock, position: Int) {
        val context = root.context
        tvText.text = item.text
        tvText.typeface = ResourcesCompat.getFont(context, if (item.isPage) R.font.nunito_900 else R.font.nunito_600)
        tvText.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (item.isPage) PAGE_LABEL_SP else sizeSp)
        tvText.setTextColor(
            ContextCompat.getColor(context, if (item.isPage) R.color.text_secondary else R.color.text_primary)
        )
    }

    private companion object {
        const val PAGE_LABEL_SP = 12f
    }
}
