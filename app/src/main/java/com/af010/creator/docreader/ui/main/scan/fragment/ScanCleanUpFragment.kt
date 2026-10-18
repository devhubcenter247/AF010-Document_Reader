package com.af010.creator.docreader.ui.main.scan.fragment

import android.util.Log
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentScanCleanUpBinding
import com.af010.creator.docreader.ui.main.reader.pdf.onProgress
import com.af010.creator.docreader.ui.main.reader.pdf.setEnabledLook
import com.af010.creator.docreader.ui.main.scan.ScanSessionViewModel
import com.af010.creator.docreader.ui.main.scan.detection.DocumentImageProcessor
import com.ui.baselib.extensions.click
import com.ui.baselib.utils.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Paint over stains, shadows or stray marks of the selected page; Done fills them with the paper around. */
class ScanCleanUpFragment :
    BaseAppFragment<FragmentScanCleanUpBinding>(FragmentScanCleanUpBinding::inflate) {
    override val isFullSc: Boolean
        get() = true

    private val viewModel: ScanSessionViewModel by activityViewModel()
    private val processor = DocumentImageProcessor()
    private var busy = false

    private val index: Int get() = viewModel.selectedIndex.value ?: 0

    override fun FragmentScanCleanUpBinding.initView() {
        topBar.updatePadding(top = statusBarHeight)
        val page = viewModel.pageList.getOrNull(index)
        if (page == null) {
            navHost.goBackFragment()
            return
        }
        seekBrush.progress = DEFAULT_BRUSH
        cleanView.brushSize = brushSize(DEFAULT_BRUSH)
        cleanView.onStrokesChanged = { renderButtons() }
        renderButtons()
        runBusy {
            val preview = withContext(Dispatchers.Default) {
                runCatching { processor.loadPreview(page.output, PREVIEW_SIDE) }.getOrNull()
            }
            if (preview == null) {
                toast(R.string.scanner_capture_failed)
                navHost.goBackFragment()
            } else {
                bindingOrNull?.cleanView?.setImage(preview)
            }
        }
    }

    override fun FragmentScanCleanUpBinding.onClick() {
        btnBack.click { leave() }
        btnUndo.click { if (!busy) cleanView.undo() }
        seekBrush.onProgress { cleanView.brushSize = brushSize(it) }
        btnDone.click { apply() }
    }

    override fun onBackPressed(): Boolean {
        leave()
        return true
    }

    private fun leave() {
        if (!busy) navHost.goBackFragment()
    }

    private fun renderButtons() {
        val binding = bindingOrNull ?: return
        val painted = binding.cleanView.hasStrokes
        binding.btnUndo.setEnabledLook(painted)
        binding.btnDone.setEnabledLook(painted)
    }

    private fun brushSize(progress: Int): Float {
        val context = requireContext()
        val min = context.dpToPx(MIN_BRUSH_DP)
        val max = context.dpToPx(MAX_BRUSH_DP)
        return min + (max - min) * progress / 100f
    }

    private fun apply() {
        val target = index
        val page = viewModel.pageList.getOrNull(target) ?: return
        val strokes = binding.cleanView.strokes()
        if (strokes.isEmpty()) return
        runBusy {
            // Clean the unfiltered crop and filter it again, so a filter picked later keeps the clean-up.
            val cleaned = withContext(Dispatchers.Default) {
                runCatching {
                    val cropped = processor.cleanUp(page.cropped, strokes) ?: return@runCatching null
                    val output = processor.applyFilter(cropped, page.filter)
                    if (output == null) {
                        cropped.delete()
                        return@runCatching null
                    }
                    page.copy(cropped = cropped, output = output)
                }.onFailure { Log.e(TAG, "Unable to clean up scan", it) }.getOrNull()
            }
            if (cleaned == null) {
                toast(R.string.scanner_capture_failed)
            } else {
                viewModel.updatePage(target, cleaned)
                navHost.goBackFragment()
            }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val binding = bindingOrNull ?: return
        binding.progress.isVisible = value
        binding.cleanView.isEnabled = !value
    }

    private companion object {
        const val TAG = "ScanCleanUp"
        const val PREVIEW_SIDE = 1600
        const val DEFAULT_BRUSH = 35
        const val MIN_BRUSH_DP = 8
        const val MAX_BRUSH_DP = 56
    }
}
