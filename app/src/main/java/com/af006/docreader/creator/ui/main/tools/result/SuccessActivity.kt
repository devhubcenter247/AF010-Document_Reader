package com.af010.creator.docreader.ui.main.tools.result

import android.content.Context
import android.content.Intent
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
import com.af010.creator.docreader.ui.main.tools.typeIcon
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.d010.pdf.reader.base.openFileWith
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import com.ui.baselib.utils.dpToPx
import java.io.File

/** "Saved!": the file(s) a tool wrote, with open / share and the tools to continue with. */
class SuccessActivity : BaseAppActivity<ActivitySuccessBinding>(ActivitySuccessBinding::inflate) {

    private val result: ToolResult? by lazy {
        IntentCompat.getParcelableExtra(intent, EXTRA_RESULT, ToolResult::class.java)
    }
    private val outputs: List<File> get() = result?.outputs.orEmpty().map(::File)

    private val nextToolAdapter by lazy { NextToolAdapter() }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivitySuccessBinding.setData() {
        val first = outputs.firstOrNull() ?: return finish()
        val totalSize = outputs.sumOf { it.length() }
        val sizeText = Formatter.formatShortFileSize(this@SuccessActivity, totalSize)
        // A picture shows itself rather than the generic image tile.
        if (first.isImage) {
            Glide.with(this@SuccessActivity).load(first)
                .transform(CenterCrop(), RoundedCorners(dpToPx(6)))
                .into(ivType)
        } else {
            ivType.setImageResource(first.typeIcon)
        }
        tvName.text = first.name
        tvMeta.text = if (outputs.size > 1) {
            getString(
                R.string.document_meta,
                resources.getQuantityString(R.plurals.file_count, outputs.size, outputs.size),
                sizeText
            )
        } else {
            sizeText
        }

        // Compress: how much smaller the file became.
        val sourceSize = result?.sourceSize ?: 0L
        val saved = if (sourceSize > 0) ((sourceSize - totalSize) * 100 / sourceSize).toInt() else 0
        tvSaving.visibleOrGone(saved > 0)
        if (saved > 0) {
            tvMeta.text = getString(R.string.document_meta, sizeText, getString(R.string.percent_smaller, saved))
            tvSaving.text = getString(R.string.percent_saved, saved)
        }

        val isPdf = first.extension.equals("pdf", ignoreCase = true)
        layoutNextTools.visibleOrGone(isPdf)
        rcvNextTools.adapter = nextToolAdapter
        nextToolAdapter.submitList(listOf(Tool.Sign, Tool.Annotate, Tool.Protect, Tool.Merge))
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
        nextToolAdapter.onToolClick { tool ->
            openTool(tool, outputs.take(1).map { it.path })
        }
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
            root.setOnClickListener { onItemClick.invoke(item) }
        }
    }

    companion object {
        private const val EXTRA_RESULT = "EXTRA_RESULT"

        fun start(context: Context, result: ToolResult) {
            context.startActivity(
                Intent(context, SuccessActivity::class.java).putExtra(EXTRA_RESULT, result)
            )
        }
    }
}
