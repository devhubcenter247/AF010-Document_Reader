package com.af010.creator.docreader.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.ui.dialog.hostAccent
import com.intuit.sdp.R as SdpR

/**
 * Paints a page or slide thumbnail (item_page_thumb, item_slide_thumb, item_slide_sorter): the
 * [picture] fills [card] as a white sheet with the ring's corners, and the ring is drawn in the
 * card's foreground over the picture's edge, so the two always meet. Picked, the ring and the
 * [number] pill take [accent]: by default the colour of the file on screen ([hostAccent]), else
 * the brand colour.
 */
fun showPageThumb(
    card: View,
    picture: View,
    number: TextView,
    picked: Boolean,
    @ColorInt accent: Int = card.context.pageThumbAccent(),
) {
    val resources = card.resources
    val radius = resources.getDimension(SdpR.dimen._8sdp)
    card.background = null
    picture.background = GradientDrawable().apply {
        cornerRadius = radius
        setColor(Color.WHITE) // paper, white in both themes like bg_page_sheet
    }
    picture.clipToOutline = true
    card.foreground = GradientDrawable().apply {
        cornerRadius = radius
        if (picked) setStroke(resources.getDimensionPixelSize(SdpR.dimen._2sdp), accent)
        else setStroke(resources.getDimensionPixelSize(SdpR.dimen._1sdp), ContextCompat.getColor(card.context, R.color.color_stroke))
    }
    number.background = if (picked) GradientDrawable().apply {
        cornerRadius = resources.getDimension(SdpR.dimen._9sdp)
        setColor(accent)
    } else null
}

@ColorInt
private fun Context.pageThumbAccent(): Int =
    ContextCompat.getColor(this, hostAccent() ?: R.color.primary)
