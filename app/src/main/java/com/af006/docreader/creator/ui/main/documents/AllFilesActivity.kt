package com.af010.creator.docreader.ui.main.documents

import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityAllFilesBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.StorageTab
import com.af010.creator.docreader.ui.main.common.FilterChip
import com.af010.creator.docreader.ui.main.common.FilterChipAdapter
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.common.showNoAccess
import com.af010.creator.docreader.ui.main.files.DocumentActions
import com.af010.creator.docreader.ui.main.files.DocumentAdapter
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.files.sheet.SortBottomSheet
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.af010.creator.docreader.ui.main.select.MultiSelectActivity
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.clickSingle
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Every document, as Recent / Favourites / All, narrowed by type and sorted. */
class AllFilesActivity : BaseAppActivity<ActivityAllFilesBinding>(ActivityAllFilesBinding::inflate) {

    private val viewModel: DocumentsViewModel by viewModel()
    private val rows = DocumentAdapter()
    private val typeChips = FilterChipAdapter<DocumentType?>()
    private val actions by lazy { DocumentActions(this, viewModel) }
    private val permissionRequester = StoragePermissionRequester(this) { viewModel.refresh() }

    override fun initialize() = Unit

    override fun backPressed() = finish()

    override fun ActivityAllFilesBinding.setData() {
        bar.tvTitle.setText(R.string.all_file)
        bar.btnFirst.isVisible = true
        bar.btnFirst.setImageResource(R.drawable.ic_fo_search)
        bar.btnFirst.contentDescription = getString(R.string.search_files)

        segmentTab.setUp(R.string.recent, R.string.fo_favourites, R.string.fo_all) { viewModel.setTab(StorageTab.entries[it]) }
        rcvTypes.adapter = typeChips
        typeChips.submitList(TYPES.map { (type, label) -> FilterChip(type, label, isSelected = type == null) })
        rcvDocuments.adapter = rows

        viewModel.filter.mainCollect { segmentTab.select(it.tab.ordinal) }
        viewModel.documents.mainCollect { response ->
            val files = (response as? DataResponse.DataSuccess)?.data
            if (files != null) rows.submitList(files)
            rcvDocuments.isVisible = !files.isNullOrEmpty()
            emptyList.root.isVisible = files?.isEmpty() == true
            if (response.isNoAccess) emptyState.showNoAccess() else emptyState.root.isVisible = false
        }
    }

    override fun ActivityAllFilesBinding.onClick() {
        bar.btnBack.clickSingle { finish() }
        bar.btnFirst.clickSingle { launchActivity<SearchActivity>() }
        btnSort.clickSingle {
            SortBottomSheet(this@AllFilesActivity, viewModel.filter.value.sort) { viewModel.setSort(it) }.show()
        }
        typeChips.onChipClick { type ->
            typeChips.select(type)
            viewModel.setScope(type, null)
        }
        emptyState.btnPrimary.clickSingle { permissionRequester.request(this@AllFilesActivity) }
        emptyList.tvTitle.setText(R.string.fo_nothing_here)
        emptyList.tvDesc.setText(R.string.fo_nothing_here_desc)
        rows
            .onDocumentClick { actions.open(it.document) }
            .onMoreClick { actions.showOptions(it) }
            .onDocumentLongClick {
                launchActivity<MultiSelectActivity>(
                    MultiSelectActivity.EXTRA_TAB to viewModel.filter.value.tab.name,
                    MultiSelectActivity.EXTRA_TYPE to viewModel.filter.value.type?.name,
                    MultiSelectActivity.EXTRA_SELECTED_ID to it.document.mediaId
                )
            }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private companion object {
        val TYPES = listOf(
            null to R.string.fo_all,
            DocumentType.Pdf to R.string.tab_pdf,
            DocumentType.Doc to R.string.tab_word,
            DocumentType.Excel to R.string.tab_excel,
            DocumentType.Ppt to R.string.powerpoint,
            DocumentType.Txt to R.string.fo_text
        )
    }
}
