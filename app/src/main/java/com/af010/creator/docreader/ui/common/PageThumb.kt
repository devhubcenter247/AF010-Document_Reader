package com.af010.creator.docreader.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.ui.dialog.hostAccent
import com.intuit.sdp.R as SdpR
import kotlin.math.roundToInt

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
    val thin = resources.getDimension(SdpR.dimen._1sdp)
    // The picture's edge sits under the solid middle of the ring, never at its soft outer edge,
    // so no white fringe shows around it; its corners follow the ring's inner curve.
    val inset = (thin / 2).roundToInt().coerceAtLeast(1)
    card.background = null
    // A margin, not the card's padding: the card's foreground (the ring) would follow its padding in.
    (picture.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
        if (params.leftMargin != inset || params.topMargin != inset) {
            params.setMargins(inset, inset, inset, inset)
            picture.layoutParams = params
        }
    }
    picture.background = GradientDrawable().apply {
        cornerRadius = radius - inset
        setColor(Color.WHITE) // paper, white in both themes like bg_page_sheet
    }
    picture.clipToOutline = true
    card.foreground = GradientDrawable().apply {
        cornerRadius = radius
        // Picked: half again the plain ring (sdp has no 1.5), so the pick shows without a heavy frame.
        if (picked) setStroke((thin * 1.5f).roundToInt(), accent)
        else setStroke(thin.roundToInt().coerceAtLeast(1), ContextCompat.getColor(card.context, R.color.color_stroke))
    }
    number.background = if (picked) GradientDrawable().apply {
        cornerRadius = resources.getDimension(SdpR.dimen._9sdp)
        setColor(accent)
    } else null
}

@ColorInt
private fun Context.pageThumbAccent(): Int =
    ContextCompat.getColor(this, hostAccent() ?: R.color.primary)
