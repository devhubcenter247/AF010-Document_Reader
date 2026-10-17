package com.ui.baselib.extensions

import android.R
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ui.baselib.api.putAnySafe

inline fun <reified T : Fragment> newInstanceWithArgs(vararg params: Pair<String, Any?>): T {
    val fragment = try {
        T::class.java.getDeclaredConstructor().newInstance()
    } catch (e: NoSuchMethodException) {
        throw IllegalArgumentException("Fragment ${T::class.java.simpleName} must have a public no-arg constructor.", e)
    }
    fragment.arguments = Bundle().apply {
        for ((key, value) in params) {
            try {
                putAnySafe(key, value)
            } catch (e: Throwable) {
                android.util.Log.e("BaseExtensions", "Skip argument \"$key\"", e)
            }
        }
    }
    return fragment
}

private fun buildTag(fragment: Fragment, containerId: Int, extra: String? = null): String {
    val base = fragment::class.java.name
    return if (extra.isNullOrBlank()) "$base#c=$containerId" else "$base#c=$containerId#$extra"
}

private fun ensureCanUse(fragment: Fragment, fm: FragmentManager) {
    if (fragment.isAdded && fragment.parentFragmentManager !== fm) {
        throw IllegalStateException("Fragment instance is already added to a different FragmentManager.")
    }
}

fun AppCompatActivity.changeFragment(
      fragment: Fragment,
      containerId: Int = R.id.content,
      addToBackStack: Boolean = false,
      tagExtra: String? = null
) {
    if (isFinishing || isDestroyed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        runOnUiThread { changeFragment(fragment, containerId, addToBackStack, tagExtra) }
        return
    }
    val fm = supportFragmentManager
    val container = findViewById<View?>(containerId) ?: return
    val tag = buildTag(fragment, containerId, tagExtra)
    val existing = fm.findFragmentByTag(tag)
    val target = existing ?: fragment
    ensureCanUse(target, fm)
    val current = fm.fragments.firstOrNull { it.isAdded && it.isVisible && it.id == containerId }
    if (current === target) return
    runCatching { fm.executePendingTransactions() }
    val tx = fm.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left,
            com.ui.baselib.R.anim.enter_from_left,
            com.ui.baselib.R.anim.exit_to_right
        )
        current?.let { if (it.isAdded) hide(it) }
        if (target.isAdded) {
            val targetContainerId = target.id
            if (targetContainerId != container.id) {
                remove(target)
                add(container.id, target, tag)
            } else {
                if (target.isDetached) attach(target) else show(target)
            }
        } else {
            add(container.id, target, tag)
        }
        if (addToBackStack) addToBackStack(tag)
    }
    if (fm.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commit() }.onFailure { tx.commitAllowingStateLoss() }
}

fun AppCompatActivity.addFragment(
      fragment: Fragment,
      containerId: Int = R.id.content,
      addToBackStack: Boolean = false,
      tagExtra: String? = null
) {
    if (isFinishing || isDestroyed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        runOnUiThread { addFragment(fragment, containerId, addToBackStack, tagExtra) }
        return
    }
    val fm = supportFragmentManager
    val container = findViewById<View?>(containerId) ?: return
    val tag = buildTag(fragment, containerId, tagExtra)
    val existing = fm.findFragmentByTag(tag)
    val target = existing ?: fragment
    ensureCanUse(target, fm)
    runCatching { fm.executePendingTransactions() }
    val tx = fm.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left,
            com.ui.baselib.R.anim.enter_from_left,
            com.ui.baselib.R.anim.exit_to_right
        )
        fm.fragments.lastOrNull { it != target && it.isAdded && it.isVisible && it.id == container.id }?.let { hide(it) }
        if (target.isAdded) {
            val targetContainerId = target.id
            if (targetContainerId != container.id) {
                remove(target)
                add(container.id, target, tag)
            } else {
                if (target.isDetached) attach(target) else show(target)
            }
        } else {
            add(container.id, target, tag)
        }
        if (addToBackStack) addToBackStack(tag)
    }
    if (fm.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commit() }.onFailure { tx.commitAllowingStateLoss() }
}

