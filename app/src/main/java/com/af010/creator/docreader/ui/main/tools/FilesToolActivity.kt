package com.af010.creator.docreader.ui.main.tools

import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityToolFilesBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.common.RowDivider
import com.af010.creator.docreader.ui.main.tools.pick.PickFileActivity
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.clickSingle
import java.io.File

/**
 * Setup screen of a tool that turns a list of files into one new file (Merge, ZIP): the list,
 * a button to add more, the name of the result, and the action button.
 */
abstract class FilesToolActivity :
    BaseAppActivity<ActivityToolFilesBinding>(ActivityToolFilesBinding::inflate) {

    abstract val tool: Tool
    @get:StringRes abstract val titleRes: Int
    @get:StringRes abstract val nameLabelRes: Int
    @get:StringRes abstract val addFilesRes: Int
    @get:DrawableRes abstract val actionIcon: Int
    abstract val extension: String
    abstract val minimumFiles: Int

    /**
     * Merge lists its files in order (numbers, move up, drag, swipe away); ZIP has no order and
     * shows a picture and a remove button instead.
     */
    open val ordered: Boolean = false

    /** The detail of "Step 2 of 3 · …", or null for a screen without the steps. */
    @get:StringRes open val stepDetail: Int? = null

    /** The name suggested for the result. */
    open fun defaultName(first: File): String = first.nameWithoutExtension

    /** The line under a file's name. */
    abstract fun metaOf(file: File): String
    abstract fun actionText(files: List<File>): String
    abstract fun request(files: List<File>, name: String): ToolRequest

    /** The line under the title, refreshed with the list; null for none. */
    open fun summary(files: List<File>): String? = null

    /** The page total next to the order rule (Merge). */
    open fun totalText(files: List<File>): String? = null

    /** Called when the list changes, e.g. to load details shown by [metaOf]. */
    open fun onFilesChanged(files: List<File>) {}

    protected var files: List<File> = emptyList()
        private set

    private val fileAdapter by lazy { FileRowAdapter(ordered) }

    private val addLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val added = result.data?.getStringArrayListExtra(EXTRA_PATHS).orEmpty().map(::File)
        if (added.isNotEmpty()) setFiles((files + added).distinctBy { it.path })
    }

    override fun backPressed() = finish()

    override fun initialize() {
        files = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().map(::File)
    }

    override fun ActivityToolFilesBinding.setData() {
        topBar.show(tool, titleRes)
        stepDetail?.let(steps::showSetupStep)
        steps.root.isVisible = stepDetail != null
        hero.root.isVisible = !ordered
        layoutOrder.isVisible = ordered
        btnAddFiles.setText(addFilesRes)
        tvNameLabel.setText(nameLabelRes)
        edtName.setText(files.firstOrNull()?.let(::defaultName).orEmpty())
        tvExtension.text = getString(R.string.file_extension, extension)
        action.btnAction.setIconResource(actionIcon)
        rcvFiles.adapter = fileAdapter
        // Merge rows lead with their number, ZIP rows with the type tile.
        rcvFiles.addItemDecoration(RowDivider(if (ordered) com.intuit.sdp.R.dimen._44sdp else com.intuit.sdp.R.dimen._50sdp))
        if (ordered) ItemTouchHelper(dragCallback).attachToRecyclerView(rcvFiles)
        setFiles(files)
    }

    override fun ActivityToolFilesBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        fileAdapter
            .onRemoveClick { row -> setFiles(files - row.file) }
            .onMoveUpClick { position ->
                if (position > 0) setFiles(files.toMutableList().apply { add(position - 1, removeAt(position)) })
            }
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

    /** Rebuilds the rows and the texts around them; call it when what [metaOf] and co. return changes. */
    protected fun refreshRows() {
        fileAdapter.submitList(files.map { FileRow(it, metaOf(it)) })
        binding.render()
    }

    private fun ActivityToolFilesBinding.render() {
        val name = edtName.text.toString().trim()
        val ready = files.size >= minimumFiles && name.isNotEmpty() && !name.contains('/')
        rcvFiles.isVisible = files.isNotEmpty()
        val summary = summary(files)
        topBar.tvSubtitle.text = summary
        topBar.tvSubtitle.isVisible = summary != null
        tvTotal.text = totalText(files)
        tvTotal.isVisible = !tvTotal.text.isNullOrEmpty()
        action.btnAction.text = actionText(files)
        action.btnAction.isEnabled = ready
    }

    /** Merge: drag a row by its handle (long press) to reorder, swipe it away to drop it. */
    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START or ItemTouchHelper.END
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

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            files.getOrNull(viewHolder.bindingAdapterPosition)?.let { setFiles(files - it) }
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            // The row being dragged gets the picked pill, so it covers the rows it passes over.
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) viewHolder?.itemView?.isActivated = true
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.isActivated = false
            // The numbers follow the new order.
            refreshRows()
        }
    }
}
