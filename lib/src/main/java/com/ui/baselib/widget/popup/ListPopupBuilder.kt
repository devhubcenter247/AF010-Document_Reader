package com.ui.baselib.widget.popup

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

/**
 * A declarative list popup builder with Compose-like DSL syntax.
 *
 * Usage:
 * ```kotlin
 * ListPopupBuilder(context)
 *     .anchor(anchorView)
 *     .items(
 *         PopupItem("Edit", R.drawable.ic_edit),
 *         PopupItem("Delete", R.drawable.ic_delete),
 *         PopupItem("Share", R.drawable.ic_share)
 *     )
 *     .onItemClick { position, item ->
 *         when (position) {
 *             0 -> editItem()
 *             1 -> deleteItem()
 *             2 -> shareItem()
 *         }
 *     }
 *     .show()
 * ```
 *
 * Or with custom item view:
 * ```kotlin
 * ListPopupBuilder<CustomItem>(context)
 *     .anchor(anchorView)
 *     .items(customItems)
 *     .itemBinding { ItemPopupBinding.inflate(it) }
 *     .bindItem { binding, position, item ->
 *         binding.text.text = item.title
 *         binding.icon.setImageResource(item.iconRes)
 *     }
 *     .onItemClick { position, item ->
 *         handleClick(item)
 *     }
 *     .show()
 * ```
 */
class ListPopupBuilder<T : Any>(private val context: Context) {

    private var anchor: View? = null
    private var width: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var maxHeight: Int = Int.MAX_VALUE
    private var gravity: Int = Gravity.NO_GRAVITY
    private var offsetX: Int = 0
    private var offsetY: Int = 0
    private var backgroundColor: Int = Color.WHITE
    private var cornerRadius: Float = 0f
    private var elevation: Float = 8f
    private var itemSpacing: Int = 0
    private var outsideTouchable: Boolean = true
    private var focusable: Boolean = true
    private var dismissOnItemClick: Boolean = true