fun AppCompatActivity.replaceFragment(
      fragment: Fragment,
      containerId: Int = R.id.content,
      addToBackStack: Boolean = false,
      tagExtra: String? = null
) {
    if (isFinishing || isDestroyed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        runOnUiThread { replaceFragment(fragment, containerId, addToBackStack, tagExtra) }
        return
    }
    val fm = supportFragmentManager
    val container = findViewById<View?>(containerId) ?: return
    val tag = buildTag(fragment, containerId, tagExtra)
    val existing = fm.findFragmentByTag(tag)
    val target = existing ?: fragment
    ensureCanUse(target, fm)
    val current = fm.findFragmentById(containerId)
    if (current === target && target.isVisible) return
    runCatching { fm.executePendingTransactions() }
    val tx = fm.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left,
            com.ui.baselib.R.anim.enter_from_left,
            com.ui.baselib.R.anim.exit_to_right
        )
        if (target.isAdded) {
            val targetContainerId = target.id
            if (targetContainerId != container.id) {
                remove(target)
                add(container.id, target, tag)
            } else {
                if (target.isDetached) attach(target) else show(target)
            }
        } else {
            add(container.id, target, tag)
        }
        supportFragmentManager.fragments
            .filter { it !== target && it.id == container.id && it.isAdded }
            .forEach { remove(it) }
        if (addToBackStack) addToBackStack(tag)
    }
    if (fm.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commit() }.onFailure { tx.commitAllowingStateLoss() }
}

fun AppCompatActivity.closeFragment(fragment: Fragment) {
    if (isFinishing || isDestroyed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        runOnUiThread { closeFragment(fragment) }
        return
    }
    if (fragment.isAdded && fragment.isVisible) {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(
                com.ui.baselib.R.anim.enter_from_right,
                com.ui.baselib.R.anim.exit_to_left
            )
            .remove(fragment)
            .commitNowAllowingStateLoss()
    }
}

private fun Fragment.fm(): FragmentManager = childFragmentManager
private fun Fragment.onMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
private fun Fragment.containerOrNull(containerId: Int): View? = view?.findViewById(containerId)

fun Fragment.addFragment(
      fragment: Fragment,
      containerId: Int = R.id.content,
      addToBackStack: Boolean = false,
      tagExtra: String? = null
) {
    if (!onMainThread()) {
        requireActivity().runOnUiThread { addFragment(fragment, containerId, addToBackStack, tagExtra) }
        return
    }
    val manager = fm()
    val container = containerOrNull(containerId) ?: return
    val tag = buildTag(fragment, containerId, tagExtra)
    val existing = manager.findFragmentByTag(tag)
    val target = existing ?: fragment
    ensureCanUse(target, manager)
    runCatching { manager.executePendingTransactions() }
    val tx = manager.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left,
            com.ui.baselib.R.anim.enter_from_left,
            com.ui.baselib.R.anim.exit_to_right
        )
        if (target.isAdded) {
            val targetContainerId = target.id
            if (targetContainerId != container.id) {
                remove(target)
                add(container.id, target, tag)
            } else {
                show(target)
            }
        } else {
            add(container.id, target, tag)
        }
        if (addToBackStack) addToBackStack(tag)
    }
    if (manager.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commit() }.onFailure { tx.commitAllowingStateLoss() }
}

