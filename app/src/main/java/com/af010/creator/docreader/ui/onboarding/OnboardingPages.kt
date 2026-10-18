package com.af010.creator.docreader.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemOnboardingToolBinding
import com.af010.creator.docreader.databinding.LayoutDocCardBinding
import com.af010.creator.docreader.utils.extensions.setFillColor

/** What one onboarding slide says, and the picture drawn above it. */
enum class OnboardingSlide(
    @param:StringRes val kicker: Int,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    val picture: Int
) {
    Open(R.string.fo_onboarding_kicker_1, R.string.fo_onboarding_title_1, R.string.fo_onboarding_body_1, R.layout.page_onboarding_open),
    Sign(R.string.fo_onboarding_kicker_2, R.string.fo_onboarding_title_2, R.string.fo_onboarding_body_2, R.layout.page_onboarding_sign),
    Scan(R.string.fo_onboarding_kicker_3, R.string.fo_onboarding_title_3, R.string.fo_onboarding_body_3, R.layout.page_onboarding_scan),
    Tools(R.string.fo_onboarding_kicker_4, R.string.fo_onboarding_title_4, R.string.fo_onboarding_body_4, R.layout.page_onboarding_tools)
}

/** The pictures only: the text under the pager changes with the page. */
class OnboardingPictures : RecyclerView.Adapter<OnboardingPictures.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = OnboardingSlide.entries.size

    override fun getItemViewType(position: Int) = OnboardingSlide.entries[position].picture

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        when (viewType) {
            R.layout.page_onboarding_open -> {
                docCard(view, R.id.cardWord, "DOCX", R.drawable.bg_chip_word, R.color.color_file_word, tilt = -16f)
                docCard(view, R.id.cardExcel, "XLSX", R.drawable.bg_chip_excel, R.color.color_file_excel, tilt = 12f)
                docCard(view, R.id.cardSlides, "PPTX", R.drawable.bg_chip_powerpoint, R.color.color_file_powerpoint, tilt = -4f, onTop = true)
            }
            R.layout.page_onboarding_tools -> fillTools(view.findViewById(R.id.gridTools))
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit

    private fun docCard(page: View, id: Int, type: String, @DrawableRes chip: Int, @ColorRes ink: Int, tilt: Float, onTop: Boolean = false) {
        val card = page.findViewById<View>(id)
        // <include> drops rotation and z, so they are set here.
        card.rotation = tilt
        if (onTop) card.translationZ = card.resources.getDimension(com.intuit.sdp.R.dimen._2sdp)
        LayoutDocCardBinding.bind(card).tvChip.apply {
            text = type
            setBackgroundResource(chip)
            setTextColor(ContextCompat.getColor(context, ink))
        }
    }

    private fun fillTools(grid: GridLayout) {
        val inflater = LayoutInflater.from(grid.context)
        val gap = grid.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
        TILES.forEachIndexed { index, tile ->
            val item = ItemOnboardingToolBinding.inflate(inflater, grid, false)
            item.ivIcon.setImageResource(tile.icon)
            item.ivIcon.setFillColor(ContextCompat.getColor(grid.context, tile.color))
            item.tvLabel.setText(tile.label)
            item.root.rotation = tile.tilt
            item.root.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(index / 3, 1f), GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(gap, gap, gap, gap)
            }
            grid.addView(item.root)
        }
    }

    private class Tile(@DrawableRes val icon: Int, @StringRes val label: Int, @ColorRes val color: Int, val tilt: Float)

    private companion object {
        val TILES = listOf(
            Tile(R.drawable.ic_fo_merge, R.string.tool_merge, R.color.color_file_pdf, -3f),
            Tile(R.drawable.ic_fo_split, R.string.tool_split, R.color.color_file_powerpoint, 2f),
            Tile(R.drawable.ic_fo_compress, R.string.tool_compress, R.color.color_file_excel, -1f),
            Tile(R.drawable.ic_fo_lock, R.string.tool_protect, R.color.primary, 1f),
            Tile(R.drawable.ic_fo_ocr, R.string.tool_ocr, R.color.color_tool_amber, -2f),
            Tile(R.drawable.ic_fo_translate, R.string.tool_translate, R.color.color_file_image, 3f),
            Tile(R.drawable.ic_fo_watermark, R.string.tool_watermark, R.color.primary, 2f),
            Tile(R.drawable.ic_fo_redact, R.string.tool_redact, R.color.color_tool_indigo, -3f),
            Tile(R.drawable.ic_fo_zip, R.string.zip, R.color.color_file_zip, 1f)
        )
    }
}
