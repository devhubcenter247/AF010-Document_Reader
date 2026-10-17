package com.af010.creator.docreader.ui.main.scan.fragment

import android.util.Log
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentScanCropBinding
import com.af010.creator.docreader.ui.main.scan.PendingCrop
import com.af010.creator.docreader.ui.main.scan.ScanActivity
import com.af010.creator.docreader.ui.main.scan.ScanPage
import com.af010.creator.docreader.ui.main.scan.ScanSessionViewModel
import com.af010.creator.docreader.ui.main.scan.detection.DocumentImageProcessor
import com.af010.creator.docreader.ui.main.scan.detection.ScanFilter
import com.ui.baselib.extensions.click
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.opencv.core.Point

/** Drag the four corners onto the document's edges; Next straightens the page. */
class ScanCropFragment : BaseAppFragment<FragmentScanCropBinding>(FragmentScanCropBinding::inflate) {
    override val isFullSc: Boolean
        get() = true

    private val viewModel: ScanSessionViewModel by activityViewModel()
    private val processor = DocumentImageProcessor()
    private var busy = false

    /** True when an already scanned page is cropped again, so its photo must be kept. */
    private val recropping: Boolean get() = viewModel.retakeIndex != null

    override fun FragmentScanCropBinding.initView() {
        topBar.updatePadding(top = statusBarHeight)
        val pending = viewModel.pendingCrop
        if (pending == null) {
            navHost.goBackFragment()
            return
        }
        show(pending)
        // A retaken page keeps its place; a new one goes after the pages already scanned.
        val count = viewModel.pages.value.orEmpty().size
        val number = viewModel.retakeIndex?.plus(1) ?: (count + 1)
        tvPage.text = getString(R.string.fo_page_x_of_y, number, maxOf(count, number))
    }

    override fun FragmentScanCropBinding.onClick() {
        btnBack.click { leave() }
        btnRotate.click { rotate() }
        btnAutoDetect.click { autoDetect() }
        btnFullPage.click {
            val pending = viewModel.pendingCrop ?: return@click
            show(pending.copy(corners = fullPage))
        }
        btnNext.click { apply() }
        btnRetake.click { leave() }
    }

    override fun onBackPressed(): Boolean {
        leave()
        return true
    }

    private fun show(pending: PendingCrop) {
        viewModel.pendingCrop = pending
        binding.cornerView.setImage(pending.original, pending.width, pending.height, pending.corners)
    }

    private fun leave() {
        if (busy) return
        if (recropping) {
            viewModel.pendingCrop = null
            viewModel.retakeIndex = null
        } else {
            viewModel.discardPending()
        }
        navHost.goBackFragment()
    }

    private fun rotate() {
        val pending = viewModel.pendingCrop ?: return
        val corners = binding.cornerView.corners().map { Point(1.0 - it.y, it.x) }
        runBusy {
            val rotated = withContext(Dispatchers.Default) { processor.rotateClockwise(pending.original) }
            if (rotated == null) {
                toast(R.string.scanner_capture_failed)
                return@runBusy
            }
            if (!recropping) pending.original.delete()
            show(PendingCrop(rotated, pending.height, pending.width, corners))
        }
    }

    private fun autoDetect() {
        val pending = viewModel.pendingCrop ?: return
        runBusy {
            val detected = withContext(Dispatchers.Default) {
                runCatching { processor.prepareCrop(pending.original) }.getOrNull()
            }
            if (detected != null) show(detected)
        }
    }

    private fun apply() {
        val pending = viewModel.pendingCrop ?: return
        val corners = binding.cornerView.corners()
        // A page cropped again keeps the filter picked for it.
        val filter = viewModel.retakeIndex?.let { viewModel.pageList.getOrNull(it) }?.filter ?: ScanFilter.Default
        runBusy {
            val page = withContext(Dispatchers.Default) {
                runCatching {
                    val scan = processor.process(pending.original, corners)
                    val output = processor.applyFilter(scan.output, filter)
                    if (output == null) ScanPage(scan.original, cropped = scan.output)
                    else ScanPage(scan.original, cropped = scan.output, output = output, filter = filter)
                }
                    .onFailure { Log.e(TAG, "Unable to crop scan", it) }
                    .getOrNull()
            }
            if (page == null) {
                toast(R.string.scanner_capture_failed)
                return@runBusy
            }
            val wasRecropping = recropping
            viewModel.pendingCrop = null
            viewModel.addOrReplace(page)
            if (wasRecropping) {
                // The filters screen is right underneath.
                navHost.goBackFragment()
            } else {
                navHost.navigatePopUpTo(
                    routeName = ScanActivity.ROUTE_FILTERS,
                    popUpTo = ScanActivity.ROUTE_CROP,
                    popUpToInclusive = true,
                )
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
                bindingOrNull?.let { setBusy(false) }
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.isVisible = value
        binding.btnNext.isEnabled = !value
    }

    private companion object {
        const val TAG = "ScanCrop"
        val fullPage = listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(1.0, 1.0), Point(0.0, 1.0))
    }
}
