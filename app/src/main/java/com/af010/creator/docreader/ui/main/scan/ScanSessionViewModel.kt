package com.af010.creator.docreader.ui.main.scan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.af010.creator.docreader.ui.main.scan.detection.ScanFilter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.opencv.core.Point

/**
 * One scanned page: [original] photo, [cropped] (perspective-corrected, unfiltered) and [output],
 * which is [cropped] with [filter] applied. Filters always re-run from [cropped] so they never stack.
 */
data class ScanPage(
    val original: File,
    val cropped: File,
    val output: File = cropped,
    val filter: ScanFilter = ScanFilter.Default,
)

data class PendingCrop(
    val original: File,
    val width: Int,
    val height: Int,
    val corners: List<Point>,
)

class ScanSessionViewModel : ViewModel() {
    private val _pages = MutableLiveData<List<ScanPage>>(emptyList())
    val pages: LiveData<List<ScanPage>> = _pages

    private val _selectedIndex = MutableLiveData(0)
    val selectedIndex: LiveData<Int> = _selectedIndex

    var pendingCrop: PendingCrop? = null

    var retakeIndex: Int? = null

    /** The name the PDF will get; shown on the filters screen, which can change it. */
    var name: String = "Scan " + SimpleDateFormat("d MMM HH.mm", Locale.getDefault()).format(Date())

    val pageList: List<ScanPage> get() = _pages.value.orEmpty()

    /**
     * The photos an editing session started with ([ScanActivity.isEditing]). Their files belong to the
     * screen that opened the editor, so they are not deleted until it takes the edits back.
     */
    var editStart: List<ScanPage> = emptyList()
        private set
    private val startFiles: Set<File> get() = editStart.flatMap { it.files }.toSet()

    val hasEdits: Boolean get() = pageList != editStart

    fun startEditing(pages: List<ScanPage>, index: Int) {
        editStart = pages
        _pages.value = pages
        select(index)
    }

    /** Keeps the edits: the starting files no page uses any more can go. */
    fun keepEdits() {
        val used = pageList.flatMap { it.files }.toSet()
        (startFiles - used).forEach { it.delete() }
    }

    /** Drops the edits: deletes what the session wrote and leaves the starting photos as they were. */
    fun discardEdits() {
        val start = startFiles
        pageList.flatMap { it.files }.filter { it !in start }.forEach { it.delete() }
        _selectedIndex.value = (_selectedIndex.value ?: 0).coerceAtMost(editStart.lastIndex).coerceAtLeast(0)
        _pages.value = editStart
    }

    fun select(index: Int) {
        if (index in pageList.indices) _selectedIndex.value = index
    }

    fun addOrReplace(page: ScanPage) {
        val pages = pageList.toMutableList()
        val index = retakeIndex?.takeIf { it in pages.indices }
        retakeIndex = null
        if (index != null) {
            pages[index].deleteFiles(keep = page)
            pages[index] = page
        } else {
            pages += page
        }
        _pages.value = pages
        _selectedIndex.value = index ?: pages.lastIndex
    }

    /** Replaces the page at [index], deleting the old page's files that [page] no longer uses. */
    fun updatePage(index: Int, page: ScanPage) {
        val pages = pageList.toMutableList()
        val old = pages.getOrNull(index) ?: return
        old.deleteFiles(keep = page)
        pages[index] = page
        _pages.value = pages
    }

    fun delete(index: Int) {
        val pages = pageList.toMutableList()
        if (index !in pages.indices) return
        pages.removeAt(index).deleteFiles()
        _pages.value = pages
        _selectedIndex.value = index.coerceAtMost(pages.lastIndex).coerceAtLeast(0)
    }

    fun discardPending() {
        pendingCrop?.original?.delete()
        pendingCrop = null
    }

    fun clear() {
        discardPending()
        pageList.forEach { it.deleteFiles() }
        _pages.value = emptyList()
        _selectedIndex.value = 0
        retakeIndex = null
    }

    private val ScanPage.files: List<File> get() = listOf(original, cropped, output).distinct()

    private fun ScanPage.deleteFiles(keep: ScanPage? = null) {
        val kept = keep?.files.orEmpty().toSet() + startFiles
        files.filter { it !in kept }.forEach { it.delete() }
    }
}
