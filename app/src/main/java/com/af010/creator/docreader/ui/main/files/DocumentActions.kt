package com.af010.creator.docreader.ui.main.files

import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.ui.dialog.DeleteDialog
import com.af010.creator.docreader.ui.dialog.RenameDialog
import com.af010.creator.docreader.ui.main.files.sheet.FileAction
import com.af010.creator.docreader.ui.main.files.sheet.FileInfoBottomSheet
import com.af010.creator.docreader.ui.main.files.sheet.FileOptionsBottomSheet
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.ui.main.reader.pdf.PdfReaderActivity
import com.af010.creator.docreader.ui.main.tools.openTool
import com.d010.pdf.reader.base.openFileWith
import com.d010.pdf.reader.base.shareFile
import com.d010.pdf.reader.base.toastShort

/** Open and the options sheet (share, favorite, rename, copy, delete) for one document; used by every list screen. */
class DocumentActions(
    private val activity: FragmentActivity,
    private val viewModel: DocumentsViewModel
) {
    /** The readers put the document in the recent files themselves, whichever screen opened them. */
    fun open(doc: RecentDocument) {
        when {
            doc.path.endsWith(".pdf", ignoreCase = true) -> PdfReaderActivity.start(activity, doc.path)
            OfficeActivity.canOpen(doc.path) -> OfficeActivity.start(activity, doc.path)
            // Images, archives: another app shows them.
            activity.openFileWith(doc.path) -> viewModel.markOpened(doc)
            else -> toast(R.string.file_open_with_failed)
        }
    }

    fun showOptions(item: RecentUi) {
        FileOptionsBottomSheet(activity, item) { action -> handle(item, action) }.show()
    }

    private fun handle(item: RecentUi, action: FileAction) {
        val doc = item.document
        when (action) {
            FileAction.Share -> if (!activity.shareFile(doc.path)) toast(R.string.file_share_failed)
            FileAction.Favorite -> viewModel.setFavorite(doc, !item.isFavorite)
            FileAction.Rename -> RenameDialog(activity, doc) { newName ->
                viewModel.rename(doc, newName) { renamed ->
                    if (!renamed) toast(R.string.file_rename_failed)
                }
            }.show()
            FileAction.Copy -> viewModel.copy(doc) { copied ->
                if (!copied) toast(R.string.file_copy_failed)
            }
            FileAction.Delete -> DeleteDialog(activity, 1) {
                viewModel.delete(listOf(doc)) { failed ->
                    if (failed > 0) toast(R.string.file_delete_failed)
                }
            }.show()
            FileAction.Info -> FileInfoBottomSheet(activity, doc).show()
        }
    }

    private fun toast(message: Int) = activity.toastShort(activity.getString(message))
}
