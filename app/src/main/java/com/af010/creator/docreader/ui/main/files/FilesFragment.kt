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
import com.af010.creator.docreader.databinding.LayoutTypeCountBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.StorageTab
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.common.showToolTile
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.main.common.CardGroupDecoration
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.files.sheet.SortBottomSheet
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.af010.creator.docreader.ui.main.select.MultiSelectActivity
import com.af010.creator.docreader.ui.main.tools.openTool
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.af010.creator.docreader.utils.extensions.colorRes
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.clickSingle
import java.util.Calendar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The Files tab: the phone's documents. On top the file types with their counts (one is always picked,
 * PDF at first, and the list shows that type; they scroll away with it), then Recent / Favourites / All with the sort button, then the
 * files. Creating and the tools live in the "+" sheet and the Tools tab, not here. Without storage
 * access: a library that asks for access and still offers what works without.
 */
class FilesFragment : BaseAppFragment<FragmentFilesBinding>(FragmentFilesBinding::inflate) {

    private val viewModel: DocumentsViewModel by viewModel()
    private val rows = DocumentAdapter(RowEnd.Options)
    private val documentActions by lazy { DocumentActions(requireActivity(), viewModel) }

    private val permissionRequester = StoragePermissionRequester(this) { viewModel.refresh() }

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openPicked(uri)
    }

    override fun FragmentFilesBinding.initView() {
        tvGreeting.setText(greeting())
        tvAppName.isSelected = true // starts the marquee
        btnPro.isVisible = remoteConfig.paywallEnabled
        btnProPill.isVisible = remoteConfig.paywallEnabled
        TYPE_TILES.forEach { tile -> typeTile(tile.type).badge(tile) }
        // The list always shows one type; PDF until another is picked (kept across view re-creation).
        if (viewModel.filter.value.type == null) viewModel.setScope(DEFAULT_TYPE, null)

        segmentTab.setUp(R.string.recent, R.string.fo_favourites, R.string.fo_all) { viewModel.setTab(StorageTab.entries[it]) }
        rcvDocuments.adapter = rows
        rcvDocuments.addItemDecoration(CardGroupDecoration())


        quickScan.quick(Tool.Scan, R.string.scan)
        quickPhotos.quick(Tool.ImagesToPdf, R.string.fo_photos)
        quickNew.quick(R.drawable.ic_fo_plus, R.color.primary, R.string.new_file)

        viewModel.filter.mainCollect { filter ->
            segmentTab.select(filter.tab.ordinal)
            // What an empty list means depends on the tab.
            val (title, desc) = when (filter.tab) {
                StorageTab.Recent -> R.string.fo_nothing_recent to R.string.fo_nothing_recent_desc
                StorageTab.Favorites -> R.string.fo_nothing_favourite to R.string.fo_nothing_favourite_desc
                StorageTab.All -> R.string.fo_nothing_here to R.string.fo_nothing_here_desc
            }
            emptyList.tvTitle.setText(title)
            emptyList.tvDesc.setText(desc)
            TYPE_TILES.forEach { tile -> typeTile(tile.type).root.isSelected = tile.type == filter.type }
        }
        viewModel.documents.mainCollect { show(it) }
        viewModel.typeCounts.mainCollect { showCounts(it) }
    }

    override fun FragmentFilesBinding.onClick() {
        btnSearch.clickSingle { launchActivity<SearchActivity>() }
        btnPro.clickSingle { PaywallActivity.start(requireActivity()) }
        btnProPill.clickSingle { PaywallActivity.start(requireActivity()) }
        btnSort.clickSingle {
            SortBottomSheet(requireActivity(), viewModel.filter.value.sort, showType = false) { viewModel.setSort(it) }.show()
        }
        TYPE_TILES.forEach { tile -> typeTile(tile.type).root.clickSingle { viewModel.setScope(tile.type, null) } }
        rows
            .onDocumentClick { documentActions.open(it.document) }
            .onDocumentLongClick { openMultiSelect(it.document.mediaId) }
            .onMoreClick { documentActions.showOptions(it) }

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
        screenNoAccess.isVisible = noAccess
        layoutNoAccess.isVisible = noAccess
        screenFiles.isVisible = !noAccess
        if (response !is DataResponse.DataSuccess) return
        val files = response.data
        rows.submitList(files)
        rcvDocuments.isVisible = files.isNotEmpty()
        scrollEmpty.isVisible = files.isEmpty()
    }

    /** Every document on the phone, counted per type. */
    private fun FragmentFilesBinding.showCounts(counts: Map<DocumentType, Int>) {
        TYPE_TILES.forEach { tile ->
            val count = counts[tile.type] ?: 0
            typeTile(tile.type).tvCount.text = resources.getQuantityString(R.plurals.file_count, count, count)
        }
    }

    private fun FragmentFilesBinding.typeTile(type: DocumentType): LayoutTypeCountBinding = when (type) {
        DocumentType.Pdf -> typePdf
        DocumentType.Excel -> typeExcel
        DocumentType.Ppt -> typePpt
        else -> typeWord
    }

    private fun LayoutTypeCountBinding.badge(tile: TypeTile) {
        tvBadge.text = tile.letter
        tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(root.context, tile.type.colorRes))
        tvName.setText(tile.name)
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

    private fun openMultiSelect(selectedId: Long) {
        val filter = viewModel.filter.value
        launchActivity<MultiSelectActivity>(
            MultiSelectActivity.EXTRA_TAB to filter.tab.name,
            MultiSelectActivity.EXTRA_TYPE to filter.type?.name,
            MultiSelectActivity.EXTRA_SELECTED_ID to selectedId
        )
    }

    companion object {
        private val DEFAULT_TYPE = DocumentType.Pdf

        /** The file types on top of the tab: the letter on the page icon and the name under it. */
        private val TYPE_TILES = listOf(
            TypeTile(DocumentType.Pdf, "PDF", R.string.tab_pdf),
            TypeTile(DocumentType.Doc, "W", R.string.tab_word),
            TypeTile(DocumentType.Excel, "X", R.string.tab_excel),
            TypeTile(DocumentType.Ppt, "P", R.string.fo_slides)
        )

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

private class TypeTile(val type: DocumentType, val letter: String, @param:StringRes val name: Int)