fun Fragment.replaceFullViewFragment(
    fragment: Fragment,
    containerId: Int,
    addToBackStack: Boolean = false,
    tagExtra: String? = null
) {
    if (!onMainThread()) {
        requireActivity().runOnUiThread { replaceFullViewFragment(fragment, containerId, addToBackStack, tagExtra) }
        return
    }
    val manager = fm()
    val container = containerOrNull(containerId) ?: return
    val tag = buildTag(fragment, containerId, tagExtra)
    val existing = manager.findFragmentByTag(tag)
    val target = existing ?: fragment
    ensureCanUse(target, manager)
    val current = manager.findFragmentById(container.id)
    if (current === target && target.isVisible) return
    runCatching { manager.executePendingTransactions() }
    val tx = manager.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left,
            com.ui.baselib.R.anim.enter_from_left,
            com.ui.baselib.R.anim.exit_to_right
        )
        if (target.isAdded) {
            val targetContainerId = target.id
            if (targetContainerId != container.id) {
                remove(target)
                add(container.id, target, tag)
            } else {
                show(target)
            }
        } else {
            add(container.id, target, tag)
        }
        manager.fragments.filter { it !== target && it.id == container.id && it.isAdded }.forEach { remove(it) }
        if (addToBackStack) addToBackStack(tag)
    }
    if (manager.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commit() }.onFailure { tx.commitAllowingStateLoss() }
}

fun Fragment.replaceFragment(
      fragment: Fragment,
      containerId: Int = R.id.content,
      addToBackStack: Boolean = true,
      tagExtra: String? = null
) {
    if (!onMainThread()) {
        requireActivity().runOnUiThread { replaceFragment(fragment, containerId, addToBackStack, tagExtra) }
        return
    }
    val manager = fm()
    val container = containerOrNull(containerId) ?: return
    val tag = buildTag(fragment, containerId, tagExtra)
    val existing = manager.findFragmentByTag(tag)
    val target = existing ?: fragment
    ensureCanUse(target, manager)
    val current = manager.findFragmentById(container.id)
    if (current === target && target.isVisible) return
    runCatching { manager.executePendingTransactions() }
    val tx = manager.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left,
            com.ui.baselib.R.anim.enter_from_left,
            com.ui.baselib.R.anim.exit_to_right
        )
        if (target.isAdded) {
            val targetContainerId = target.id
            if (targetContainerId != container.id) {
                remove(target)
                add(container.id, target, tag)
            } else {
                show(target)
            }
        } else {
            add(container.id, target, tag)
        }
        manager.fragments.filter { it !== target && it.id == container.id && it.isAdded }.forEach { remove(it) }
        if (addToBackStack) addToBackStack(tag)
    }
    if (manager.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commit() }.onFailure { tx.commitAllowingStateLoss() }
}

fun closeFragment(fragment: Fragment) {
    val act = fragment.activity as? AppCompatActivity ?: return
    if (act.isFinishing || act.isDestroyed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        act.runOnUiThread { closeFragment(fragment) }
        return
    }
    val fm = act.supportFragmentManager
    runCatching { fm.executePendingTransactions() }
    val tx = fm.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left
        )
        if (fragment.isAdded) remove(fragment)
    }
    if (fm.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commitNow() }.onFailure { tx.commitNowAllowingStateLoss() }
}

fun Fragment.closeSelf() {
    val act = activity as? AppCompatActivity ?: return
    if (act.isFinishing || act.isDestroyed) return
    if (Looper.myLooper() != Looper.getMainLooper()) {
        act.runOnUiThread { closeSelf() }
        return
    }
    val fm = parentFragmentManager
    runCatching { fm.executePendingTransactions() }
    val tx = fm.beginTransaction().apply {
        setReorderingAllowed(true)
        setCustomAnimations(
            com.ui.baselib.R.anim.enter_from_right,
            com.ui.baselib.R.anim.exit_to_left
        )
        if (isAdded) remove(this@closeSelf)
    }
    if (fm.isStateSaved) tx.commitAllowingStateLoss() else runCatching { tx.commitNow() }.onFailure { tx.commitNowAllowingStateLoss() }
}


inline fun <T> List<T>.moveItemToPosition(position: Int, predicate: (T) -> Boolean): List<T> {
    if (isEmpty()) return this
    for (element in this.withIndex()) {
        if (predicate(element.value)) {
            return this.toMutableList().apply {
                removeAt(element.index)
                val targetIndex = (position - 1).coerceIn(0, size)
                add(targetIndex, element.value)
            }.toList()
        }
    }
    return this
}