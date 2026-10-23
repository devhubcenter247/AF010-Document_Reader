package com.af010.creator.docreader.ui.main.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityScanBinding
import com.af010.creator.docreader.ui.main.scan.fragment.ScanCameraFragment
import com.af010.creator.docreader.ui.main.scan.fragment.ScanCleanUpFragment
import com.af010.creator.docreader.ui.main.scan.fragment.ScanCropFragment
import com.af010.creator.docreader.ui.main.scan.detection.ScanFilter
import com.af010.creator.docreader.ui.main.scan.fragment.ScanFiltersFragment
import com.ui.baselib.base.NavGraph
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.opencv.android.OpenCVLoader
import java.io.File

/**
 * Document scanner: camera (edges found live) → adjust corners → filters and clean-up per page → save as PDF.
 * The pages of the session live in [ScanSessionViewModel], shared by the fragments.
 *
 * Started through [EditPhotos] it edits photos picked elsewhere instead: it opens on the filters
 * screen with those photos as pages, and Done (or back) hands the edited pages back.
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

    private val session: ScanSessionViewModel by viewModel()

    /** True when editing photos given by [EditPhotos] rather than scanning. */
    val isEditing: Boolean get() = intent.hasExtra(EXTRA_ORIGINALS)

    override fun startDestination(): String? = if (isEditing) ROUTE_FILTERS else super.startDestination()

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        if (!OpenCVLoader.initLocal()) {
            toast(R.string.scanner_capture_failed)
            finish()
            return
        }
        if (isEditing) {
            // Also after the process was killed: the session is gone, start again from the photos given.
            if (session.pageList.isEmpty()) session.startEditing(intent.readPages(), intent.getIntExtra(EXTRA_INDEX, 0))
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

    /** Ends an [EditPhotos] session, handing the pages back as they are now. */
    fun keepEdits() {
        session.keepEdits()
        setResult(Activity.RESULT_OK, Intent().putPages(session.pageList))
        finish()
    }

    /** Ends an [EditPhotos] session leaving the photos as they were given. */
    fun discardEdits() {
        session.discardEdits()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    /** Opens the editor on photos (as pages, [index] shown first); returns the edited pages, null when left without them. */
    class EditPhotos : ActivityResultContract<EditPhotos.Input, List<ScanPage>?>() {
        data class Input(val pages: List<ScanPage>, val index: Int)

        override fun createIntent(context: Context, input: Input): Intent =
            Intent(context, ScanActivity::class.java).putPages(input.pages).putExtra(EXTRA_INDEX, input.index)

        override fun parseResult(resultCode: Int, intent: Intent?): List<ScanPage>? =
            intent?.takeIf { resultCode == Activity.RESULT_OK }?.readPages()
    }

    companion object {
        const val ROUTE_CAMERA = "scan_camera"
        const val ROUTE_CROP = "scan_crop"
        const val ROUTE_FILTERS = "scan_filters"
        const val ROUTE_CLEAN_UP = "scan_clean_up"
        private const val STALE_AFTER_MS = 10 * 60 * 1000L
        private const val EXTRA_ORIGINALS = "EXTRA_ORIGINALS"
        private const val EXTRA_CROPPED = "EXTRA_CROPPED"
        private const val EXTRA_OUTPUTS = "EXTRA_OUTPUTS"
        private const val EXTRA_FILTERS = "EXTRA_FILTERS"
        private const val EXTRA_INDEX = "EXTRA_INDEX"

        private fun Intent.putPages(pages: List<ScanPage>): Intent = apply {
            putExtra(EXTRA_ORIGINALS, pages.map { it.original.path }.toTypedArray())
            putExtra(EXTRA_CROPPED, pages.map { it.cropped.path }.toTypedArray())
            putExtra(EXTRA_OUTPUTS, pages.map { it.output.path }.toTypedArray())
            putExtra(EXTRA_FILTERS, pages.map { it.filter.name }.toTypedArray())
        }

        private fun Intent.readPages(): List<ScanPage> {
            val originals = getStringArrayExtra(EXTRA_ORIGINALS) ?: return emptyList()
            val cropped = getStringArrayExtra(EXTRA_CROPPED) ?: originals
            val outputs = getStringArrayExtra(EXTRA_OUTPUTS) ?: cropped
            val filters = getStringArrayExtra(EXTRA_FILTERS).orEmpty()
            return originals.indices.map { i ->
                ScanPage(
                    original = File(originals[i]),
                    cropped = File(cropped[i]),
                    output = File(outputs[i]),
                    filter = ScanFilter.entries.find { it.name == filters.getOrNull(i) } ?: ScanFilter.Default,
                )
            }
        }

        /** Where the session's photos are kept until the PDF is written. */
        fun scanDirectory(context: Context): File =
            File(context.cacheDir, "scanner").apply { mkdirs() }
    }
}
