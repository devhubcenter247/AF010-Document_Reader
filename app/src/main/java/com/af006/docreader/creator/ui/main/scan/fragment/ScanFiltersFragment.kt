package com.af010.creator.docreader.ui.main.scan.fragment

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentScanFiltersBinding
import com.af010.creator.docreader.databinding.ItemScanFilterBinding
import com.af010.creator.docreader.model.PageMargin
import com.af010.creator.docreader.model.PageOrientation
import com.af010.creator.docreader.model.PageSetup
import com.af010.creator.docreader.model.PageSize
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.scan.ScanActivity
import com.af010.creator.docreader.ui.main.scan.ScanPage
import com.af010.creator.docreader.ui.main.scan.ScanSessionViewModel
import com.af010.creator.docreader.ui.main.scan.detection.DocumentImageProcessor
import com.af010.creator.docreader.ui.main.scan.detection.ScanFilter
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.utils.extensions.setStroke
import com.bumptech.glide.Glide
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrInvisible
import com.ui.baselib.utils.dpToPx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Review the scanned pages: pick a filter per page, rotate, crop again, delete, then save as PDF. */
class ScanFiltersFragment :
    BaseAppFragment<FragmentScanFiltersBinding>(FragmentScanFiltersBinding::inflate) {
    override val isFullSc: Boolean
        get() = true

    private val viewModel: ScanSessionViewModel by activityViewModel()
    private val processor = DocumentImageProcessor()
    private val filterAdapter by lazy { FilterAdapter() }

    private var busy = false
    private var previewJob: Job? = null

    /** The crop the filter thumbnails were last made from. */
    private var previewSource: String? = null

    private val index: Int get() = viewModel.selectedIndex.value ?: 0
    private val page: ScanPage? get() = viewModel.pageList.getOrNull(index)

    override fun FragmentScanFiltersBinding.initView() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvSubtitle.visible()
        toolbar.tvSubtitle.setText(R.string.scan_tap_filter)
        toolbar.btnAction.setText(R.string.save_pdf)
        toolbar.btnAction.visible()
        rcvFilters.adapter = filterAdapter
        filterAdapter.submitList(ScanFilter.entries.map { FilterPreview(it) })

        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            if (pages.isEmpty()) navHost.goBackFragment() else render()
        }
        viewModel.selectedIndex.observe(viewLifecycleOwner) { render() }
    }

    override fun FragmentScanFiltersBinding.onClick() {
        toolbar.btnBack.click { addPage() }
        btnAddPage.click { addPage() }
        btnPrevious.click { viewModel.select(index - 1) }
        btnNext.click { viewModel.select(index + 1) }
        filterAdapter.onFilterClick { applyFilter(it) }

        btnCrop.click { cropAgain() }
        btnRotate.click { rotate() }
        btnCleanUp.click { if (!busy && page != null) navigateTo(ScanActivity.ROUTE_CLEAN_UP) }
        btnDelete.click { if (!busy) viewModel.delete(index) }

        toolbar.btnAction.clickSingle {
            val pages = viewModel.pageList
            if (busy || pages.isEmpty()) return@clickSingle
            val name = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            ProcessingActivity.start(
                appContext(),
                ToolRequest.ImagesToPdf(
                    pages.map { it.output.path }, name,
                    PageSetup(PageSize.Fit, PageOrientation.Portrait, PageMargin.None),
                    fromScan = true
                )
            )
            activity?.finish()
        }
    }

    override fun onBackPressed(): Boolean {
        addPage()
        return true
    }

    /** Back to the camera, keeping the pages scanned so far. */
    private fun addPage() {
        if (busy) return
        viewModel.retakeIndex = null
        navHost.goBackFragment()
    }

    private fun render() {
        val binding = bindingOrNull ?: return
        val pages = viewModel.pageList
        val page = page ?: return
        binding.toolbar.tvTitle.text = getString(R.string.page_of, index + 1, pages.size)
        binding.btnPrevious.visibleOrInvisible(index > 0)
        binding.btnNext.visibleOrInvisible(index < pages.lastIndex)
        Glide.with(this).load(page.output).into(binding.ivPage)
        filterAdapter.selected = page.filter
        loadFilterPreviews(page)
    }

    /** Small versions of the page with each filter applied, for the strip under the page. */
    private fun loadFilterPreviews(page: ScanPage) {
        if (previewSource == page.cropped.path) return
        previewSource = page.cropped.path
        previewJob?.cancel()
        previewJob = viewLifecycleOwner.lifecycleScope.launch {
            val base = withContext(Dispatchers.Default) {
                runCatching { processor.loadPreview(page.cropped, PREVIEW_SIDE) }.getOrNull()
            } ?: return@launch
            for (filter in ScanFilter.entries) {
                val bitmap = withContext(Dispatchers.Default) {
                    runCatching { processor.filterBitmap(base, filter) }.getOrNull()
                } ?: continue
                filterAdapter.updateItem({ it.filter == filter }) { it.copy(bitmap = bitmap) }
            }
        }
    }

    private fun applyFilter(filter: ScanFilter) {
        val page = page ?: return
        if (page.filter == filter) return
        val target = index
        runBusy {
            // Always from the unfiltered crop, so filters never stack.
            val output = withContext(Dispatchers.Default) {
                runCatching { processor.applyFilter(page.cropped, filter) }.getOrNull()
            }
            if (output == null) toast(R.string.scanner_capture_failed)
            else viewModel.updatePage(target, page.copy(output = output, filter = filter))
        }
    }

    private fun rotate() {
        val page = page ?: return
        val target = index
        runBusy {
            // Rotate the unfiltered crop too, so a filter picked later keeps the rotation.
            val rotated = withContext(Dispatchers.Default) {
                val cropped = processor.rotateClockwise(page.cropped) ?: return@withContext null
                if (page.output == page.cropped) return@withContext page.copy(cropped = cropped, output = cropped)
                val output = processor.rotateClockwise(page.output)
                if (output == null) {
                    cropped.delete()
                    return@withContext null
                }
                page.copy(cropped = cropped, output = output)
            }
            if (rotated == null) toast(R.string.scanner_capture_failed)
            else viewModel.updatePage(target, rotated)
        }
    }

    /** Opens the corner screen on the page's original photo; the result replaces the page. */
    private fun cropAgain() {
        val page = page ?: return
        val target = index
        runBusy {
            val pending = withContext(Dispatchers.Default) {
                runCatching { processor.prepareCrop(page.original) }.getOrNull()
            }
            if (pending == null) {
                toast(R.string.scanner_capture_failed)
            } else {
                viewModel.retakeIndex = target
                viewModel.pendingCrop = pending
                navigateTo(ScanActivity.ROUTE_CROP)
            }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        bindingOrNull?.progress?.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } finally {
                busy = false
                bindingOrNull?.progress?.isVisible = false
            }
        }
    }

    private data class FilterPreview(val filter: ScanFilter, val bitmap: Bitmap? = null)

    private class FilterAdapter : BaseAdapter<FilterPreview, ItemScanFilterBinding>() {
        override fun createBinding(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ) = ItemScanFilterBinding.inflate(inflater, parent, false)

        private var onItemClick: ((ScanFilter) -> Unit) = {}
        fun onFilterClick(listener: (ScanFilter) -> Unit) = apply {
            onItemClick = listener
        }

        var selected: ScanFilter = ScanFilter.Default
            set(value) {
                if (field == value) return
                field = value
                notifyItemRangeChanged(0, itemCount)
            }

        override fun ItemScanFilterBinding.bind(item: FilterPreview, position: Int) {
            val context = root.context
            val isSelected = item.filter == selected
            tvLabel.setText(item.filter.label)
            tvLabel.setTextColor(
                ContextCompat.getColor(context, if (isSelected) R.color.text_on_selected else R.color.text_primary)
            )
            card.setStroke(if (isSelected) context.dpToPx(3) else 0, ContextCompat.getColor(context, R.color.primary))
            card.invalidate()
            ivPreview.setImageBitmap(item.bitmap)
            root.setOnClickListener { onItemClick.invoke(item.filter) }
        }
    }

    private companion object {
        const val PREVIEW_SIDE = 220
    }
}
