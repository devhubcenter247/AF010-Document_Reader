package com.af010.creator.docreader.model

import android.os.Parcelable
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import kotlinx.parcelize.Parcelize

/** [keeps] is a rough share of the size left afterwards, in percent, for the estimate on screen. */
enum class CompressLevel(@StringRes val title: Int, @StringRes val desc: Int, val keeps: Int) {
    Low(R.string.compress_low, R.string.compress_low_desc, 80),
    Medium(R.string.compress_medium, R.string.compress_medium_desc, 45),
    High(R.string.compress_high, R.string.compress_high_desc, 25)
}

/** Sizes are in PDF points (1/72 inch); [Fit] makes each page as tall as its image needs. */
enum class PageSize(@StringRes val title: Int, val width: Float, val height: Float) {
    A4(R.string.page_size_a4, 595f, 842f),
    Letter(R.string.page_size_letter, 612f, 792f),
    Fit(R.string.page_size_fit, 595f, 0f)
}

enum class PageOrientation(@StringRes val title: Int) {
    Portrait(R.string.orientation_portrait),
    Landscape(R.string.orientation_landscape),
    Auto(R.string.orientation_auto)
}

enum class PageMargin(@StringRes val title: Int, val points: Float) {
    None(R.string.margin_none, 0f),
    Small(R.string.margin_small, 18f),
    Large(R.string.margin_large, 36f)
}

@Parcelize
data class PageSetup(
    val size: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.Portrait,
    val margin: PageMargin = PageMargin.Small
) : Parcelable

/** A box to black out on [page] (zero based); the edges are fractions 0..1 of the page as shown. */
@Parcelize
data class RedactArea(
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) : Parcelable

/** Everything a tool needs to run; built by the tool's setup screen and executed by ToolRunner. */
sealed interface ToolRequest : Parcelable {
    val tool: Tool

    @Parcelize
    data class Merge(val paths: List<String>, val name: String) : ToolRequest {
        override val tool: Tool get() = Tool.Merge
    }

    /** Each entry of [parts] lists the zero-based pages of one output file. */
    @Parcelize
    data class Split(val path: String, val parts: List<List<Int>>) : ToolRequest {
        override val tool: Tool get() = Tool.Split
    }

    /** Saves [pages] (zero based, every page when empty) as JPG pictures. */
    @Parcelize
    data class PdfToImages(val path: String, val pages: List<Int> = emptyList()) : ToolRequest {
        override val tool: Tool get() = Tool.PdfToImages
    }

    @Parcelize
    data class Compress(val path: String, val level: CompressLevel) : ToolRequest {
        override val tool: Tool get() = Tool.Compress
    }

    @Parcelize
    data class Protect(val path: String, val password: String) : ToolRequest {
        override val tool: Tool get() = Tool.Protect
    }

    /** [color] carries the opacity in its alpha; [angle] is counter-clockwise, in degrees. */
    @Parcelize
    data class Watermark(
        val path: String,
        val text: String,
        val color: Int,
        val angle: Float
    ) : ToolRequest {
        override val tool: Tool get() = Tool.Watermark
    }

    @Parcelize
    data class Redact(val path: String, val areas: List<RedactArea>) : ToolRequest {
        override val tool: Tool get() = Tool.Redact
    }

    @Parcelize
    data class Ocr(val path: String) : ToolRequest {
        override val tool: Tool get() = Tool.Ocr
    }

    /** [sourceLanguage] null = detect it from the text. */
    @Parcelize
    data class Translate(
        val path: String,
        val pages: List<Int>,
        val sourceLanguage: String?,
        val targetLanguage: String
    ) : ToolRequest {
        override val tool: Tool get() = Tool.Translate
    }

    @Parcelize
    data class Zip(val paths: List<String>, val name: String) : ToolRequest {
        override val tool: Tool get() = Tool.Zip
    }

    /** Also used to save a scan ([fromScan]). */
    @Parcelize
    data class ImagesToPdf(
        val paths: List<String>,
        val name: String,
        val setup: PageSetup,
        val fromScan: Boolean = false
    ) : ToolRequest {
        override val tool: Tool get() = if (fromScan) Tool.Scan else Tool.ImagesToPdf
    }

    /**
     * The Word, Excel or PowerPoint file at [path] as a PDF named [name]. The pages are drawn from
     * the document open on screen, which hands ToolRunner an [com.af010.creator.docreader.pdf.OfficeWriter] first.
     */
    @Parcelize
    data class OfficeToPdf(val path: String, val name: String) : ToolRequest {
        override val tool: Tool get() = Tool.OfficeToPdf
    }
}

/** [outputs] are the files a tool wrote; [sourceSize] is the input size in bytes when comparing makes sense. */
@Parcelize
data class ToolResult(
    val tool: Tool,
    val outputs: List<String>,
    val sourceSize: Long = 0L
) : Parcelable
