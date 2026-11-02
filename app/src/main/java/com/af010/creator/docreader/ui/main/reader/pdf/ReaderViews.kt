package com.af010.creator.docreader.ui.main.reader.pdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.ui.common.ColorGridDialog
import com.af010.creator.docreader.ui.dialog.hostAccent
import com.af010.creator.docreader.utils.extensions.setCircle
import com.af010.creator.docreader.utils.extensions.setStroke
import com.ui.baselib.utils.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun SeekBar.onProgress(listener: (Int) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) listener(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })
}

/**
 * Fills the row with one round dot per colour, [sizeRes] wide, then a rainbow "+" that opens the
 * full palette ([ColorGridDialog]); [onPick] gets the tapped or picked colour. A colour picked
 * there is kept on the "+" so [selectColorDot] can ring it.
 */
internal fun LinearLayout.addColorDots(
    colors: List<Int>,
    @androidx.annotation.DimenRes sizeRes: Int = com.intuit.sdp.R.dimen._22sdp,
    onPick: (Int) -> Unit
) {
    removeAllViews()
    val size = resources.getDimensionPixelSize(sizeRes)
    val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
    colors.forEachIndexed { index, dotColor ->
        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { if (index > 0) marginStart = gap }
            setCircle(dotColor)
            tag = dotColor
            setOnClickListener { onPick(dotColor) }
        }
        addView(dot)
    }
    addView(ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = gap }
        setBackgroundResource(R.drawable.bg_color_more)
        setImageResource(R.drawable.ic_fo_plus)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        setPadding(size / 4, size / 4, size / 4, size / 4)
        contentDescription = context.getString(R.string.fo_colour)
        setOnClickListener {
            val activity = context.findActivity() ?: return@setOnClickListener
            ColorGridDialog(activity, tag as? Int) { picked ->
                tag = picked
                onPick(picked)
            }.show()
        }
    })
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** The dot of [color] gets a ring, in the colour of the open file. */
internal fun LinearLayout.selectColorDot(color: Int) {
    for (index in 0 until childCount) {
        val dot = getChildAt(index) as? View ?: continue
        val selected = dot.tag == color
        val ring = if (selected) context.hostAccent() ?: R.color.primary else R.color.color_checkbox_stroke
        dot.setStroke(context.dpToPx(if (selected) 3 else 1), ContextCompat.getColor(context, ring))
        dot.invalidate()
    }
}

internal fun View.setEnabledLook(enabled: Boolean) {
    isEnabled = enabled
    alpha = if (enabled) 1f else 0.35f
}

/** The picture at [uri], sampled down so its longer side stays near [maxPx]; null when it cannot be read. */
internal suspend fun Context.loadPicture(uri: Uri, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPx) sample *= 2
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
