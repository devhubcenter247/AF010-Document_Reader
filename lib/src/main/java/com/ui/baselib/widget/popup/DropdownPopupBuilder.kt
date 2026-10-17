package com.ui.baselib.widget.popup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt

/**
 * A declarative dropdown popup builder with Compose-like DSL syntax.
 * Provides built-in styling for quick dropdown menus.
 *
 * Usage:
 * ```kotlin
 * DropdownPopupBuilder(context)
 *     .anchor(spinnerView)
 *     .items("Option 1", "Option 2", "Option 3")
 *     .selectedIndex(0)
 *     .textSize(14f)
 *     .textColor(Color.BLACK)
 *     .selectedTextColor(Color.BLUE)
 *     .itemPadding(horizontal = 16.dp, vertical = 12.dp)
 *     .showCheckMark(true)
 *     .onItemSelected { index, text ->
 *         spinnerView.text = text
 *     }
 *     .show()
 * ```
 */
class DropdownPopupBuilder(private val context: Context) {

    private var anchor: View? = null
    private var width: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var matchAnchorWidth: Boolean = true
    private var maxHeight: Int = Int.MAX_VALUE
    private var gravity: Int = Gravity.NO_GRAVITY
    private var offsetX: Int = 0
    private var offsetY: Int = 0

    // Styling
    private var backgroundColor: Int = Color.WHITE
    private var cornerRadius: Float = dp(8f)
    private var elevation: Float = dp(8f)
    private var itemPaddingHorizontal: Int = dp(16f).toInt()
    private var itemPaddingVertical: Int = dp(12f).toInt()
    private var textSizeSp: Float = 14f  // in SP units
    private var textColor: Int = Color.BLACK
    private var selectedTextColor: Int = "#1976D2".toColorInt()
    private var disabledTextColor: Int = Color.GRAY
    private var textTypeface: Typeface = Typeface.DEFAULT
    private var selectedTextTypeface: Typeface = Typeface.DEFAULT_BOLD
    private var itemBackgroundColor: Int = Color.TRANSPARENT
    private var selectedItemBackgroundColor: Int = Color.TRANSPARENT
    private var itemCornerRadius: Float = 0f
    private var showCheckMark: Boolean = false
    private var checkMarkRes: Int = 0
    private var checkMarkColor: Int = selectedTextColor
    private var dividerColor: Int = "#E0E0E0".toColorInt()
    private var showDivider: Boolean = false
    private var dividerHeight: Int = dp(1f).toInt()

    // Data
    private var items: List<String> = emptyList()
    private var selectedIndex: Int = -1
    private var disabledIndices: Set<Int> = emptySet()

