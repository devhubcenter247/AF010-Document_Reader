package com.af010.creator.docreader.ui.main.scan.fragment

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
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
import com.af010.creator.docreader.ui.dialog.TextInputDialog
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.show
import com.bumptech.glide.Glide
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrInvisible
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
        // The bar has a fixed height: grow it by the status bar it now reaches under.
        topBar.root.updatePadding(top = statusBarHeight)
        topBar.root.updateLayoutParams { height += statusBarHeight }
        topBar.tvTitle.text = viewModel.name
        topBar.tvSubtitle.isVisible = true
        topBar.tvSubtitle.setText(R.string.fo_pick_a_look)
        topBar.btnFirst.setImageResource(R.drawable.ic_fo_pencil)
        topBar.btnFirst.contentDescription = getString(R.string.fo_rename)
        topBar.btnFirst.isVisible = true
        rcvFilters.adapter = filterAdapter
        filterAdapter.submitList(ScanFilter.entries.map { FilterPreview(it) })

        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            if (pages.isEmpty()) navHost.goBackFragment() else render()
        }
        viewModel.selectedIndex.observe(viewLifecycleOwner) { render() }
    }

    override fun FragmentScanFiltersBinding.onClick() {
        topBar.btnBack.click { addPage() }
        topBar.btnFirst.clickSingle { renameScan() }
        btnAddPage.click { addPage() }
        btnPrevious.click { viewModel.select(index - 1) }
        btnNext.click { viewModel.select(index + 1) }
        filterAdapter.onFilterClick { applyFilter(it) }

        btnCrop.click { cropAgain() }
        btnCleanUp.click { if (!busy && page != null) navigateTo(ScanActivity.ROUTE_CLEAN_UP) }
        btnDelete.click { if (!busy) viewModel.delete(index) }

        action.btnAction.clickSingle {
            val pages = viewModel.pageList
            if (busy || pages.isEmpty()) return@clickSingle
            val name = viewModel.name.replace('/', '-').trim().ifEmpty {
                "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            }
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
        binding.tvPage.text = getString(R.string.fo_x_of_y, index + 1, pages.size)
        binding.action.show(
            getString(R.string.fo_save_pdf_pages, resources.getQuantityString(R.plurals.translate_page_count, pages.size, pages.size)),
            R.drawable.ic_fo_download
        )
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

    private fun renameScan() {
        TextInputDialog(
            requireActivity(), R.string.fo_rename, R.string.fo_scan_name, R.string.fo_rename, viewModel.name
        ) { name ->
            viewModel.name = name.trim()
            bindingOrNull?.topBar?.tvTitle?.text = viewModel.name
        }.show()
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
            root.isSelected = isSelected
            ivPreview.setImageBitmap(item.bitmap)
            root.setOnClickListener { onItemClick.invoke(item.filter) }
        }
    }

    private companion object {
        const val PREVIEW_SIDE = 220
    }
}
