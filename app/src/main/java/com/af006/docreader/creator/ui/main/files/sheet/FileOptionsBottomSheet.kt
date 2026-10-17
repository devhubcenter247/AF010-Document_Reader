package com.af010.creator.docreader.ui.main.files.sheet

import androidx.core.widget.TextViewCompat
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetFileOptionsBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.utils.extensions.dateText
import com.af010.creator.docreader.utils.extensions.iconRes
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.af010.creator.docreader.utils.extensions.sizeText
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone

enum class FileAction {
    Share, Favorite, ToPdf, Edit, Rename, Copy, Info, Delete
}

class FileOptionsBottomSheet(
    activity: FragmentActivity,
    private val item: RecentUi,
    private val onAction: (FileAction) -> Unit
) : BaseBottomSheet<SheetFileOptionsBinding>(activity, SheetFileOptionsBinding::inflate) {

    override fun SheetFileOptionsBinding.onBind() {
        val context = root.context
        val doc = item.document
        ivType.setImageResource(doc.type.iconRes)
        tvName.text = doc.name
        tvMeta.text = context.getString(
            R.string.file_meta_edited, doc.sizeText(context), doc.dateText(context)
        )
        if (item.isFavorite) {
            // The layout's outline star is tinted; the filled one carries its own color.
            TextViewCompat.setCompoundDrawableTintList(btnFavorite, null)
            btnFavorite.setCompoundDrawablesSized(com.intuit.sdp.R.dimen._17sdp, top = R.drawable.ic_app_star_filled)
        }
        // Word and PowerPoint only: the SDK cannot draw a sheet as a page.
        btnToPdf.visibleOrGone(doc.type == DocumentType.Doc || doc.type == DocumentType.Ppt)

        mapOf(
            btnShare to FileAction.Share,
            btnFavorite to FileAction.Favorite,
            btnToPdf to FileAction.ToPdf,
            rowEdit to FileAction.Edit,
            rowRename to FileAction.Rename,
            rowCopy to FileAction.Copy,
            rowInfo to FileAction.Info,
            rowDelete to FileAction.Delete
        ).forEach { (view, action) ->
            view.clickSingle {
                dismissSafe()
                onAction(action)
            }
        }
    }
}
