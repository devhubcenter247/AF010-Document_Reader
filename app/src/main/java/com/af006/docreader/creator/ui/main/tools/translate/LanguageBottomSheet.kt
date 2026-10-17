package com.af010.creator.docreader.ui.main.tools.translate

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemLanguageOptionBinding
import com.af010.creator.docreader.databinding.SheetLanguageBinding
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click

/** Single-choice list of languages; [languages] are tag to display name. */
class LanguageBottomSheet(
    activity: FragmentActivity,
    @StringRes private val title: Int,
    private val languages: List<Pair<String, String>>,
    private val selected: String?,
    private val onSelected: (tag: String) -> Unit
) : BaseBottomSheet<SheetLanguageBinding>(activity, SheetLanguageBinding::inflate) {

    override fun SheetLanguageBinding.onBind() {
        header.tvTitle.setText(title)
        header.btnClose.click { dismissSafe() }
        rcvLanguages.adapter = LanguageOptionAdapter(selected) { tag ->
            dismissSafe()
            onSelected(tag)
        }.submitList(languages)
        rcvLanguages.scrollToPosition(languages.indexOfFirst { it.first == selected }.coerceAtLeast(0))
    }

    private class LanguageOptionAdapter(
        private val selected: String?,
        private val onItemClick: (String) -> Unit
    ) : BaseAdapter<Pair<String, String>, ItemLanguageOptionBinding>() {
        override fun createBinding(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ) = ItemLanguageOptionBinding.inflate(inflater, parent, false)

        override fun ItemLanguageOptionBinding.bind(item: Pair<String, String>, position: Int) {
            tvLanguage.text = item.second
            tvLanguage.setCompoundDrawablesSized(
                com.intuit.sdp.R.dimen._17sdp,
                start = if (item.first == selected) R.drawable.ic_app_radio_on else R.drawable.ic_app_radio_off
            )
            root.setOnClickListener { onItemClick.invoke(item.first) }
        }
    }
}
