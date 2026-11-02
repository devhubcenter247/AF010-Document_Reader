package com.af010.creator.docreader.ui.main.tools.office

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.databinding.ItemPdfPageBinding
import com.af010.creator.docreader.ui.main.reader.office.OfficePdf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The pages of [preview], one per screen of the pager. Each is drawn [width] px wide when first
 * shown, one at a time (the SDK draws on one thread), and fills its slot keeping its shape; until
 * then it takes the usual shape of the document's pages.
 */
class PdfPageAdapter(
    private val preview: OfficePdf.Preview,
    private val scope: CoroutineScope,
    private val width: Int
) : RecyclerView.Adapter<PdfPageAdapter.Holder>() {

    class Holder(val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
        /** The page this holder shows and its picture once drawn. */
        var page = RecyclerView.NO_POSITION
        var picture: Bitmap? = null
    }

    private val pictures = LruCache<Int, Bitmap>(KEPT)
    private val drawing = Mutex()

    override fun getItemCount() = preview.count

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.job?.cancel()
        holder.page = position
        holder.picture = pictures.get(position)
        holder.show()
        if (holder.picture != null) return
        holder.job = scope.launch {
            val picture = drawing.withLock { runCatching { preview.draw(position, width) }.getOrNull() } ?: return@launch
            pictures.put(position, picture)
            if (holder.page != position) return@launch
            holder.picture = picture
            holder.show()
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.job?.cancel()
        holder.page = RecyclerView.NO_POSITION
        holder.picture = null
        holder.binding.ivPage.setImageDrawable(null)
    }

    private fun Holder.show(): Unit = with(binding) {
        val picture = picture
        ivPage.setImageBitmap(picture)
        progress.isVisible = picture == null
        // Sized once the slot is known; by then the picture may have come, so it reads it again.
        if (root.width == 0) return root.doOnLayout { show() }
        val ratio = if (picture != null) picture.width.toFloat() / picture.height
        else if (preview.landscape) LANDSCAPE else PORTRAIT
        // As large as the slot allows, keeping the page's shape.
        val slotW = root.width - root.paddingLeft - root.paddingRight
        val slotH = root.height - root.paddingTop - root.paddingBottom
        val (w, h) = if (slotW / ratio <= slotH) slotW to (slotW / ratio).toInt() else (slotH * ratio).toInt() to slotH
        if (ivPage.layoutParams.width != w || ivPage.layoutParams.height != h) {
            ivPage.updateLayoutParams<ViewGroup.LayoutParams> {
                this.width = w
                this.height = h
            }
        }
    }

    private companion object {
        const val KEPT = 6
        const val PORTRAIT = 210f / 297f
        const val LANDSCAPE = 297f / 210f
    }
}
