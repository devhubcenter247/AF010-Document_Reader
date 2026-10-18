package com.af010.creator.docreader.ui.main.scan

import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityScanBinding
import com.af010.creator.docreader.ui.main.scan.fragment.ScanCameraFragment
import com.af010.creator.docreader.ui.main.scan.fragment.ScanCleanUpFragment
import com.af010.creator.docreader.ui.main.scan.fragment.ScanCropFragment
import com.af010.creator.docreader.ui.main.scan.fragment.ScanFiltersFragment
import com.ui.baselib.base.NavGraph
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Document scanner: camera (edges found live) → adjust corners → filters and clean-up per page → save as PDF.
 * The pages of the session live in [ScanSessionViewModel], shared by the fragments.
 */
class ScanActivity : BaseAppActivity<ActivityScanBinding>(ActivityScanBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    override val fragmentContainerId: Int = R.id.fragmentContainer

    override val navGraph: NavGraph = NavGraph().apply {
        addDestination(ROUTE_CAMERA, isStartDestination = true) { ScanCameraFragment() }
        addDestination(ROUTE_CROP) { ScanCropFragment() }
        addDestination(ROUTE_FILTERS) { ScanFiltersFragment() }
        addDestination(ROUTE_CLEAN_UP) { ScanCleanUpFragment() }
    }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        if (!OpenCVLoader.initLocal()) {
            toast(R.string.scanner_capture_failed)
            finish()
            return
        }
        // Photos of earlier sessions; recent ones may still be read by a PDF being saved.
        launchIO {
            val cutoff = System.currentTimeMillis() - STALE_AFTER_MS
            scanDirectory(this@ScanActivity).listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        }
    }

    override fun ActivityScanBinding.setData() {

    }

    override fun ActivityScanBinding.onClick() {

    }

    companion object {
        const val ROUTE_CAMERA = "scan_camera"
        const val ROUTE_CROP = "scan_crop"
        const val ROUTE_FILTERS = "scan_filters"
        const val ROUTE_CLEAN_UP = "scan_clean_up"
        private const val STALE_AFTER_MS = 10 * 60 * 1000L

        /** Where the session's photos are kept until the PDF is written. */
        fun scanDirectory(context: android.content.Context): File =
            File(context.cacheDir, "scanner").apply { mkdirs() }
    }
}
