package com.ui.baselib.base

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewbinding.ViewBinding
import java.util.Collections

abstract class BaseAdapter<T, VB : ViewBinding> :
    RecyclerView.Adapter<BaseAdapter<T, VB>.ViewHolder>(), LifecycleOwner {

    private val listItem = mutableListOf<T>()
    val currentPosition = MutableLiveData(RecyclerView.NO_POSITION)

    // Lifecycle support
    private var lifecycleRegistry = LifecycleRegistry(this)
    protected var context: Context? = null
        private set

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
        // A changed row is redrawn in place: the default cross-fade makes the list blink.
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        // Re-attached (e.g. fragment view rebuilt): a DESTROYED registry keeps its scope cancelled.
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            lifecycleRegistry = LifecycleRegistry(this)
        }
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun stringRes(@StringRes res: Int): String = context?.getString(res) ?: ""

    fun stringRes(@StringRes res: Int, vararg args: Any): String = context?.getString(res, *args) ?: ""

    var onPositionChanged: (Int) -> Unit = {}

    abstract fun createBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): VB
    abstract fun VB.bind(item: T, position: Int)

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
    inner class ViewHolder(val binding: VB) : RecyclerView.ViewHolder(binding.root) {

        val context: Context
            get() = binding.root.context

        fun stringRes(@StringRes res: Int): String = context.getString(res)

        fun stringRes(@StringRes res: Int, vararg args: Any): String = context.getString(res, *args)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val vb = createBinding(inflater, parent, viewType)
        return ViewHolder(vb)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position !in listItem.indices) return
        val item = listItem[position]
        holder.binding.bind(item, position)
    }

    override fun getItemCount(): Int = listItem.size

    val currentItems: List<T>
        get() = listItem.toList()

    /** Check if adapter has no items */
    fun isEmpty(): Boolean = listItem.isEmpty()

    /** Check if adapter has items */
    fun isNotEmpty(): Boolean = listItem.isNotEmpty()

    /** Get item at position safely, returns null if out of bounds */
    fun getItem(position: Int): T? = listItem.getOrNull(position)

    /** Get currently selected item safely */
    fun getCurrentItem(): T? = getItem(getCurrentPos())

    /** Get immutable copy of current list */
    fun getListItem(): List<T> = listItem.toList()

    /** Get mutable list reference (use with caution) */
    fun getMutableList(): MutableList<T> = listItem

    /** Submit new list and refresh all items. */
    fun submitList(items: List<T>) = apply {
        this.listItem.clear()
        this.listItem.addAll(items)
        notifyDataSetChanged()
        normalizeCurrentPosition()
    }

    /** Add single item at specific index */
    fun addItem(item: T, index: Int = listItem.size) {
        val insertIndex = index.coerceIn(0, listItem.size)
        this.listItem.add(insertIndex, item)
        notifyItemInserted(insertIndex)
        val current = getCurrentPos()
        if (current != RecyclerView.NO_POSITION && insertIndex <= current) moveSelection(current + 1)
    }

    /** Add multiple items at end of list */
    fun addItems(items: List<T>) {
        if (items.isEmpty()) return
        val startPos = listItem.size
        this.listItem.addAll(items)
        notifyItemRangeInserted(startPos, items.size)
    }

    /** Remove item at position */
    fun removeItem(position: Int) {
        if (position in 0 until listItem.size) {
            listItem.removeAt(position)
            notifyItemRemoved(position)
            // bind() receives the position, so rows after the removed one must rebind.
            notifyItemRangeChanged(position, listItem.size - position)
            val current = getCurrentPos()
            when {
                current == position -> moveSelection(RecyclerView.NO_POSITION)
                current > position -> moveSelection(current - 1)
            }
        }
    }

    /** Remove specific item */
    fun removeItem(item: T) {
        val index = listItem.indexOf(item)
        if (index != -1) removeItem(index)
    }

    /** Remove all items matching a predicate. */
    fun removeItems(predicate: (T) -> Boolean) {
        val newItems = listItem.filterNot(predicate)
        if (newItems.size == listItem.size) return
        submitList(newItems)
        normalizeCurrentPosition()
    }

    /** Update item at position */
    fun updateItem(index: Int, newItem: T) {
        if (index in 0 until listItem.size) {
            listItem[index] = newItem
            notifyItemChanged(index)
        }
    }

    /** Update first item matching a predicate. */
    fun updateItem(predicate: (T) -> Boolean, transform: (T) -> T) {
        val index = listItem.indexOfFirst(predicate)
        if (index == -1) return
        updateItem(index, transform(listItem[index]))
    }

    /** Replace all items using a transformation and refresh once. */
    fun replaceAll(transform: (T) -> T) {
        if (listItem.isEmpty()) return
        submitList(listItem.map(transform))
    }

    /** Move item from one position to another (for drag & drop) */
    fun onMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition !in listItem.indices || toPosition !in listItem.indices) return
        if (fromPosition == toPosition) return

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(listItem, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(listItem, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        val current = getCurrentPos()
        when {
            current == fromPosition -> moveSelection(toPosition)
            fromPosition < toPosition && current in (fromPosition + 1)..toPosition -> moveSelection(current - 1)
            fromPosition > toPosition && current in toPosition until fromPosition -> moveSelection(current + 1)
        }
    }

    /** Sort items with a comparator and refresh once. */
    fun sortWith(comparator: Comparator<in T>) {
        if (listItem.size < 2) return
        listItem.sortWith(comparator)
        notifyDataSetChanged()
    }

    /** Sort items by an ascending comparable value. */
    fun <R : Comparable<R>> sortBy(selector: (T) -> R?) {
        sortWith(compareBy(selector))
    }

    /** Sort items by a descending comparable value. */
    fun <R : Comparable<R>> sortByDescending(selector: (T) -> R?) {
        sortWith(compareByDescending(selector))
    }

    /** Remove item by swipe */
    fun swipe(position: Int) {
        removeItem(position)
    }

    /** Clear all items */
    fun clearAll() {
        val size = listItem.size
        if (size == 0) return
        listItem.clear()
        currentPosition.value = RecyclerView.NO_POSITION
        onPositionChanged(RecyclerView.NO_POSITION)
        notifyItemRangeRemoved(0, size)
    }

    /** Set current selected position and notify changes */
    fun setCurrentPos(position: Int) {
        val prev = currentPosition.value ?: RecyclerView.NO_POSITION
        if (prev == position) return

        if (prev in 0 until itemCount) notifyItemChanged(prev)
        currentPosition.value = position
        if (position in 0 until itemCount) notifyItemChanged(position)
        onPositionChanged(position)
    }

    /** Get current selected position */
    fun getCurrentPos(): Int = currentPosition.value ?: RecyclerView.NO_POSITION

    /** Follows the selected item to its new index without rebinding (the row itself didn't change). */
    private fun moveSelection(position: Int) {
        currentPosition.value = position
        onPositionChanged(position)
    }

    private fun normalizeCurrentPosition() {
        val current = getCurrentPos()
        if (current == RecyclerView.NO_POSITION) return
        if (current !in listItem.indices) {
            currentPosition.value = RecyclerView.NO_POSITION
            onPositionChanged(RecyclerView.NO_POSITION)
        }
    }
}
