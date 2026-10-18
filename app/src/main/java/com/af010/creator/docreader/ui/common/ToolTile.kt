package com.af010.creator.docreader.ui.common

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.Tool

/** The tool's glyph in white on its colour, inside the rounded tile (bg_tool_glyph). */
fun ImageView.showToolTile(tool: Tool) {
    setImageResource(tool.glyph)
    setBackgroundResource(R.drawable.bg_tool_glyph)
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, tool.tint))
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.on_primary)))
    // Views laid out for the old full-tile pictures have no padding: keep the glyph a quarter in.
    val size = layoutParams?.width ?: 0
    if (paddingLeft == 0 && size > 0) (size / 4).let { setPadding(it, it, it, it) }
}
