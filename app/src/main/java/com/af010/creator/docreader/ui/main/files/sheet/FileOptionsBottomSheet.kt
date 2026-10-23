package com.af010.creator.docreader.ui.main.files.sheet

import android.content.res.ColorStateList
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetFileOptionsBinding
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.extensionLabel
import com.af010.creator.docreader.utils.extensions.folderName
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.af010.creator.docreader.utils.extensions.sizeText
import com.af010.creator.docreader.utils.extensions.softColorRes
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.clickSingle

enum class FileAction { Share, Favorite, Rename, Copy, Info, Delete }

/** One document's actions: four quick tiles, then File info and Delete. */
class FileOptionsBottomSheet(
    private val host: FragmentActivity,
    private val item: RecentUi,
    private val onAction: (FileAction) -> Unit
) : BaseBottomSheet<SheetFileOptionsBinding>(host, SheetFileOptionsBinding::inflate) {

    override fun SheetFileOptionsBinding.onBind() {
        val doc = item.document
        tvType.text = doc.extensionLabel
        tvType.setTextColor(host.getColor(doc.type.colorRes))
        tvType.backgroundTintList = ColorStateList.valueOf(host.getColor(doc.type.softColorRes))
        tvName.text = doc.name
        tvName.isSelected = true
        tvMeta.text = host.getString(R.string.document_meta, doc.sizeText(host), doc.folderName)

        btnFavorite.isSelected = item.isFavorite
        if (item.isFavorite) btnFavorite.setCompoundDrawablesSized(com.intuit.sdp.R.dimen._17sdp, top = R.drawable.ic_fo_star_filled)

        mapOf(
            btnShare to FileAction.Share,
            btnFavorite to FileAction.Favorite,
            btnRename to FileAction.Rename,
            btnCopy to FileAction.Copy,
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
