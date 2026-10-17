package com.af010.creator.docreader.ui.main.scan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.af010.creator.docreader.ui.main.scan.detection.ScanFilter
import java.io.File
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

    val pageList: List<ScanPage> get() = _pages.value.orEmpty()

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

    private fun ScanPage.deleteFiles(keep: ScanPage? = null) {
        val kept = keep?.let { setOf(it.original, it.cropped, it.output) }.orEmpty()
        listOf(original, cropped, output).distinct()
            .filter { it !in kept }
            .forEach { it.delete() }
    }
}
