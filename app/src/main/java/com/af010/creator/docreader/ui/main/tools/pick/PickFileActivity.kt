package com.af010.creator.docreader.ui.main.tools.pick

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityPickFileBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.show
import com.af010.creator.docreader.ui.main.common.showNoAccess
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.files.SearchScope
import com.af010.creator.docreader.ui.main.tools.TOOL_STEPS
import com.af010.creator.docreader.ui.common.show
import com.ui.baselib.extensions.afterTextChanged
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.openTool
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.clickSingle
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

/**
 * Picks the files a tool works on, from the device list or the system file picker. Merge and ZIP
 * take several files, the other tools one. Continues to the tool, or with [EXTRA_FOR_RESULT]
 * hands the paths back to the caller in [EXTRA_PATHS].
 */
class PickFileActivity : BaseAppActivity<ActivityPickFileBinding>(ActivityPickFileBinding::inflate) {
    private val viewModel: DocumentsViewModel by viewModel()
    private val fileAdapter by lazy { PickFileAdapter(numbered = multiple) }

    private val tool: Tool by lazy {
        val name = intent.getStringExtra(EXTRA_TOOL)
        Tool.entries.firstOrNull { it.name == name } ?: Tool.Merge
    }
    private val multiple: Boolean get() = tool == Tool.Merge || tool == Tool.Zip
    /** Office to PDF takes the files the Office screen can draw as pages. */
    private val officeOnly: Boolean get() = tool == Tool.OfficeToPdf
    private val pdfOnly: Boolean get() = tool != Tool.Zip && !officeOnly
    private val scopeType: DocumentType? get() = if (pdfOnly) DocumentType.Pdf else null
    private val minimum: Int
        get() = if (tool == Tool.Merge && !intent.getBooleanExtra(EXTRA_FOR_RESULT, false)) 2 else 1

    private var selected: List<String> = emptyList()
    private var noAccess = false

    private val browseLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) importFiles(if (multiple) uris else uris.take(1))
    }

    private val permissionRequester = StoragePermissionRequester(this) {
        viewModel.refresh()
    }

    override fun backPressed() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Access may have been granted or revoked in system Settings while the app was away.
        viewModel.refresh()
    }

    override fun initialize() {
        // ZIP bundles any file, pictures included.
        if (tool == Tool.Zip) viewModel.includeImages()
        viewModel.setScope(scopeType, null)
    }

    override fun ActivityPickFileBinding.setData() {
        topBar.tvTitle.setText(tool.title)
        steps.show(1, TOOL_STEPS, getString(R.string.fo_step_pick_files))
        edtSearch.setHint(if (pdfOnly) R.string.fo_search_your_pdfs else R.string.fo_search_your_files)
        viewModel.setSearchScope(SearchScope.FileName)
        rcvDocuments.adapter = fileAdapter

        viewModel.documents.mainCollect { response ->
            noAccess = response.isNoAccess
            val files = (response as? DataResponse.DataSuccess)?.data?.let { all ->
                if (officeOnly) all.filter { it.document.type == DocumentType.Doc || it.document.type == DocumentType.Ppt }
                else all
            }
            if (files != null) fileAdapter.submitList(files)
            rcvDocuments.isVisible = !files.isNullOrEmpty()
            layoutCount.isVisible = !noAccess
            files?.let {
                tvCount.text = resources.getQuantityString(
                    if (pdfOnly) R.plurals.fo_pdfs_on_phone else R.plurals.fo_files_on_phone, it.size, it.size
                )
            }
            when {
                noAccess -> emptyState.showNoAccess(R.string.pick_no_access_desc, R.string.browse_files)
                files?.isEmpty() == true -> emptyState.show(
                    if (pdfOnly) getString(R.string.no_type_file_found, getString(R.string.tab_pdf))
                    else getString(R.string.no_file_found),
                    R.string.pick_empty_desc,
                    R.string.browse_files
                )
                else -> emptyState.root.isVisible = false
            }
        }
        render()
    }

    override fun ActivityPickFileBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        // Browsing works without storage access: the system picker hands the files over.
        emptyState.btnPrimary.clickSingle {
            if (noAccess) permissionRequester.request(this@PickFileActivity) else browse()
        }
        emptyState.btnSecondary.clickSingle { browse() }
        btnBrowse.clickSingle { browse() }
        edtSearch.afterTextChanged { viewModel.setQuery(it) }
        fileAdapter.onFileClick {
            val path = it.document.path
            if (!multiple) {
                proceed(listOf(path))
            } else {
                selected = if (path in selected) selected - path else selected + path
                render()
            }
        }
        btnNext.clickSingle { proceed(selected) }
    }

    private fun browse() {
        browseLauncher.launch(
            when {
                officeOnly -> OFFICE_TYPES
                pdfOnly -> arrayOf("application/pdf")
                else -> arrayOf("*/*")
            }
        )
    }

    private fun ActivityPickFileBinding.render() {
        fileAdapter.selectedPaths = selected
        layoutPicked.isVisible = multiple
        viewFade.isVisible = multiple
        val ready = selected.size >= minimum
        tvPicked.text = resources.getQuantityString(R.plurals.fo_files_picked, selected.size, selected.size)
        tvPickedHint.setText(
            when {
                tool == Tool.Merge -> if (ready) R.string.fo_merge_order_hint else R.string.fo_merge_need_two
                else -> if (ready) R.string.fo_zip_hint else R.string.fo_zip_need_one
            }
        )
        btnNext.isEnabled = ready
    }

    /** Copies files chosen in the system picker into the cache so the tools can read them as files. */
    private fun importFiles(uris: List<Uri>) {
        launchIO {
            val directory = File(cacheDir, IMPORT_DIR).apply { mkdirs() }
            val paths = uris.mapNotNull { uri ->
                runCatching {
                    val target = File(directory, displayName(uri))
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    target.path.takeIf { target.length() > 0 }
                }.getOrNull()
            }
            withMain {
                if (paths.isEmpty()) toast(R.string.error_file_open_generic)
                else proceed(if (multiple) selected + paths else paths)
            }
        }
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "file_${System.currentTimeMillis()}"

    private fun proceed(paths: List<String>) {
        if (intent.getBooleanExtra(EXTRA_FOR_RESULT, false)) {
            setResult(Activity.RESULT_OK, Intent().putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths)))
        } else {
            openTool(tool, paths)
        }
        finish()
    }

    companion object {
        const val EXTRA_TOOL = "EXTRA_TOOL"
        const val EXTRA_FOR_RESULT = "EXTRA_FOR_RESULT"
        private const val IMPORT_DIR = "picked_files"
        private val OFFICE_TYPES = arrayOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )

        fun start(context: Context, tool: Tool) {
            context.startActivity(intent(context, tool, forResult = false))
        }

        fun intent(context: Context, tool: Tool, forResult: Boolean): Intent =
            Intent(context, PickFileActivity::class.java)
                .putExtra(EXTRA_TOOL, tool.name)
                .putExtra(EXTRA_FOR_RESULT, forResult)
    }
}
