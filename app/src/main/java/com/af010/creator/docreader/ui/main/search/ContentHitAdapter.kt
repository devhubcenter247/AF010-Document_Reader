package com.af010.creator.docreader.ui.main.search

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemContentHitBinding
import com.af010.creator.docreader.pdf.ContentHit
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.name
import com.ui.baselib.base.BaseAdapter

/** Matches inside documents: the file and the page (or line), then the words around the match. */
class ContentHitAdapter(private val onHit: (ContentHit) -> Unit) : BaseAdapter<ContentHit, ItemContentHitBinding>() {

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int) =
        ItemContentHitBinding.inflate(inflater, parent, false)

    override fun ItemContentHitBinding.bind(item: ContentHit, position: Int) {
        val context = root.context
        dot.backgroundTintList = ColorStateList.valueOf(context.getColor(item.document.type.colorRes))
        tvName.text = item.document.name
        tvName.isSelected = true
        tvPlace.text = context.getString(if (item.inPdf) R.string.fo_page_n else R.string.fo_line_n, item.place)
        tvSnippet.text = SpannableStringBuilder("…").append(item.before).apply {
            val start = length
            append(item.match)
            listOf(
                BackgroundColorSpan(context.getColor(R.color.color_pro)),
                ForegroundColorSpan(context.getColor(R.color.color_ink)),
                StyleSpan(Typeface.BOLD)
            ).forEach { setSpan(it, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            append(item.after).append("…")
        }
        root.setOnClickListener { onHit(item) }
    }
}
