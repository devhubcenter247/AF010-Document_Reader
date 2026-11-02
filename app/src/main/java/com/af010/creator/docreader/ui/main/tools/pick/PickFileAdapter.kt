package com.af010.creator.docreader.ui.main.tools.pick

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemPickFileBinding
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.ui.common.showTypeTile
import com.af010.creator.docreader.utils.extensions.dateText
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.sizeText
import com.ui.baselib.base.DiffAdapter

/** Files to pick from; with [numbered], a picked row shows its place in the order. */
class PickFileAdapter(private val numbered: Boolean) : DiffAdapter<RecentUi, ItemPickFileBinding>(
    areItemsTheSame = { old, new -> old.document.mediaId == new.document.mediaId },
    areContentsTheSame = { old, new -> old.sameContent(new) }
) {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemPickFileBinding.inflate(inflater, parent, false)

    private var onItemClick: ((RecentUi) -> Unit) = {}
    fun onFileClick(listener: (RecentUi) -> Unit) = apply {
        onItemClick = listener
    }

    /** Paths in the order they were picked. */
    var selectedPaths: List<String> = emptyList()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemPickFileBinding.bind(item: RecentUi, position: Int) {
        val context = root.context
        val doc = item.document
        val order = selectedPaths.indexOf(doc.path)
        tvType.showTypeTile(doc)
        tvName.text = doc.name
        tvMeta.text = context.getString(R.string.document_meta, doc.sizeText(context), doc.dateText(context))
        root.isSelected = order >= 0
        tvName.isSelected = true
        tvOrder.isVisible = numbered
        tvOrder.text = if (order >= 0) (order + 1).toString() else ""
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
