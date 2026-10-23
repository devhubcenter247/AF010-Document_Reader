package com.af010.creator.docreader.ui.main.tools.result

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.media.MediaScannerConnection
import androidx.core.view.isVisible
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.ui.dialog.RenameDialog
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.softColorRes
import java.util.Locale
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySuccessBinding
import com.af010.creator.docreader.databinding.ItemNextToolBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolResult
import com.af010.creator.docreader.ui.common.showToolTile
import com.af010.creator.docreader.ui.main.reader.office.OfficeActivity
import com.af010.creator.docreader.ui.main.reader.pdf.PdfReaderActivity
import com.af010.creator.docreader.ui.main.tools.isImage
import com.af010.creator.docreader.ui.main.tools.openTool
import com.bumptech.glide.Glide
import com.d010.pdf.reader.base.openFileWith
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.clickSingle
import java.io.File

/** "Saved!": the file(s) a tool wrote, with open / share and the tools to continue with. */
class SuccessActivity : BaseAppActivity<ActivitySuccessBinding>(ActivitySuccessBinding::inflate) {

    private val result: ToolResult? by lazy {
        IntentCompat.getParcelableExtra(intent, EXTRA_RESULT, ToolResult::class.java)
    }
    /** The written files; a rename here replaces the first one. */
    private var outputs: List<File> = emptyList()

    private val nextToolAdapter by lazy { NextToolAdapter() }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        outputs = result?.outputs.orEmpty().map(::File)
    }

    override fun ActivitySuccessBinding.setData() {
        if (outputs.isEmpty()) return finish()
        showFile()
        tvMessage.text = message()
        val isPdf = outputs.first().extension.equals("pdf", ignoreCase = true)
        layoutNextTools.isVisible = isPdf
        rcvNextTools.adapter = nextToolAdapter
        nextToolAdapter.submitList(NEXT_TOOLS.filter { it != result?.tool }.take(3))
    }

    override fun ActivitySuccessBinding.onClick() {
        btnClose.clickSingle { finish() }
        btnOpen.clickSingle {
            val first = outputs.firstOrNull() ?: return@clickSingle
            when {
                first.extension.equals("pdf", ignoreCase = true) -> PdfReaderActivity.start(this@SuccessActivity, first.path)
                OfficeActivity.canOpen(first.name) -> OfficeActivity.start(this@SuccessActivity, first.path)
                !openFileWith(first.path) -> toast(R.string.file_open_with_failed)
            }
        }
        btnShare.clickSingle { share() }
        btnRename.clickSingle { rename() }
        nextToolAdapter.onToolClick { tool ->
            openTool(tool, outputs.take(1).map { it.path })
        }
    }

    private fun ActivitySuccessBinding.showFile() {
        val first = outputs.first()
        val sizeText = Formatter.formatShortFileSize(this@SuccessActivity, outputs.sumOf { it.length() })
        // A picture shows itself rather than a tile.
        ivThumb.isVisible = first.isImage
        if (first.isImage) Glide.with(this@SuccessActivity).load(first).into(ivThumb)
        val type = AppUtils.documentTypeOf(first)
        tvType.text = first.extension.uppercase(Locale.ROOT).take(4)
        tvType.backgroundTintList = ColorStateList.valueOf(getColor(type?.softColorRes ?: R.color.color_file_zip_bg))
        tvType.setTextColor(getColor(type?.colorRes ?: R.color.color_file_zip))
        tvName.text = if (outputs.size > 1) resources.getQuantityString(R.plurals.file_count, outputs.size, outputs.size) else first.name
        tvName.isSelected = true
        tvMeta.text = getString(R.string.document_meta, sizeText, getString(R.string.fo_saved_place))
        // Several files cannot be renamed as one.
        btnRename.isVisible = outputs.size == 1
    }

    private fun message(): String {
        val sourceSize = result?.sourceSize ?: 0L
        val total = outputs.sumOf { it.length() }
        return when (result?.tool) {
            Tool.Merge -> getString(R.string.fo_done_merge)
            Tool.Split -> resources.getQuantityString(R.plurals.fo_done_split, outputs.size, outputs.size)
            Tool.Compress -> if (sourceSize > total && sourceSize > 0) {
                getString(R.string.fo_done_compress, ((sourceSize - total) * 100 / sourceSize).toInt())
            } else getString(R.string.fo_done_generic)
            Tool.Protect -> getString(R.string.fo_done_protect)
            Tool.Zip -> getString(R.string.fo_done_zip)
            Tool.Ocr -> getString(R.string.fo_done_ocr)
            else -> getString(R.string.fo_done_generic)
        }
    }

    /** Renames the one new file in place and tells MediaStore, so the lists pick the new name up. */
    private fun rename() {
        val file = outputs.singleOrNull() ?: return
        RenameDialog(this, RecentDocument(path = file.path)) { newName ->
            val target = File(file.parentFile, "$newName.${file.extension}")
            if (target.exists() || !file.renameTo(target)) {
                toast(R.string.file_rename_failed)
                return@RenameDialog
            }
            outputs = listOf(target)
            MediaScannerConnection.scanFile(this, arrayOf(file.path, target.path), null, null)
            binding.showFile()
        }.show()
    }

    private fun share() {
        val uris = ArrayList(outputs.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        }.onFailure { toast(R.string.file_share_failed) }
    }

    private class NextToolAdapter : BaseAdapter<Tool, ItemNextToolBinding>() {
        override fun createBinding(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ) = ItemNextToolBinding.inflate(inflater, parent, false)

        private var onItemClick: ((Tool) -> Unit) = {}
        fun onToolClick(listener: (Tool) -> Unit) = apply {
            onItemClick = listener
        }

        override fun ItemNextToolBinding.bind(item: Tool, position: Int) {
            ivIcon.showToolTile(item)
            tvTitle.setText(item.title)
            tvTitle.isSelected = true
            card.setOnClickListener { onItemClick.invoke(item) }
        }
    }

    companion object {
        private const val EXTRA_RESULT = "EXTRA_RESULT"

        /** Offered after a PDF is made; the tool that just ran is left out. */
        private val NEXT_TOOLS = listOf(Tool.Compress, Tool.Sign, Tool.Protect, Tool.Merge)

        fun start(context: Context, result: ToolResult) {
            context.startActivity(
                Intent(context, SuccessActivity::class.java).putExtra(EXTRA_RESULT, result)
            )
        }
    }
}
