package com.af010.creator.docreader.ui.main.reader.office.sheet

import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetOfficeOptionsBinding
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.visibleOrGone

enum class OfficeOption { Slideshow, GoTo, ToPdf, Favorite, Info, OpenWith, Delete }

/**
 * The "more" menu of a document that is being viewed. [available] leaves out what the file
 * cannot do (a slideshow outside PowerPoint, the library actions for a file that is not in it).
 */
class OfficeOptionsBottomSheet(
    activity: FragmentActivity,
    private val fileName: String,
    private val available: Set<OfficeOption>,
    private val isFavorite: Boolean,
    @StringRes private val goToLabel: Int,
    private val onOption: (OfficeOption) -> Unit
) : BaseBottomSheet<SheetOfficeOptionsBinding>(activity, SheetOfficeOptionsBinding::inflate) {

    override fun SheetOfficeOptionsBinding.onBind() {
        header.tvTitle.text = fileName
        header.btnClose.click { dismissSafe() }
        tvGoTo.setText(goToLabel)
        if (isFavorite) ivFavorite.setImageResource(R.drawable.ic_fo_star_filled)

        mapOf(
            rowSlideshow to OfficeOption.Slideshow,
            rowGoTo to OfficeOption.GoTo,
            rowToPdf to OfficeOption.ToPdf,
            rowFavorite to OfficeOption.Favorite,
            rowInfo to OfficeOption.Info,
            rowOpenWith to OfficeOption.OpenWith,
            rowDelete to OfficeOption.Delete
        ).forEach { (row, option) ->
            row.visibleOrGone(option in available)
            row.click {
                dismissSafe()
                onOption(option)
            }
        }
    }
}
