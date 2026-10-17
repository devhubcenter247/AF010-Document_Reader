@file:Suppress("unused")

package com.ui.baselib.utils
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat

fun textStyle(init: TextStyle.() -> Unit): CharSequence = TextStyle().apply(init).build()
fun textStyle(context: Context, init: TextStyle.() -> Unit): CharSequence =
    TextStyle(context).apply(init).build()

class TextStyle(private val context: Context? = null) {
    private val b = SpannableStringBuilder()
    fun append(
        text: String,
        style: Int = Typeface.NORMAL,
        textColor: Int = Color.BLACK,
        underline: Boolean = false,
        centerLine: Boolean = false,
        bgColor: Int? = null,
        fontSizeSp: Float? = null,
        typeface: Typeface? = null,
        @FontRes fontRes: Int? = null,
        onClick: (() -> Unit)? = null
    ) = apply {
        applyStyle(
            text = text,
            style = style,
            textColor = textColor,
            underline = underline,
            centerLine = centerLine,
            bgColor = bgColor,
            fontSizeSp = fontSizeSp,
            typeface = typeface ?: loadTypeface(fontRes),
            onClick = onClick
        )
    }

    fun bold(
        text: String,
        textColor: Int = Color.BLACK,
        underline: Boolean = false,
        centerLine: Boolean = false,
        bgColor: Int? = null,
        fontSizeSp: Float? = null,
        typeface: Typeface? = null,
        @FontRes fontRes: Int? = null,
        onClick: (() -> Unit)? = null
    ) = append(
        text,
        Typeface.BOLD,
        textColor,
        underline,
        centerLine,
        bgColor,
        fontSizeSp,
        typeface,
        fontRes,
        onClick
    )

    fun italic(
        text: String,
        textColor: Int = Color.BLACK,
        underline: Boolean = false,
        centerLine: Boolean = false,
        bgColor: Int? = null,
        fontSizeSp: Float? = null,
        typeface: Typeface? = null,
        @FontRes fontRes: Int? = null,
        onClick: (() -> Unit)? = null
    ) = append(
        text,
        Typeface.ITALIC,
        textColor,
        underline,
        centerLine,
        bgColor,
        fontSizeSp,
        typeface,
        fontRes,
        onClick
    )

    fun boldItalic(
        text: String,
        textColor: Int = Color.BLACK,
        underline: Boolean = false,
        centerLine: Boolean = false,
        bgColor: Int? = null,
        fontSizeSp: Float? = null,
        typeface: Typeface? = null,
        @FontRes fontRes: Int? = null,
        onClick: (() -> Unit)? = null
    ) = append(
        text,
        Typeface.BOLD_ITALIC,
        textColor,
        underline,
        centerLine,
        bgColor,
        fontSizeSp,
        typeface,
        fontRes,
        onClick
    )

    fun normal(
        text: String,
        textColor: Int = Color.BLACK,
        underline: Boolean = false,
        centerLine: Boolean = false,
        bgColor: Int? = null,
        fontSizeSp: Float? = null,
        typeface: Typeface? = null,
        @FontRes fontRes: Int? = null,
        onClick: (() -> Unit)? = null
    ) = append(
        text,
        Typeface.NORMAL,
        textColor,
        underline,
        centerLine,
        bgColor,
        fontSizeSp,
        typeface,
        fontRes,
        onClick
    )

    fun build(): CharSequence = b

    private fun applyStyle(
        text: String,
        style: Int,
        textColor: Int,
        underline: Boolean,
        centerLine: Boolean,
        bgColor: Int?,
        fontSizeSp: Float?,
        typeface: Typeface?,
        onClick: (() -> Unit)?
    ) {
        if (text.isEmpty()) return
        val start = b.length
        b.append(text)
        val end = b.length

        fun safeSpan(span: Any) {
            val s = start.coerceIn(0, b.length)
            val e = end.coerceIn(s, b.length)
            if (s == e) return
            runCatching { b.setSpan(span, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }

        safeSpan(StyleSpan(style))
        safeSpan(ForegroundColorSpan(textColor))
        if (underline) safeSpan(UnderlineSpan())
        if (centerLine) safeSpan(StrikethroughSpan())
        bgColor?.let { safeSpan(BackgroundColorSpan(it)) }

        fontSizeSp?.takeIf { it > 0f }?.let {
            safeSpan(AbsoluteSizeSpan(it.toInt(), true))
        }

        typeface?.let { tf -> safeSpan(CustomTypefaceSpan(tf)) }

        onClick?.let { cb ->
            safeSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = cb()
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = underline
                }
            })
        }
    }
    fun lineHeightPx(px: Int) = apply {
        if (px > 0 && b.isNotEmpty()) {
            b.setSpan(FixedLineHeightSpan(px), 0, b.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }

    fun lineHeightSp(sp: Float, ctx: Context? = context) = apply {
        val px = if (ctx != null) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, ctx.resources.displayMetrics
        ).toInt() else sp.toInt()
        lineHeightPx(px)
    }
    private fun loadTypeface(@FontRes fontRes: Int?): Typeface? =
        if (fontRes != null && context != null) runCatching {
            context?.let {
                ResourcesCompat.getFont(context, fontRes)
            }
        }.getOrNull() else null
}

class FixedLineHeightSpan(private val heightPx: Int) : LineHeightSpan.WithDensity {
    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
        fm: Paint.FontMetricsInt, paint: TextPaint
    ) = applyWithDensity(fm, paint.density)

    override fun chooseHeight(
        text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
        fm: Paint.FontMetricsInt
    ) = applyWithDensity(fm, 1f)

    @Suppress("UNUSED_PARAMETER")
    private fun applyWithDensity(fm: Paint.FontMetricsInt, density: Float) {
        val need = heightPx
        val origin = fm.descent - fm.ascent
        if (origin <= 0) return
        val diff = need - origin
        val half = diff / 2
        fm.ascent -= half
        fm.top = fm.ascent
        fm.descent += diff - half
        fm.bottom = fm.descent
    }
}

class CustomTypefaceSpan(private val tf: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    override fun updateMeasureState(tp: TextPaint) = apply(tp)
    private fun apply(tp: TextPaint) {
        val old = tp.typeface
        val oldStyle = old?.style ?: 0
        val fakeBold = (oldStyle and Typeface.BOLD) != 0 && (tf.style and Typeface.BOLD) == 0
        val fakeItalic = (oldStyle and Typeface.ITALIC) != 0 && (tf.style and Typeface.ITALIC) == 0

        tp.typeface = tf
        tp.isFakeBoldText = fakeBold
        tp.textSkewX = if (fakeItalic) -0.25f else 0f
    }
}

fun TextView.enableClickableSpans() {
    movementMethod = LinkMovementMethod.getInstance()
    highlightColor = Color.TRANSPARENT
}

fun TextView.setStyled(init: TextStyle.() -> Unit) {
    text = textStyle(context, init)
    enableClickableSpans()
}
