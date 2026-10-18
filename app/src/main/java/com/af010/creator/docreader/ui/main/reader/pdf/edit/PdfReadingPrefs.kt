package com.af010.creator.docreader.ui.main.reader.pdf.edit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A page the user marked in a document, with a label. [page] is zero based. */
internal data class PdfBookmark(val page: Int, val label: String)

/**
 * What the reader remembers per document (by path): the last page read and the bookmarks; and
 * for every document: the page colors (normal, night, sepia) and the auto scroll speed.
 */
internal class PdfReadingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pdf_reading", Context.MODE_PRIVATE)

    fun lastPage(path: String): Int = prefs.getInt("page:$path", -1)

    fun setLastPage(path: String, page: Int) { prefs.edit().putInt("page:$path", page).apply() }

    fun bookmarks(path: String): List<PdfBookmark> = try {
        val array = JSONArray(prefs.getString("marks:$path", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { PdfBookmark(it.getInt("page"), it.optString("label")) } }
            .sortedBy { it.page }
    } catch (e: Exception) {
        emptyList()
    }

    fun setBookmarks(path: String, marks: List<PdfBookmark>) {
        val array = JSONArray()
        marks.distinctBy { it.page }.sortedBy { it.page }.forEach { array.put(JSONObject().put("page", it.page).put("label", it.label)) }
        prefs.edit().putString("marks:$path", array.toString()).apply()
    }

    /** Bookmarks, last page and so on follow a renamed file. */
    fun moved(from: String, to: String) {
        val edit = prefs.edit()
        prefs.getString("marks:$from", null)?.let { edit.putString("marks:$to", it).remove("marks:$from") }
        if (prefs.contains("page:$from")) edit.putInt("page:$to", prefs.getInt("page:$from", 0)).remove("page:$from")
        edit.apply()
    }

    /** 0 normal, 1 night, 2 sepia. */
    var theme: Int
        get() = prefs.getInt("theme", THEME_NORMAL)
        set(value) { prefs.edit().putInt("theme", value).apply() }

    /** Auto scroll speed, 1 (slow) to 10. */
    var scrollSpeed: Int
        get() = prefs.getInt("scroll_speed", 3)
        set(value) { prefs.edit().putInt("scroll_speed", value).apply() }

    /** Pages side by side, one per swipe, instead of a vertical scroll. */
    var horizontal: Boolean
        get() = prefs.getBoolean("horizontal", false)
        set(value) { prefs.edit().putBoolean("horizontal", value).apply() }

    /** Text size of the text view, in sp. */
    var reflowSize: Float
        get() = prefs.getFloat("reflow_size", 16f)
        set(value) { prefs.edit().putFloat("reflow_size", value).apply() }

    companion object {
        const val THEME_NORMAL = 0
        const val THEME_NIGHT = 1
        const val THEME_SEPIA = 2
    }
}
