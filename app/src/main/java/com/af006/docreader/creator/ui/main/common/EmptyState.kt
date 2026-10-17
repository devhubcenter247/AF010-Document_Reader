package com.af010.creator.docreader.ui.main.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.LayoutEmptyStateBinding
import com.af010.creator.docreader.database.DocumentRepositoryImpl
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone

/** True when the list could not be read because file access has not been granted. */
val DataResponse<*>.isNoAccess: Boolean
    get() = this is DataResponse.DataError && message == DocumentRepositoryImpl.PERMISSION_DENIED

/**
 * Shows `layout_empty_state.xml` with these texts. A button without a text (0) is hidden; the
 * screen sets the click listeners itself.
 */
fun LayoutEmptyStateBinding.show(
    title: CharSequence,
    @StringRes message: Int,
    @StringRes primary: Int = 0,
    @StringRes secondary: Int = 0,
    @DrawableRes icon: Int = R.drawable.ic_app_folder
) {
    ivIcon.setImageResource(icon)
    tvTitle.text = title
    tvMessage.setText(message)
    layoutButtons.visibleOrGone(primary != 0)
    if (primary != 0) btnPrimary.setText(primary)
    btnSecondary.visibleOrGone(secondary != 0)
    if (secondary != 0) btnSecondary.setText(secondary)
    root.visible()
}

/** The "allow access" card every document list shows without file access. */
fun LayoutEmptyStateBinding.showNoAccess(
    @StringRes message: Int = R.string.no_access_desc,
    @StringRes secondary: Int = 0
) = show(
    root.context.getString(R.string.no_access_title), message, R.string.allow_access, secondary
)
