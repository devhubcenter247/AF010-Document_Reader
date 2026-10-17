package com.af010.creator.docreader.ui.main.tools.images

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityImageToPdfBinding
import com.af010.creator.docreader.databinding.ItemImagePageBinding
import com.af010.creator.docreader.model.PageMargin
import com.af010.creator.docreader.model.PageOrientation
import com.af010.creator.docreader.model.PageSetup
import com.af010.creator.docreader.model.PageSize
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.bumptech.glide.Glide
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pick photos, put them in order, choose the page layout, and turn them into one PDF. */
class ImageToPdfActivity :
    BaseAppActivity<ActivityImageToPdfBinding>(ActivityImageToPdfBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private var images: List<File> = emptyList()
    private var setup = PageSetup()

    private val imageAdapter by lazy { ImageAdapter() }

    private val pickLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) importImages(uris) else if (images.isEmpty()) finish()
    }

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityImageToPdfBinding.setData() {
        toolbar.root.updatePadding(top = statusBarHeight)
        toolbar.tvTitle.setText(R.string.images_to_pdf)
        toolbar.tvSubtitle.visible()
        rcvImages.adapter = imageAdapter
        ItemTouchHelper(dragCallback).attachToRecyclerView(rcvImages)
        render()
        pickImages()
    }

    override fun ActivityImageToPdfBinding.onClick() {
        toolbar.btnBack.clickSingle { finish() }
        imageAdapter
            .onAddClick { pickImages() }
            .onRemoveClick { file ->
                images = images - file
                render()
            }

        // Each option steps to its next value on tap.
        btnPageSize.click {
            setup = setup.copy(size = PageSize.entries.next(setup.size))
            render()
        }
        btnOrientation.click {
            setup = setup.copy(orientation = PageOrientation.entries.next(setup.orientation))
            render()
        }
        btnMargins.click {
            setup = setup.copy(margin = PageMargin.entries.next(setup.margin))
            render()
        }
        action.btnAction.clickSingle {
            val name = "PDF_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            ProcessingActivity.start(
                this@ImageToPdfActivity,
                ToolRequest.ImagesToPdf(images.map { it.path }, name, setup)
            )
            finish()
        }
    }

    private fun pickImages() {
        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Copies the picked photos into the cache: the PDF writer reads files, not content Uris. */
    private fun importImages(uris: List<Uri>) {
        launchIO {
            val directory = File(cacheDir, IMPORT_DIR).apply { mkdirs() }
            val copied = uris.mapIndexedNotNull { index, uri ->
                runCatching {
                    val target = File(directory, "image_${System.currentTimeMillis()}_$index.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    target.takeIf { it.length() > 0 }
                }.getOrNull()
            }
            withMain {
                images = images + copied
                render()
            }
        }
    }

    private fun render() = with(binding) {
        // The trailing null is the "Add" tile.
        imageAdapter.submitList(images + null)
        toolbar.tvSubtitle.text =
            resources.getQuantityString(R.plurals.image_count_reorder, images.size, images.size)
        tvPageSize.setText(setup.size.title)
        tvOrientation.setText(setup.orientation.title)
        tvMargins.setText(setup.margin.title)
        action.btnAction.text = getString(R.string.create_pdf_action, pageCountText(images.size))
        action.btnAction.isEnabled = images.isNotEmpty()
        action.btnAction.alpha = if (images.isNotEmpty()) 1f else 0.5f
    }

    private fun <T> List<T>.next(current: T): T = this[(indexOf(current) + 1) % size]

    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
    ) {
        // The "Add" tile neither moves nor makes room.
        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (viewHolder.bindingAdapterPosition >= images.size) 0
            else super.getDragDirs(recyclerView, viewHolder)

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (to >= images.size) return false
            images = images.toMutableList().apply { add(to, removeAt(from)) }
            imageAdapter.onMove(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Renumber the tiles once the drag ends.
            render()
        }
    }

    private class ImageAdapter : BaseAdapter<File?, ItemImagePageBinding>() {
        override fun createBinding(
            inflater: LayoutInflater,
            parent: ViewGroup,
            viewType: Int
        ) = ItemImagePageBinding.inflate(inflater, parent, false)

        private var onAdd: (() -> Unit) = {}
        private var onItemRemove: ((File) -> Unit) = {}

        fun onAddClick(listener: () -> Unit) = apply { onAdd = listener }
        fun onRemoveClick(listener: (File) -> Unit) = apply { onItemRemove = listener }

        override fun ItemImagePageBinding.bind(item: File?, position: Int) {
            btnAdd.visibleOrGone(item == null)
            ivImage.visibleOrGone(item != null)
            tvNumber.visibleOrGone(item != null)
            btnRemove.visibleOrGone(item != null)
            btnAdd.setOnClickListener { onAdd.invoke() }
            if (item == null) return

            tvNumber.text = (position + 1).toString()
            Glide.with(ivImage).load(item).centerCrop().into(ivImage)
            btnRemove.setOnClickListener { onItemRemove.invoke(item) }
        }
    }

    private companion object {
        const val IMPORT_DIR = "images_to_pdf"
    }
}