    // Callbacks
    private var onItemSelectedListener: ((Int, String) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    // ---- Builder methods ----

    fun anchor(view: View) = apply { anchor = view }

    fun width(width: Int) = apply {
        this.width = width
        matchAnchorWidth = false
    }

    fun matchAnchorWidth(match: Boolean = true) = apply { matchAnchorWidth = match }

    fun maxHeight(maxHeight: Int) = apply { this.maxHeight = maxHeight }

    fun gravity(gravity: Int) = apply { this.gravity = gravity }

    fun offset(x: Int = 0, y: Int = 0) = apply {
        offsetX = x
        offsetY = y
    }

    fun backgroundColor(@ColorInt color: Int) = apply { backgroundColor = color }

    fun cornerRadius(radius: Float) = apply { cornerRadius = radius }

    fun elevation(elevation: Float) = apply { this.elevation = elevation }

    fun itemPadding(horizontal: Int = itemPaddingHorizontal, vertical: Int = itemPaddingVertical) = apply {
        itemPaddingHorizontal = horizontal
        itemPaddingVertical = vertical
    }

    /** Set text size in SP units */
    fun textSize(sizeSp: Float) = apply { textSizeSp = sizeSp }

    fun textColor(@ColorInt color: Int) = apply { textColor = color }

    fun selectedTextColor(@ColorInt color: Int) = apply { selectedTextColor = color }

    fun disabledTextColor(@ColorInt color: Int) = apply { disabledTextColor = color }

    fun textTypeface(typeface: Typeface) = apply { textTypeface = typeface }

    fun selectedTextTypeface(typeface: Typeface) = apply { selectedTextTypeface = typeface }

    fun itemBackgroundColor(@ColorInt color: Int) = apply { itemBackgroundColor = color }

    fun selectedItemBackgroundColor(@ColorInt color: Int) = apply { selectedItemBackgroundColor = color }

    fun itemCornerRadius(radius: Float) = apply { itemCornerRadius = radius }

    fun showCheckMark(show: Boolean, @DrawableRes iconRes: Int = 0) = apply {
        showCheckMark = show
        if (iconRes != 0) {
            checkMarkRes = iconRes
        }
    }

    fun checkMarkColor(@ColorInt color: Int) = apply { checkMarkColor = color }

    fun showDivider(show: Boolean = true, @ColorInt color: Int = dividerColor, height: Int = dividerHeight) = apply {
        showDivider = show
        dividerColor = color
        dividerHeight = height
    }

    fun items(items: List<String>) = apply { this.items = items }

    fun items(vararg items: String) = apply { this.items = items.toList() }

    fun selectedIndex(index: Int) = apply { selectedIndex = index }

    fun disabledIndices(vararg indices: Int) = apply { disabledIndices = indices.toSet() }

    fun onItemSelected(listener: (Int, String) -> Unit) = apply { onItemSelectedListener = listener }

    fun onDismiss(listener: () -> Unit) = apply { onDismissListener = listener }

    // ---- Actions ----

    fun show(): DropdownPopupBuilder {
        val anchorView = anchor ?: throw IllegalStateException("Anchor view must be set")

        val popupWidth = if (matchAnchorWidth) anchorView.width else width

        // Create RecyclerView
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = DropdownAdapter()
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        // Create wrapper
        val wrapper = PopupContentWrapper(context).apply {
            setBackgroundColor(backgroundColor)
            setCornerRadius(cornerRadius)
            if (this@DropdownPopupBuilder.elevation > 0f) {
                setElevation(this@DropdownPopupBuilder.elevation)
            }
            addView(recyclerView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        // Measure
        wrapper.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )

        val actualHeight = minOf(wrapper.measuredHeight, maxHeight)

        // Create popup
        popupWindow = PopupWindow(wrapper, popupWidth, actualHeight).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = this@DropdownPopupBuilder.elevation

            setOnDismissListener {
                onDismissListener?.invoke()
            }
        }

        popupWindow?.showAsDropDown(anchorView, offsetX, offsetY, gravity)

        return this
    }

    fun dismiss() {
        popupWindow?.dismiss()
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    fun updateSelectedIndex(index: Int) {
        selectedIndex = index
        // Recreate if showing
        if (isShowing()) {
            dismiss()
            show()
        }
    }

    // ---- Helper ----

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private fun createRoundedBackground(@ColorInt color: Int, radius: Float): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    // ---- Adapter ----

    private inner class DropdownAdapter : RecyclerView.Adapter<DropdownViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DropdownViewHolder {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(itemPaddingHorizontal, itemPaddingVertical, itemPaddingHorizontal, itemPaddingVertical)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val textView = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)

                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val checkMark = ImageView(context).apply {
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    dp(20f).toInt(),
                    dp(20f).toInt()
                ).apply {
                    marginStart = dp(8f).toInt()
                }
            }

            itemLayout.addView(textView)
            itemLayout.addView(checkMark)
            container.addView(itemLayout)

            // Divider view
            if (showDivider) {
                val divider = View(context).apply {
                    setBackgroundColor(dividerColor)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dividerHeight
                    )
                }
                container.addView(divider)
            }

