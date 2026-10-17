package com.af010.creator.docreader.ui.main.files

import android.content.res.ColorStateList
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.base.BaseAppFragment
import com.af010.creator.docreader.databinding.FragmentFilesBinding
import com.af010.creator.docreader.databinding.LayoutQuickTileBinding
import com.af010.creator.docreader.databinding.LayoutStartTileBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.StorageTab
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.common.showToolTile
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.documents.AllFilesActivity
import com.af010.creator.docreader.ui.main.newdocument.NewDocumentActivity
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.af010.creator.docreader.ui.main.select.MultiSelectActivity
import com.af010.creator.docreader.ui.main.tools.openTool
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.clickSingle
import java.util.Calendar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The Files tab. With file access: start something new, the edit card and the files opened
 * lately. Without it: a library that asks for access and still offers what works without.
 */
class FilesFragment : BaseAppFragment<FragmentFilesBinding>(FragmentFilesBinding::inflate) {

    private val viewModel: DocumentsViewModel by viewModel()
    private val recent = DocumentAdapter(RowEnd.Open)
    private val documentActions by lazy { DocumentActions(requireActivity(), viewModel) }

    private val permissionRequester = StoragePermissionRequester(this) { viewModel.refresh() }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openPicked(uri)
    }

    override fun FragmentFilesBinding.initView() {
        tvGreeting.setText(greeting())
        btnPro.isVisible = remoteConfig.paywallEnabled
        btnProPill.isVisible = remoteConfig.paywallEnabled

        tileWord.blank(R.string.fo_document, R.string.tab_word, R.color.color_file_word, tilt = -3f)
        tileExcel.blank(R.string.fo_sheet, R.string.tab_excel, R.color.color_file_excel, tilt = 2f)
        tilePpt.blank(R.string.fo_slides, R.string.powerpoint, R.color.color_file_powerpoint, tilt = -2f)

        quickScan.quick(Tool.Scan, R.string.scan)
        quickPhotos.quick(Tool.ImagesToPdf, R.string.fo_photos)
        quickNew.quick(R.drawable.ic_fo_plus, R.color.primary, R.string.new_file)

        emptyRecent.tvTitle.setText(R.string.fo_nothing_recent)
        emptyRecent.tvDesc.setText(R.string.fo_nothing_recent_desc)

        rcvDocuments.adapter = recent
        // This tab only lists what was opened in the app; All files has the rest.
        if (viewModel.filter.value.tab != StorageTab.Recent) viewModel.setTab(StorageTab.Recent)
        viewModel.documents.mainCollect { show(it) }
    }

    override fun FragmentFilesBinding.onClick() {
        btnSearch.clickSingle { launchActivity<SearchActivity>() }
        btnPro.clickSingle { PaywallActivity.start(requireActivity()) }
        btnProPill.clickSingle { PaywallActivity.start(requireActivity()) }

        tileWord.root.clickSingle { newDocument(DocumentType.Doc) }
        tileExcel.root.clickSingle { newDocument(DocumentType.Excel) }
        tilePpt.root.clickSingle { newDocument(DocumentType.Ppt) }
        btnScan.clickSingle { requireActivity().openTool(Tool.Scan) }
        btnPhotos.clickSingle { requireActivity().openTool(Tool.ImagesToPdf) }

        cardEdit.clickSingle { launchActivity<AllFilesActivity>() }
        chipAddText.clickSingle { requireActivity().openTool(Tool.AddText) }
        chipSign.clickSingle { requireActivity().openTool(Tool.Sign) }
        chipAnnotate.clickSingle { requireActivity().openTool(Tool.Annotate) }
        chipFillForms.clickSingle { requireActivity().openTool(Tool.FillForm) }
        chipOrganize.clickSingle { requireActivity().openTool(Tool.Organize) }

        btnAllFiles.clickSingle { launchActivity<AllFilesActivity>() }
        recent
            .onDocumentClick { documentActions.open(it.document) }
            .onDocumentLongClick { openMultiSelect(it.document.mediaId) }

        btnAllowAccess.clickSingle { permissionRequester.request(requireActivity()) }
        // Works without storage access: the system picker hands the file over.
        btnOpenFile.clickSingle { filePicker.launch(OPENABLE_TYPES) }
        quickScan.root.clickSingle { requireActivity().openTool(Tool.Scan) }
        quickPhotos.root.clickSingle { requireActivity().openTool(Tool.ImagesToPdf) }
        quickNew.root.clickSingle { (activity as? MainActivity)?.showCreateSheet() }
    }

    override fun onResume() {
        super.onResume()
        // Access may have been granted or revoked in system Settings while the app was away.
        viewModel.refresh()
    }

    private fun FragmentFilesBinding.show(response: DocumentsResponse) {
        val noAccess = response.isNoAccess
        layoutNoAccess.isVisible = noAccess
        layoutHome.isVisible = !noAccess
        if (response !is DataResponse.DataSuccess) return
        val files = response.data.take(RECENT_LIMIT)
        recent.submitList(files)
        rcvDocuments.isVisible = files.isNotEmpty()
        emptyRecent.root.isVisible = files.isEmpty()
    }

    private fun LayoutStartTileBinding.blank(@StringRes title: Int, @StringRes app: Int, @ColorRes color: Int, tilt: Float) {
        val tint = ColorStateList.valueOf(ContextCompat.getColor(root.context, color))
        root.backgroundTintList = tint
        lineAccent.backgroundTintList = tint
        ivPlus.backgroundTintList = tint
        page.rotation = tilt
        tvTitle.setText(title)
        tvApp.setText(app)
    }

    private fun LayoutQuickTileBinding.quick(tool: Tool, @StringRes label: Int) {
        ivIcon.showToolTile(tool)
        tvLabel.setText(label)
    }

    private fun LayoutQuickTileBinding.quick(@DrawableRes glyph: Int, @ColorRes color: Int, @StringRes label: Int) {
        ivIcon.setImageResource(glyph)
        ivIcon.setBackgroundResource(R.drawable.bg_tool_glyph)
        ivIcon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(root.context, color))
        ivIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(root.context, R.color.on_primary))
        tvLabel.setText(label)
    }

    @StringRes
    private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> R.string.fo_good_morning
        in 12..17 -> R.string.fo_good_afternoon
        else -> R.string.fo_good_evening
    }

    private fun newDocument(type: DocumentType) =
        launchActivity<NewDocumentActivity>(NewDocumentActivity.EXTRA_TYPE to type.name)

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

    private fun openMultiSelect(selectedId: Long? = null) {
        launchActivity<MultiSelectActivity>(
            MultiSelectActivity.EXTRA_TAB to viewModel.filter.value.tab.name,
            MultiSelectActivity.EXTRA_SELECTED_ID to selectedId
        )
    }

    companion object {
        /** How many recently opened files the tab lists before "All files". */
        private const val RECENT_LIMIT = 8

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
    }
}
