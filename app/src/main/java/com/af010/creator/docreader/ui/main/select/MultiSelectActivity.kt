package com.af010.creator.docreader.ui.main.select

import android.content.Intent
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityMultiSelectBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.model.StorageTab
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.dialog.DeleteDialog
import com.af010.creator.docreader.ui.main.files.DocumentAdapter
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.tools.openTool
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.setAllEnabled
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Pick several documents, then share or delete them. Shows the same list as the screen it was
 * opened from ([EXTRA_TAB], [EXTRA_TYPE], [EXTRA_FOLDER]); [EXTRA_SELECTED_ID] starts selected.
 */
class MultiSelectActivity :
    BaseAppActivity<ActivityMultiSelectBinding>(ActivityMultiSelectBinding::inflate) {
    private val viewModel: DocumentsViewModel by viewModel()
    private val documentAdapter by lazy { DocumentAdapter().apply { selectionMode = true } }

    private var documents: List<RecentDocument> = emptyList()
    private var selectedIds: Set<Long> = emptySet()

    private val selected: List<RecentDocument>
        get() = documents.filter { it.mediaId in selectedIds }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        // Read straight from the intent: the caller passes null for the extras it has no value for.
        val type = intent.getStringExtra(EXTRA_TYPE)
        viewModel.setScope(
            DocumentType.entries.firstOrNull { it.name == type },
            intent.getStringExtra(EXTRA_FOLDER)
        )
        val tab = intent.getStringExtra(EXTRA_TAB)
        StorageTab.entries.firstOrNull { it.name == tab }?.let { viewModel.setTab(it) }
        selectedIds = setOfNotNull(
            intent.getLongExtra(EXTRA_SELECTED_ID, NO_ID).takeIf { it != NO_ID }
        )
    }

    override fun ActivityMultiSelectBinding.setData() {
        rcvDocuments.adapter = documentAdapter

        viewModel.documents.mainCollect { response ->
            if (response !is DataResponse.DataSuccess) return@mainCollect
            documents = response.data.map { it.document }
            documentAdapter.submitList(response.data)
            // Drop ids of files that disappeared (deleted here or by another app).
            updateSelection(selectedIds intersect documents.map { it.mediaId }.toSet())
        }
    }

    override fun ActivityMultiSelectBinding.onClick() {
        btnClose.clickSingle { finish() }
        btnSelectAll.click {
            val allIds = documents.map { it.mediaId }.toSet()
            updateSelection(if (selectedIds == allIds) emptySet() else allIds)
        }
        documentAdapter.onDocumentClick {
            val id = it.document.mediaId
            updateSelection(if (id in selectedIds) selectedIds - id else selectedIds + id)
        }

        btnShare.clickSingle { shareSelected() }
        btnMerge.clickSingle {
            val pdfs = selected.filter { it.type == DocumentType.Pdf }
            if (pdfs.size < 2) {
                toast(R.string.merge_need_two_files)
            } else {
                openTool(Tool.Merge, pdfs.map { it.path })
                finish()
            }
        }
        btnZip.clickSingle {
            openTool(Tool.Zip, selected.map { it.path })
            finish()
        }
        btnDelete.clickSingle {
            val targets = selected
            DeleteDialog(this@MultiSelectActivity, targets.size) {
                viewModel.delete(targets) { failed ->
                    if (failed > 0) toast(getString(R.string.delete_selected_failed, failed))
                    else finish()
                }
            }.show()
        }
    }

    private fun ActivityMultiSelectBinding.updateSelection(ids: Set<Long>) {
        selectedIds = ids
        documentAdapter.selectedIds = ids
        tvCount.text = ids.size.toString()
        btnSelectAll.setText(if (ids.isNotEmpty() && ids.size == documents.size) R.string.fo_clear else R.string.select_all)
        val any = ids.isNotEmpty()
        btnShare.isEnabled = any
        btnZip.isEnabled = any
        btnDelete.isEnabled = any
        // Merge takes PDFs only.
        btnMerge.isEnabled = any && selected.all { it.type == DocumentType.Pdf }
    }

    private fun shareSelected() {
        val uris = ArrayList(selected.map { viewModel.shareUri(it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        }.onFailure { toast(R.string.file_share_failed) }
    }

    companion object {
        const val EXTRA_TAB = "EXTRA_TAB"
        const val EXTRA_TYPE = "EXTRA_TYPE"
        const val EXTRA_FOLDER = "EXTRA_FOLDER"
        const val EXTRA_SELECTED_ID = "EXTRA_SELECTED_ID"
        private const val NO_ID = Long.MIN_VALUE
    }
}
