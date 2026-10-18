package com.af010.creator.docreader.ui.main.tools.merge

import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.tools.FilesToolActivity
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.sizeText
import org.koin.android.ext.android.inject
import java.io.File

class MergeActivity : FilesToolActivity() {
    override val tool = Tool.Merge
    override val titleRes = R.string.merge_pdf
    override val nameLabelRes = R.string.fo_name_new_file
    override val addFilesRes = R.string.fo_add_another_file
    override val actionIcon = R.drawable.ic_fo_merge
    override val stepDetail = R.string.fo_step_set_order
    override val extension = "pdf"
    override val minimumFiles = 2
    override val ordered = true

    private val toolbox: PdfToolbox by inject()

    /** Page counts by path, filled in as each file is read. */
    private val pageCounts = HashMap<String, Int>()

    override fun metaOf(file: File): String {
        val pages = pageCounts[file.path] ?: return file.sizeText(this)
        return getString(R.string.document_meta, file.sizeText(this), pageCountText(pages))
    }

    override fun defaultName(first: File): String = "${first.nameWithoutExtension}-merged"

    override fun actionText(files: List<File>): String =
        resources.getQuantityString(R.plurals.fo_merge_files, files.size, files.size)

    override fun totalText(files: List<File>): String? {
        if (files.any { it.path !in pageCounts }) return null
        val pages = files.sumOf { pageCounts[it.path] ?: 0 }
        return resources.getQuantityString(R.plurals.fo_pages_total, pages, pages)
    }

    override fun request(files: List<File>, name: String) =
        ToolRequest.Merge(files.map { it.path }, name)

    override fun onFilesChanged(files: List<File>) {
        files.filter { it.path !in pageCounts }.forEach { file ->
            launchMain {
                pageCounts[file.path] = runCatching { toolbox.pageCount(file) }.getOrDefault(0)
                refreshRows()
            }
        }
    }
}
