package com.af010.creator.docreader.ui.main.tools.pick

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemPickFileBinding
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.utils.extensions.dateText
import com.af010.creator.docreader.utils.extensions.iconRes
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.sizeText
import com.ui.baselib.base.DiffAdapter

class PickFileAdapter : DiffAdapter<RecentUi, ItemPickFileBinding>(
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

    /** Paths in the order they were picked; a picked row shows its place in that order. */
    var selectedPaths: List<String> = emptyList()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemPickFileBinding.bind(item: RecentUi, position: Int) {
        val context = root.context
        val doc = item.document
        val order = selectedPaths.indexOf(doc.path)
        ivType.setImageResource(doc.type.iconRes)
        tvName.text = doc.name
        tvMeta.text = context.getString(R.string.document_meta, doc.sizeText(context), doc.dateText(context))

        root.setBackgroundColor(
            ContextCompat.getColor(
                context,
                if (order >= 0) R.color.color_bg_selected else android.R.color.transparent
            )
        )
        tvOrder.text = if (order >= 0) (order + 1).toString() else ""
        tvOrder.helper.bgColor = ContextCompat.getColor(
            context,
            if (order >= 0) R.color.primary else android.R.color.transparent
        )
        tvOrder.helper.stColor = ContextCompat.getColor(
            context,
            if (order >= 0) R.color.primary else R.color.color_checkbox_stroke
        )
        tvOrder.invalidate()
        root.setOnClickListener { onItemClick.invoke(item) }
    }
}
