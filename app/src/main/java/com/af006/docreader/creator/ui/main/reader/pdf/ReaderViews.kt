package com.af010.creator.docreader.ui.main.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.R
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

/** Fills the row with one round dot per colour, [sizeRes] wide; [onPick] gets the tapped one. */
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
}

/** The dot of [color] gets a ring. */
internal fun LinearLayout.selectColorDot(color: Int) {
    for (index in 0 until childCount) {
        val dot = getChildAt(index) as? View ?: continue
        val selected = dot.tag == color
        dot.setStroke(context.dpToPx(if (selected) 3 else 1), ContextCompat.getColor(context, if (selected) R.color.primary else R.color.color_checkbox_stroke))
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