    private var items: List<T> = emptyList()
    private var bindingProvider: ((LayoutInflater) -> ViewBinding)? = null
    private var bindItemBlock: ((ViewBinding, Int, T) -> Unit)? = null
    private var onItemClickListener: ((Int, T) -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    // ---- Builder methods ----

    /** Set the anchor view for the popup */
    fun anchor(view: View) = apply { anchor = view }

    /** Set popup width */
    fun width(width: Int) = apply { this.width = width }

    /** Set popup height */
    fun height(height: Int) = apply { this.height = height }

    /** Set maximum popup height */
    fun maxHeight(maxHeight: Int) = apply { this.maxHeight = maxHeight }

    /** Set the gravity for popup positioning */
    fun gravity(gravity: Int) = apply { this.gravity = gravity }

    /** Set offset from anchor */
    fun offset(x: Int = 0, y: Int = 0) = apply {
        offsetX = x
        offsetY = y
    }

    /** Set background color */
    fun backgroundColor(color: Int) = apply { backgroundColor = color }

    /** Set corner radius */
    fun cornerRadius(radius: Float) = apply { cornerRadius = radius }

    /** Set elevation */
    fun elevation(elevation: Float) = apply { this.elevation = elevation }

    /** Set spacing between items */
    fun itemSpacing(spacing: Int) = apply { itemSpacing = spacing }

    /** Set whether clicking outside dismisses the popup */
    fun outsideTouchable(touchable: Boolean) = apply { outsideTouchable = touchable }

    /** Set whether the popup can receive focus */
    fun focusable(focusable: Boolean) = apply { this.focusable = focusable }

    /** Set whether popup dismisses when item is clicked */
    fun dismissOnItemClick(dismiss: Boolean) = apply { dismissOnItemClick = dismiss }

    /** Set list items */
    fun items(items: List<T>) = apply { this.items = items }

    /** Set list items (vararg) */
    fun items(vararg items: T) = apply { this.items = items.toList() }

    /** Provide item view binding */
    fun <VB : ViewBinding> itemBinding(provider: (LayoutInflater) -> VB) = apply {
        @Suppress("UNCHECKED_CAST")
        bindingProvider = provider as (LayoutInflater) -> ViewBinding
    }

    /** Bind item data to view */
    fun <VB : ViewBinding> bindItem(block: (VB, Int, T) -> Unit) = apply {
        @Suppress("UNCHECKED_CAST")
        bindItemBlock = block as (ViewBinding, Int, T) -> Unit
    }

    /** Set item click listener */
    fun onItemClick(listener: (Int, T) -> Unit) = apply { onItemClickListener = listener }

    /** Set dismiss listener */
    fun onDismiss(listener: () -> Unit) = apply { onDismissListener = listener }

    // ---- Actions ----

    /** Build and show the popup */
    fun show(): ListPopupBuilder<T> {
        val anchorView = anchor ?: throw IllegalStateException("Anchor view must be set")

        // Create RecyclerView
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PopupAdapter()
            overScrollMode = View.OVER_SCROLL_NEVER

            if (itemSpacing > 0) {
                addItemDecoration(SpacingDecoration(itemSpacing))
            }
        }

        // Create wrapper with styling
        val wrapper = PopupContentWrapper(context).apply {
            setBackgroundColor(backgroundColor)
            setCornerRadius(cornerRadius)
            if (elevation > 0f) {
                setElevation(elevation)
            }
            addView(recyclerView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        // Measure to determine actual height
        wrapper.measure(
            popupMeasureSpec(width),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )

        val actualHeight = minOf(wrapper.measuredHeight, maxHeight)

        // Create popup window
        popupWindow = PopupWindow(wrapper, width, actualHeight).apply {
            isOutsideTouchable = this@ListPopupBuilder.outsideTouchable
            isFocusable = this@ListPopupBuilder.focusable
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = this@ListPopupBuilder.elevation

            setOnDismissListener {
                onDismissListener?.invoke()
            }
        }

        // Show popup
        popupWindow?.showAsDropDown(anchorView, offsetX, offsetY, gravity)

        return this
    }

    /** Dismiss the popup */
    fun dismiss() {
        popupWindow?.dismiss()
    }

    /** Check if popup is showing */
    fun isShowing(): Boolean = popupWindow?.isShowing == true

    /** Update items and refresh */
    fun updateItems(newItems: List<T>) {
        items = newItems
        // If popup is showing, we need to recreate it
        if (isShowing()) {
            dismiss()
            show()
        }
    }

    // ---- Inner classes ----

    private inner class PopupAdapter : RecyclerView.Adapter<PopupViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PopupViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = bindingProvider?.invoke(inflater)
                ?: throw IllegalStateException("Item binding must be provided via itemBinding()")
            return PopupViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PopupViewHolder, position: Int) {
            val item = items[position]
            bindItemBlock?.invoke(holder.binding, position, item)

            holder.itemView.setOnClickListener {
                onItemClickListener?.invoke(position, item)
                if (dismissOnItemClick) {
                    dismiss()
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private inner class PopupViewHolder(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root)

    private fun popupMeasureSpec(size: Int): Int =
        if (size > 0) {
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }

    private class SpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position > 0) {
                outRect.top = spacing
            }
        }
    }

    companion object {
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT

        /** DSL entry point */
        inline fun <T : Any> create(context: Context, block: ListPopupBuilder<T>.() -> Unit): ListPopupBuilder<T> {
            return ListPopupBuilder<T>(context).apply(block)
        }
    }
}

/**
 * Simple popup item data class for quick menu creation.
 */
data class PopupItem(
    val title: String,
    @DrawableRes val iconRes: Int = 0,
    val enabled: Boolean = true,
    val data: Any? = null
)

/** Extension function for DSL-style list popup creation */
inline fun <T : Any> Context.listPopup(block: ListPopupBuilder<T>.() -> Unit): ListPopupBuilder<T> {
    return ListPopupBuilder.create(this, block)
}

/** Extension function for showing list popup anchored to this view */
inline fun <T : Any> View.showListPopup(block: ListPopupBuilder<T>.() -> Unit): ListPopupBuilder<T> {
    return ListPopupBuilder<T>(context).apply {
        anchor(this@showListPopup)
        block()
    }.show()
}
