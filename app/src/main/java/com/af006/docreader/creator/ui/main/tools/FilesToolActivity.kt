package com.af010.creator.docreader.ui.main.tools

import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityToolFilesBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.tools.pick.PickFileActivity
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import java.io.File

/**
 * Setup screen of a tool that turns a list of files into one new file (Merge, ZIP): the list
 * with remove / add, the name of the result, and the action button.
 */
abstract class FilesToolActivity :
    BaseAppActivity<ActivityToolFilesBinding>(ActivityToolFilesBinding::inflate) {

    abstract val tool: Tool
    @get:StringRes abstract val titleRes: Int
    @get:StringRes abstract val subtitleRes: Int
    @get:StringRes abstract val nameLabelRes: Int
    abstract val extension: String
    abstract val minimumFiles: Int

    /** The name suggested for the result. */
    open fun defaultName(first: File): String = first.nameWithoutExtension

    /** Whether the files can be dragged into another order. */
    open val reorderable: Boolean = false

    /** The line under a file's name. */
    abstract fun metaOf(file: File): String
    abstract fun actionText(files: List<File>): String
    abstract fun request(files: List<File>, name: String): ToolRequest

    /** Called when the list changes, e.g. to load details shown by [metaOf]. */
    open fun onFilesChanged(files: List<File>) {}

    protected var files: List<File> = emptyList()
        private set

    private val fileAdapter by lazy { FileRowAdapter(reorderable) }

    private val addLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val added = result.data?.getStringArrayListExtra(EXTRA_PATHS).orEmpty().map(::File)
        if (added.isNotEmpty()) setFiles((files + added).distinctBy { it.path })
    }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {
        files = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().map(::File)
    }

    override fun ActivityToolFilesBinding.setData() {
        header.show(tool, titleRes, subtitleRes)
        tvNameLabel.setText(nameLabelRes)
        edtName.setHint(nameLabelRes)
        edtName.setText(files.firstOrNull()?.let(::defaultName).orEmpty())
        tvExtension.text = getString(R.string.file_extension, extension)
        rcvFiles.adapter = fileAdapter
        if (reorderable) ItemTouchHelper(dragCallback).attachToRecyclerView(rcvFiles)
        setFiles(files)
    }

    override fun ActivityToolFilesBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        fileAdapter.onRemoveClick { row -> setFiles(files - row.file) }
        btnAddFiles.clickSingle {
            addLauncher.launch(PickFileActivity.intent(this@FilesToolActivity, tool, forResult = true))
        }
        edtName.afterTextChanged { render() }
        action.btnAction.clickSingle {
            val name = edtName.text.toString().trim()
            ProcessingActivity.start(this@FilesToolActivity, request(files, name))
            finish()
        }
    }

    private fun setFiles(value: List<File>) {
        files = value
        refreshRows()
        onFilesChanged(value)
    }

    /** Rebuilds the rows and the button; call it when what [metaOf] or [actionText] return changes. */
    protected fun refreshRows() {
        fileAdapter.submitList(files.map { FileRow(it, metaOf(it)) })
        binding.render()
    }

    private fun ActivityToolFilesBinding.render() {
        val name = edtName.text.toString().trim()
        val ready = files.size >= minimumFiles && name.isNotEmpty() && !name.contains('/')
        rcvFiles.visibleOrGone(files.isNotEmpty())
        action.btnAction.text = actionText(files)
        action.btnAction.isEnabled = ready
        action.btnAction.alpha = if (ready) 1f else 0.5f
    }

    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            fileAdapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            files = fileAdapter.currentItems.map { it.file }
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }
}
