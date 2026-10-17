package com.af010.creator.docreader.ui.main.documents

import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityFileListBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.show
import com.af010.creator.docreader.ui.main.common.showNoAccess
import com.af010.creator.docreader.ui.main.files.DocumentActions
import com.af010.creator.docreader.ui.main.files.DocumentAdapter
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.files.sheet.SortBottomSheet
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.af010.creator.docreader.ui.main.select.MultiSelectActivity
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.af010.creator.docreader.utils.extensions.titleRes
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import org.koin.androidx.viewmodel.ext.android.viewModel

/** The documents of one type ([EXTRA_TYPE]) or one folder ([EXTRA_FOLDER]). */
class FileListActivity : BaseAppActivity<ActivityFileListBinding>(ActivityFileListBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val viewModel: DocumentsViewModel by viewModel()
    private val documentAdapter by lazy { DocumentAdapter() }
    private val documentActions by lazy { DocumentActions(this, viewModel) }

    private val type: DocumentType? by lazy {
        intent.getStringExtra(EXTRA_TYPE)?.let { name -> DocumentType.entries.firstOrNull { it.name == name } }
    }
    private val folder: String? by lazy { intent.getStringExtra(EXTRA_FOLDER) }

    private val permissionRequester = StoragePermissionRequester(this) {
        viewModel.refresh()
    }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        viewModel.setScope(type, folder)
    }

    override fun ActivityFileListBinding.setData() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.text = type?.let { getString(it.titleRes) } ?: folder
        toolbar.btnSort.visible()
        toolbar.btnSearch.visible()
        rcvDocuments.adapter = documentAdapter

        viewModel.documents.mainCollect { response ->
            val documents = (response as? DataResponse.DataSuccess)?.data
            if (documents != null) documentAdapter.submitList(documents)
            layoutDocuments.visibleOrGone(!documents.isNullOrEmpty())
            when {
                response.isNoAccess -> emptyState.showNoAccess()
                documents?.isEmpty() == true -> emptyState.show(
                    type?.let { getString(R.string.no_type_file_found, getString(it.titleRes)) }
                        ?: getString(R.string.no_file_found),
                    R.string.file_list_empty_desc
                )
                else -> emptyState.root.gone()
            }
        }
    }

    override fun ActivityFileListBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        emptyState.btnPrimary.clickSingle { permissionRequester.request(this@FileListActivity) }
        toolbar.btnSearch.clickSingle { launchActivity<SearchActivity>() }
        toolbar.btnSort.clickSingle {
            SortBottomSheet(this@FileListActivity, viewModel.filter.value.sort) {
                viewModel.setSort(it)
            }.show()
        }
        documentAdapter
            .onDocumentClick { documentActions.open(it.document) }
            .onMoreClick { documentActions.showOptions(it) }
            .onDocumentLongClick {
                launchActivity<MultiSelectActivity>(
                    MultiSelectActivity.EXTRA_TYPE to type?.name,
                    MultiSelectActivity.EXTRA_FOLDER to folder,
                    MultiSelectActivity.EXTRA_SELECTED_ID to it.document.mediaId
                )
            }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    companion object {
        const val EXTRA_TYPE = "EXTRA_TYPE"
        const val EXTRA_FOLDER = "EXTRA_FOLDER"
    }
}
