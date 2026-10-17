package com.af010.creator.docreader.ui.main.tools.zip

import android.text.format.Formatter
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.tools.FilesToolActivity
import com.af010.creator.docreader.ui.main.tools.sizeText
import java.io.File

class ZipActivity : FilesToolActivity() {
    override val tool = Tool.Zip
    override val titleRes = R.string.create_zip
    override val subtitleRes = R.string.zip_subtitle
    override val nameLabelRes = R.string.archive_name
    override val extension = "zip"
    override val minimumFiles = 1

    override fun metaOf(file: File): String = file.sizeText(this)

    override fun actionText(files: List<File>): String = getString(
        R.string.zip_action,
        resources.getQuantityString(R.plurals.file_count, files.size, files.size),
        Formatter.formatShortFileSize(this, files.sumOf { it.length() })
    )

    override fun request(files: List<File>, name: String) =
        ToolRequest.Zip(files.map { it.path }, name)
}
