package com.af010.creator.docreader.ui.main.reader.pdf.organize

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.databinding.ActivityPdfOrganizeBinding
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PageEdit
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.dialog.MessageDialog
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visible
import org.koin.android.ext.android.inject
import java.io.File

/**
 * The pages of a PDF as a grid: tap to select, hold and drag to reorder, then rotate, duplicate
 * or delete the selection, or add a blank page. Save writes the result over the file.
 */
class PdfOrganizeActivity : BaseAppActivity<ActivityPdfOrganizeBinding>(ActivityPdfOrganizeBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val toolbox: PdfToolbox by inject()
    private val repository: DocumentRepository by inject()
    private val source: File by lazy { File(intent.getStringExtra(EXTRA_PATH).orEmpty()) }
    private val password: String? by lazy { intent.getStringExtra(EXTRA_PASSWORD) }

    private val adapter by lazy { OrganizePageAdapter(::loadThumbnail) }
    private val thumbnails = HashMap<Int, Bitmap>()
    private var selected: Set<Long> = emptySet()
    private var nextId = 0L
    private var changed = false
    private var saving = false

    override fun backPressed() {
        if (saving) return
        if (!changed) return finish()
        MessageDialog(
            this, R.string.discard_changes_title, R.string.discard_changes_message,
            R.string.discard, R.string.file_action_cancel
        ) { finish() }.show()
    }

    override fun initialize() {

    }

    override fun ActivityPdfOrganizeBinding.setData() {
        topBar.updatePadding(top = statusBarHeight)
        rcvPages.adapter = adapter
        ItemTouchHelper(dragCallback).attachToRecyclerView(rcvPages)
        render()

        launchMain {
            val count = runCatching { toolbox.pageCount(source, password) }.getOrDefault(0)
            progress.gone()
            if (count <= 0) {
                toast(R.string.error_file_open_generic)
                finish()
                return@launchMain
            }
            adapter.submitList((0 until count).map { OrganizePage(nextId++, it) })
        }
    }

    override fun ActivityPdfOrganizeBinding.onClick() {
        btnBack.clickSingle { backPressed() }
        btnSave.clickSingle { save() }
        adapter.onPageClick { page ->
            selected = if (page.id in selected) selected - page.id else selected + page.id
            adapter.selectedIds = selected
            render()
        }

        btnRotate.click {
            withSelection {
                adapter.replaceAll { page ->
                    if (page.id in selected) page.copy(quarterTurns = (page.quarterTurns + 1) % 4) else page
                }
                changed()
            }
        }
        btnDuplicate.click {
            withSelection {
                val pages = adapter.getListItem().flatMap { page ->
                    if (page.id in selected) listOf(page, page.copy(id = nextId++)) else listOf(page)
                }
                adapter.submitList(pages)
                changed()
            }
        }
        btnAddPage.click {
            // After the last selected page, or at the end.
            val pages = adapter.getListItem().toMutableList()
            val index = pages.indexOfLast { it.id in selected }
            pages.add(if (index < 0) pages.size else index + 1, OrganizePage(nextId++, BLANK))
            adapter.submitList(pages)
            changed()
        }
        btnToImages.clickSingle {
            withSelection {
                val pages = adapter.getListItem().filter { it.id in selected && it.source >= 0 }.map { it.source }
                // The pictures come from the file as it is saved, not from the changes made here.
                if (pages.isNotEmpty() && password == null) {
                    ProcessingActivity.start(this@PdfOrganizeActivity, ToolRequest.PdfToImages(source.path, pages))
                } else {
                    toast(R.string.error_file_open_generic)
                }
            }
        }
        btnDelete.click {
            withSelection {
                if (selected.size >= adapter.itemCount) {
                    toast(R.string.organize_keep_one)
                    return@withSelection
                }
                adapter.submitList(adapter.getListItem().filter { it.id !in selected })
                selected = emptySet()
                adapter.selectedIds = selected
                changed()
            }
        }
    }

    private fun withSelection(action: () -> Unit) {
        if (selected.isEmpty()) toast(R.string.organize_select_first) else action()
    }

    private fun changed() {
        changed = true
        // Page numbers follow the new order.
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        binding.render()
    }

    private fun ActivityPdfOrganizeBinding.render() {
        tvSubtitle.text = if (selected.isEmpty()) getString(R.string.organize_drag)
        else getString(R.string.organize_selected, selected.size)
    }

    private fun save() {
        if (saving) return
        if (!changed) return finish()
        val pages = adapter.getListItem().map { PageEdit(it.source, it.quarterTurns) }
        if (pages.isEmpty()) return
        saving = true
        binding.progress.visible()
        launchMain {
            val temp = File(cacheDir, "organize-${System.currentTimeMillis()}.pdf")
            val saved = runCatching {
                toolbox.organize(source, password, pages, temp)
                temp.copyTo(source, overwrite = true)
            }.isSuccess
            temp.delete()
            saving = false
            binding.progress.gone()
            if (!saved) {
                toast(R.string.save_failed)
                return@launchMain
            }
            MediaScannerConnection.scanFile(applicationContext, arrayOf(source.absolutePath), null, null)
            repository.refresh()
            toast(R.string.saved_changes)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun loadThumbnail(page: Int, onReady: (Bitmap) -> Unit) {
        thumbnails[page]?.let { return onReady(it) }
        launchMain {
            val bitmap = runCatching { toolbox.renderPage(source, page, THUMBNAIL_WIDTH, password) }.getOrNull()
                ?: return@launchMain
            thumbnails[page] = bitmap
            onReady(bitmap)
        }
    }

    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            changed = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Page numbers follow the new order.
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    companion object {
        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_PASSWORD = "extra_password"
        private const val THUMBNAIL_WIDTH = 240
        private const val BLANK = -1

        fun intent(context: Context, path: String, password: String? = null): Intent =
            Intent(context, PdfOrganizeActivity::class.java)
                .putExtra(EXTRA_PATH, path)
                .putExtra(EXTRA_PASSWORD, password)
    }
}
