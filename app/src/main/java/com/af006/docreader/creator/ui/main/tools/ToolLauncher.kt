package com.af010.creator.docreader.ui.main.tools

import android.app.Activity
import android.content.Intent
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.ui.main.reader.pdf.PdfReaderActivity
import com.af010.creator.docreader.ui.main.reader.pdf.organize.PdfOrganizeActivity
import com.af010.creator.docreader.ui.main.scan.ScanActivity
import com.af010.creator.docreader.ui.main.tools.compress.CompressActivity
import com.af010.creator.docreader.ui.main.tools.images.ImageToPdfActivity
import com.af010.creator.docreader.ui.main.tools.merge.MergeActivity
import com.af010.creator.docreader.ui.main.tools.pick.PickFileActivity
import com.af010.creator.docreader.ui.main.tools.protect.ProtectActivity
import com.af010.creator.docreader.ui.main.tools.redact.RedactActivity
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.split.SplitActivity
import com.af010.creator.docreader.ui.main.tools.translate.TranslateActivity
import com.af010.creator.docreader.ui.main.tools.watermark.WatermarkActivity
import com.af010.creator.docreader.ui.main.tools.zip.ZipActivity

/** The screen where a tool is set up once its files are known; null for tools that have none. */
private val Tool.setupActivity: Class<out Activity>?
    get() = when (this) {
        Tool.Merge -> MergeActivity::class.java
        Tool.Split -> SplitActivity::class.java
        Tool.Compress -> CompressActivity::class.java
        Tool.Protect -> ProtectActivity::class.java
        Tool.Watermark -> WatermarkActivity::class.java
        Tool.Redact -> RedactActivity::class.java
        Tool.Translate -> TranslateActivity::class.java
        Tool.Zip -> ZipActivity::class.java
        else -> null
    }

/**
 * Single entry point for every tool shortcut (Files chips, Create sheet, Tools tab, file
 * actions). With no [paths] the user first picks the files; with [paths] the tool opens on them.
 */
fun Activity.openTool(tool: Tool, paths: List<String> = emptyList()) {
    val setup = tool.setupActivity
    when {
        tool == Tool.Scan -> startActivity(Intent(this, ScanActivity::class.java))
        tool == Tool.ImagesToPdf -> startActivity(Intent(this, ImageToPdfActivity::class.java))
        paths.isEmpty() -> PickFileActivity.start(this, tool)
        // The Office screen draws the pages: it opens the file and converts it there.
        tool == Tool.OfficeToPdf -> OfficeActivity.start(this, paths.first(), toPdf = true)
        // Edited in the reader, which opens straight in the tool's mode.
        tool == Tool.Sign || tool == Tool.Annotate || tool == Tool.FillForm || tool == Tool.AddText ->
            PdfReaderActivity.start(this, paths.first(), tool)
        tool == Tool.Organize -> startActivity(PdfOrganizeActivity.intent(this, paths.first()))
        tool == Tool.PdfToImages -> ProcessingActivity.start(this, ToolRequest.PdfToImages(paths.first()))
        setup == null -> ProcessingActivity.start(this, ToolRequest.Ocr(paths.first()))
        else -> startActivity(Intent(this, setup).putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths)))
    }
}
