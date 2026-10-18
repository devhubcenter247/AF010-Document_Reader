package com.af010.creator.docreader.ui.main.tools.images

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.main.common.select
import com.af010.creator.docreader.ui.main.common.setUp
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityImageToPdfBinding
import com.af010.creator.docreader.databinding.ItemImagePageBinding
import com.af010.creator.docreader.model.PageMargin
import com.af010.creator.docreader.model.PageOrientation
import com.af010.creator.docreader.model.PageSetup
import com.af010.creator.docreader.model.PageSize
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.bumptech.glide.Glide
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pick photos, put them in order, choose the page layout, and turn them into one PDF. */
class ImageToPdfActivity :
    BaseAppActivity<ActivityImageToPdfBinding>(ActivityImageToPdfBinding::inflate) {
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
        topBar.show(Tool.ImagesToPdf, R.string.photos_to_pdf)
        action.show(getString(R.string.fo_create_my_pdf), R.drawable.ic_fo_photo)
        segmentSize.setUp(PageSize.A4.title, PageSize.Letter.title, PageSize.Fit.title) {
            setup = setup.copy(size = PageSize.entries[it])
            render()
        }
        segmentOrientation.setUp(
            PageOrientation.Portrait.title, PageOrientation.Landscape.title, PageOrientation.Auto.title
        ) {
            setup = setup.copy(orientation = PageOrientation.entries[it])
            render()
        }
        segmentMargins.setUp(PageMargin.None.title, PageMargin.Small.title, PageMargin.Large.title) {
            setup = setup.copy(margin = PageMargin.entries[it])
            render()
        }
        rcvImages.adapter = imageAdapter
        ItemTouchHelper(dragCallback).attachToRecyclerView(rcvImages)
        render()
        pickImages()
    }

    override fun ActivityImageToPdfBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        imageAdapter
            .onAddClick { pickImages() }
            .onRemoveClick { file ->
                images = images - file
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
        topBar.tvSubtitle.isVisible = true
        topBar.tvSubtitle.text = resources.getQuantityString(R.plurals.fo_photos_reorder, images.size, images.size)
        segmentSize.select(setup.size.ordinal)
        segmentOrientation.select(setup.orientation.ordinal)
        segmentMargins.select(setup.margin.ordinal)
        action.btnAction.isEnabled = images.isNotEmpty()
    }

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
            card.rotation = 0f
            if (item == null) return

            // A loose pile: each photo leans a little, the way the design draws them.
            card.rotation = TILTS[position % TILTS.size]
            tvNumber.text = (position + 1).toString()
            Glide.with(ivImage).load(item).centerCrop().into(ivImage)
            btnRemove.setOnClickListener { onItemRemove.invoke(item) }
        }
    }

    private companion object {
        const val IMPORT_DIR = "images_to_pdf"
        val TILTS = floatArrayOf(-2f, 1.5f, -1f, 2f, -1.5f)
    }
}
