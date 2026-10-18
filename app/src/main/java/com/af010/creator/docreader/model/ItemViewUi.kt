package com.af010.creator.docreader.model

data class FavoriteUi(
  val document: FavoriteDocument,
  val isFavorite: Boolean
)
data class RecentUi(
  val document: RecentDocument,
  val isFavorite: Boolean
) {
  /**
   * Whether a row would look the same. Not `==`: every scan stamps its documents with a new
   * `lastTimeView`, which would make each row count as changed.
   */
  fun sameContent(other: RecentUi): Boolean =
    isFavorite == other.isFavorite && document.path == other.document.path &&
      document.lastModified == other.document.lastModified &&
      document.size == other.document.size && document.type == other.document.type
}