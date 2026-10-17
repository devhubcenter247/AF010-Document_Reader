package com.af010.creator.docreader.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.RoomWarnings
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.firebase.encoders.annotations.Encodable
import kotlinx.parcelize.Parcelize
import kotlin.Long

class Converters {
    @TypeConverter
    fun fromDocumentType(varue: DocumentType): String = varue.name
    @TypeConverter
    fun toDocumentType(varue: String): DocumentType = DocumentType.valueOf(varue)
}

enum class DocumentType {
    Doc, Pdf, Excel, Ppt, Txt, Image
}

@SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
@Entity(tableName = "RecentDocument")
@TypeConverters(Converters::class)
@Parcelize
data class RecentDocument(
    @PrimaryKey
    @ColumnInfo(name = "mediaId")
    val mediaId: Long = 0L,
    @ColumnInfo(name = "path")
    var path: String = "",
    @ColumnInfo(name = "lastModified")
    var lastModified: Long = 0L,
    @ColumnInfo(name = "lastTimeView")
    var lastTimeView: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "size")
    var size: Long = 0L,
    @ColumnInfo(name = "type")
    var type: DocumentType = DocumentType.Doc
) : Parcelable {
    @get:Encodable.Ignore
    var isSelected: Boolean = false

    fun isDefault(): Boolean =
        path.isEmpty() && lastModified == 0L && size == 0L

    fun clone(): RecentDocument =
        copy().also {
            it.isSelected = this.isSelected
            it.type = this.type
        }
}
@Entity(tableName = "FavoriteDocument")
@TypeConverters(Converters::class)
@Parcelize
data class FavoriteDocument(
    @PrimaryKey
    val mediaId: Long = 0L,
    var path: String = "",
    var lastModified: Long = 0L,
    var size: Long = 0L,
    var type: DocumentType = DocumentType.Doc,
) : Parcelable {
    @Ignore
    var isSelected: Boolean = false

    fun isDefault(): Boolean =
        path.isEmpty() && lastModified == 0L && size == 0L

    fun clone(): FavoriteDocument =
        copy().also {
            it.isSelected = this.isSelected
            it.type = this.type
        }
}