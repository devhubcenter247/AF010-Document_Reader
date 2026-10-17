package com.af010.creator.docreader.ui.main.files

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentFilesBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.StorageTab
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.UseCase
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.common.showNoAccess
import com.af010.creator.docreader.ui.main.documents.AllFilesActivity
import com.af010.creator.docreader.ui.main.documents.FileListActivity
import com.af010.creator.docreader.ui.main.files.sheet.CreateBottomSheet
import com.af010.creator.docreader.ui.main.files.sheet.SortBottomSheet
import com.af010.creator.docreader.ui.main.newdocument.NewDocumentActivity
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.af010.creator.docreader.ui.main.select.MultiSelectActivity
import com.af010.creator.docreader.ui.main.tools.openTool
import com.af010.creator.docreader.utils.PreferenceHelper
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.d010.pdf.reader.viewmodel.DataResponse
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visibleOrGone
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FilesFragment : BaseAppFragment<FragmentFilesBinding>(FragmentFilesBinding::inflate) {

    private val viewModel: DocumentsViewModel by viewModel()
    private val preferenceHelper: PreferenceHelper by inject()

    private val toolChipAdapter by lazy { ToolChipAdapter() }
    private val browseAdapter by lazy { BrowseTypeAdapter() }
    private val documentAdapter by lazy { DocumentAdapter() }
    private val documentActions by lazy { DocumentActions(requireActivity(), viewModel) }

    private val permissionRequester = StoragePermissionRequester(this) {
        viewModel.refresh()
    }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openPicked(uri)
    }

    override fun FragmentFilesBinding.initView() {
        btnPro.visibleOrGone(remoteConfig.paywallEnabled)
        rcvToolChips.adapter = toolChipAdapter
        rcvBrowse.adapter = browseAdapter
        rcvDocuments.adapter = documentAdapter
        rcvDocuments.setHasFixedSize(true)

        toolChipAdapter.submitList(UseCase.suggestedTools(preferenceHelper.useCases))
        // The other lists show every file; this tab starts on the ones opened in the app.
        if (viewModel.filter.value.tab == StorageTab.All) viewModel.setTab(StorageTab.Recent)
        segmentTab.setUp(R.string.recent, R.string.favorites) { index ->
            viewModel.setTab(StorageTab.entries[index])
        }

        viewModel.filter.mainCollect { segmentTab.select(it.tab.ordinal) }
        viewModel.typeCounts.mainCollect { counts ->
            browseAdapter.submitList(browseTypes.map { it.copy(count = counts[it.type] ?: 0) })
        }
        viewModel.documents.mainCollect { render(it) }
    }

    override fun FragmentFilesBinding.onClick() {
        btnPro.clickSingle { PaywallActivity.start(requireActivity()) }
        btnSearch.clickSingle { launchActivity<SearchActivity>() }

        cardScan.clickSingle { requireActivity().openTool(Tool.Scan) }
        cardPhotos.clickSingle { requireActivity().openTool(Tool.ImagesToPdf) }
        cardNewFile.clickSingle { showCreateSheet() }
        btnCreate.clickSingle { showCreateSheet() }
        toolChipAdapter.onToolClick { requireActivity().openTool(it) }
        btnAllTools.clickSingle {
            (appActivity as? MainActivity)?.selectTab(MainActivity.ROUTE_TOOLS)
        }

        layoutNoAccess.btnPrimary.clickSingle { permissionRequester.request(requireActivity()) }
        // Works without storage access: the system picker hands the file over.
        layoutNoAccess.btnSecondary.clickSingle { filePicker.launch(OPENABLE_TYPES) }

        btnAllFiles.clickSingle { launchActivity<AllFilesActivity>() }
        browseAdapter.onTypeClick {
            launchActivity<FileListActivity>(FileListActivity.EXTRA_TYPE to it.type.name)
        }

        btnSort.clickSingle {
            SortBottomSheet(requireActivity(), viewModel.filter.value.sort) { viewModel.setSort(it) }.show()
        }
        btnSelect.clickSingle { openMultiSelect() }
        documentAdapter
            .onDocumentClick { documentActions.open(it.document) }
            .onMoreClick { documentActions.showOptions(it) }
            .onDocumentLongClick { openMultiSelect(it.document.mediaId) }
    }

    override fun onResume() {
        super.onResume()
        // Access may have been granted or revoked in system Settings while the app was away.
        viewModel.refresh()
    }

    private fun FragmentFilesBinding.render(response: DocumentsResponse) {
        val noAccess = response.isNoAccess
        if (noAccess) layoutNoAccess.showNoAccess(secondary = R.string.open_a_file)
        else layoutNoAccess.root.gone()
        layoutBrowse.visibleOrGone(!noAccess)
        layoutDocuments.visibleOrGone(!noAccess)
        if (response !is DataResponse.DataSuccess) return

        val atTop = !rcvDocuments.canScrollVertically(-1)
        // A file just opened moves to the top: keep it in sight instead of the row that was first.
        documentAdapter.submitList(response.data) { if (atTop) rcvDocuments.scrollToPosition(0) }
        tvEmpty.visibleOrGone(response.data.isEmpty())
        tvEmpty.setText(
            if (viewModel.filter.value.tab == StorageTab.Favorites) R.string.no_favorites_yet
            else R.string.no_files_yet
        )
    }

    private fun openPicked(uri: Uri) {
        val context = appContext()
        lifecycleScope.launch {
            val file = OpenedFiles.resolve(context, uri)
            val activity = activity ?: return@launch
            when {
                file == null -> toast(R.string.error_file_open_generic)
                !OpenedFiles.open(activity, file) -> toast(R.string.file_open_with_failed)
            }
        }
    }

    private fun showCreateSheet() {
        CreateBottomSheet(
            requireActivity(),
            onTool = { requireActivity().openTool(it) },
            onNewDocument = {
                launchActivity<NewDocumentActivity>(NewDocumentActivity.EXTRA_TYPE to it.name)
            }
        ).show()
    }

    private fun openMultiSelect(selectedId: Long? = null) {
        launchActivity<MultiSelectActivity>(
            MultiSelectActivity.EXTRA_TAB to viewModel.filter.value.tab.name,
            MultiSelectActivity.EXTRA_SELECTED_ID to selectedId
        )
    }

    companion object {
        /** PDF, Word, Excel, PowerPoint (old and new formats) and plain text. */
        private val OPENABLE_TYPES = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroEnabled.12",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
        )

        private val browseTypes = listOf(
            BrowseType(DocumentType.Pdf, R.string.tab_pdf),
            BrowseType(DocumentType.Doc, R.string.tab_word),
            BrowseType(DocumentType.Excel, R.string.tab_excel),
            BrowseType(DocumentType.Ppt, R.string.slides)
        )
    }
}
