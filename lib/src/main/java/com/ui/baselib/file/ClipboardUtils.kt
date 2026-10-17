package com.ui.baselib.file

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

// ============================================================================
// region Clipboard Operations
// ============================================================================

/**
 * Copy text to clipboard.
 *
 * @param text The text to copy
 * @param label Optional label for the clip data
 */
fun Context.copyToClipboard(text: String, label: String = "Copy") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

/**
 * Get text from clipboard.
 *
 * @return The clipboard text, or null if empty or not text
 */
fun Context.getFromClipboard(): String? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    return if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).text?.toString()
    } else null
}

/**
 * Check if clipboard has text content.
 */
fun Context.hasClipboardText(): Boolean {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType("text/plain") == true
}

/**
 * Clear the clipboard.
 */
fun Context.clearClipboard() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
    } else {
        val clip = ClipData.newPlainText("", "")
        clipboard.setPrimaryClip(clip)
    }
}

// endregion