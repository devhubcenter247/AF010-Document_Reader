package com.af010.creator.docreader.model

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class PageViewType {
    PageByPage, Thumbnail
}

/** One rendered page (or slide); [index] is always 0-based. */
data class DocumentPage(
    val index: Int,
    val bitmap: Bitmap? = null
)

/** A line of text matching a PDF search; [page] is 1-based. */
@Parcelize
data class ContentWithPage(val page: Int, val content: String) : Parcelable
