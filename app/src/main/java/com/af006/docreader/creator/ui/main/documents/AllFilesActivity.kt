package com.af010.creator.docreader.ui.main.documents

import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityAllFilesBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.ui.main.files.BrowseType
import com.af010.creator.docreader.ui.main.files.BrowseTypeAdapter
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Browse every document on the device by type or by folder. */
class AllFilesActivity : BaseAppActivity<ActivityAllFilesBinding>(ActivityAllFilesBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val viewModel: DocumentsViewModel by viewModel()
    private val typeAdapter by lazy { BrowseTypeAdapter() }
    private val folderAdapter by lazy { FolderAdapter() }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityAllFilesBinding.setData() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.setText(R.string.all_file)
        toolbar.btnSearch.visible()
        rcvTypes.adapter = typeAdapter
        rcvFolders.adapter = folderAdapter

        viewModel.typeCounts.mainCollect { counts ->
            typeAdapter.submitList(types.map { it.copy(count = counts[it.type] ?: 0) })
            btnTxt.text = getString(
                R.string.document_meta,
                getString(R.string.tab_txt),
                (counts[DocumentType.Txt] ?: 0).toString()
            )
        }
        viewModel.folders.mainCollect { folders ->
            folderAdapter.submitList(folders)
            tvFolders.visibleOrGone(folders.isNotEmpty())
            layoutFolders.visibleOrGone(folders.isNotEmpty())
        }
    }

    override fun ActivityAllFilesBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        toolbar.btnSearch.clickSingle { launchActivity<SearchActivity>() }
        typeAdapter.onTypeClick {
            launchActivity<FileListActivity>(FileListActivity.EXTRA_TYPE to it.type.name)
        }
        folderAdapter.onFolderClick {
            launchActivity<FileListActivity>(FileListActivity.EXTRA_FOLDER to it)
        }
        btnTxt.clickSingle {
            launchActivity<FileListActivity>(FileListActivity.EXTRA_TYPE to DocumentType.Txt.name)
        }
    }

    companion object {
        private val types = listOf(
            BrowseType(DocumentType.Pdf, R.string.tab_pdf),
            BrowseType(DocumentType.Doc, R.string.tab_word),
            BrowseType(DocumentType.Excel, R.string.tab_excel),
            BrowseType(DocumentType.Ppt, R.string.powerpoint)
        )
    }
}