            return DropdownViewHolder(container, itemLayout, textView, checkMark)
        }

        override fun onBindViewHolder(holder: DropdownViewHolder, position: Int) {
            val text = items[position]
            val isSelected = position == selectedIndex
            val isDisabled = disabledIndices.contains(position)

            holder.textView.text = text
            holder.textView.typeface = if (isSelected) selectedTextTypeface else textTypeface
            holder.textView.setTextColor(when {
                isDisabled -> disabledTextColor
                isSelected -> selectedTextColor
                else -> textColor
            })

            // Apply item background
            val bgColor = if (isSelected) selectedItemBackgroundColor else itemBackgroundColor
            if (bgColor != Color.TRANSPARENT && itemCornerRadius > 0) {
                holder.itemLayout.background = createRoundedBackground(bgColor, itemCornerRadius)
            } else {
                holder.itemLayout.setBackgroundColor(bgColor)
            }

            // Check mark
            if (showCheckMark && isSelected) {
                holder.checkMark.visibility = View.VISIBLE
                if (checkMarkRes != 0) {
                    holder.checkMark.setImageResource(checkMarkRes)
                } else {
                    // Default checkmark (using a simple drawable)
                    holder.checkMark.setImageDrawable(createCheckMarkDrawable())
                }
                holder.checkMark.setColorFilter(checkMarkColor)
            } else {
                holder.checkMark.visibility = View.GONE
            }

            // Divider visibility (hide for last item)
            if (showDivider && holder.itemView is ViewGroup) {
                val container = holder.itemView as ViewGroup
                if (container.childCount > 1) {
                    container.getChildAt(1).visibility =
                        if (position == items.lastIndex) View.GONE else View.VISIBLE
                }
            }

            // Click handling
            holder.itemView.isEnabled = !isDisabled
            holder.itemView.alpha = if (isDisabled) 0.5f else 1f
            holder.itemView.setOnClickListener {
                if (!isDisabled) {
                    selectedIndex = position
                    onItemSelectedListener?.invoke(position, text)
                    dismiss()
                }
            }
        }

        override fun getItemCount(): Int = items.size

        private fun createCheckMarkDrawable(): android.graphics.drawable.Drawable {
            return object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = dp(2f)
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    color = checkMarkColor
                }

                override fun draw(canvas: android.graphics.Canvas) {
                    val w = bounds.width().toFloat()
                    val h = bounds.height().toFloat()

                    val path = android.graphics.Path().apply {
                        moveTo(w * 0.2f, h * 0.5f)
                        lineTo(w * 0.4f, h * 0.7f)
                        lineTo(w * 0.8f, h * 0.3f)
                    }
                    canvas.drawPath(path, paint)
                }

                override fun setAlpha(alpha: Int) {
                    paint.alpha = alpha
                }

                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
                    paint.colorFilter = colorFilter
                }

                @Deprecated("Deprecated in Java")
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
        }
    }

    private inner class DropdownViewHolder(
          itemView: View,
          val itemLayout: LinearLayout,
          val textView: TextView,
          val checkMark: ImageView
    ) : RecyclerView.ViewHolder(itemView)

    companion object {
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

        inline fun create(context: Context, block: DropdownPopupBuilder.() -> Unit): DropdownPopupBuilder {
            return DropdownPopupBuilder(context).apply(block)
        }
    }
}

/** Extension for DSL-style dropdown creation */
inline fun Context.dropdown(block: DropdownPopupBuilder.() -> Unit): DropdownPopupBuilder {
    return DropdownPopupBuilder.create(this, block)
}

/** Extension for showing dropdown anchored to this view */
inline fun View.showDropdown(block: DropdownPopupBuilder.() -> Unit): DropdownPopupBuilder {
    return DropdownPopupBuilder(context).apply {
        anchor(this@showDropdown)
        block()
    }.show()
}
