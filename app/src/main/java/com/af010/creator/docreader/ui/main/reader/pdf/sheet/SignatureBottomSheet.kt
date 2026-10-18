package com.af010.creator.docreader.ui.main.reader.pdf.sheet

import com.af010.creator.docreader.ui.dialog.fillHostAccent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.ItemSignatureBinding
import com.af010.creator.docreader.databinding.SheetSignatureBinding
import com.af010.creator.docreader.ui.main.reader.pdf.addColorDots
import com.af010.creator.docreader.ui.main.reader.pdf.edit.SignaturePad
import com.af010.creator.docreader.ui.main.reader.pdf.edit.SignatureStore
import com.af010.creator.docreader.ui.main.reader.pdf.loadPicture
import com.af010.creator.docreader.ui.main.reader.pdf.selectColorDot
import com.af010.creator.docreader.utils.extensions.setCompoundDrawablesSized
import com.af010.creator.docreader.utils.extensions.setFillColor
import com.af010.creator.docreader.utils.extensions.setStroke
import com.d010.pdf.reader.base.toastShort
import com.ui.baselib.base.BaseAdapter
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import com.ui.baselib.utils.dpToPx
import java.io.File
import kotlinx.coroutines.launch

/** Makes a signature (drawn, typed, from a picture or a saved one) and hands it over as a bitmap. */
class SignatureBottomSheet(
    private val activity: FragmentActivity,
    private val onUse: (Bitmap) -> Unit
) : BaseBottomSheet<SheetSignatureBinding>(activity, SheetSignatureBinding::inflate) {

    private enum class Tab { Draw, Type, Image, Saved }

    private val store by lazy { SignatureStore(activity) }
    private val pad by lazy { SignaturePad(activity) }
    private val savedAdapter by lazy { SavedSignatureAdapter() }

    private var tab = Tab.Draw
    private var color = COLORS.first()
    private var saveNext = true
    private var picture: Bitmap? = null
    private var pickedSaved: File? = null
    private var pictureLauncher: ActivityResultLauncher<String>? = null

    override fun SheetSignatureBinding.onBind() {
        btnUse.fillHostAccent()
        header.tvTitle.setText(R.string.your_signature)
        header.btnClose.click { dismissSafe() }

        layoutDraw.addView(pad)
        layoutColors.addColorDots(COLORS) { picked ->
            color = picked
            render()
        }
        rcvSaved.adapter = savedAdapter
        savedAdapter
            .onSignatureClick { file ->
                pickedSaved = file
                savedAdapter.selected = file
            }
            .onRemoveClick { file ->
                store.delete(file)
                if (pickedSaved == file) pickedSaved = null
                loadSaved()
            }

        mapOf(tabDraw to Tab.Draw, tabType to Tab.Type, tabImage to Tab.Image, tabSaved to Tab.Saved)
            .forEach { (view, viewTab) ->
                view.click {
                    tab = viewTab
                    render()
                }
            }
        btnClear.click {
            when (tab) {
                Tab.Draw -> pad.clear()
                Tab.Type -> edtSignature.setText("")
                Tab.Image -> picture = null
                Tab.Saved -> Unit
            }
            render()
        }
        btnPickImage.clickSingle { pickPicture() }
        ivSignature.click { pickPicture() }
        edtSignature.afterTextChanged { render() }
        btnSaveNext.click {
            saveNext = !saveNext
            render()
        }
        btnUse.clickSingle { use() }

        loadSaved()
        render()
    }

    private fun SheetSignatureBinding.loadSaved() {
        val saved = store.list()
        savedAdapter.submitList(saved)
        tabSaved.text = activity.getString(R.string.sign_saved, saved.size)
        render()
    }

    private fun SheetSignatureBinding.render() {
        listOf(tabDraw, tabType, tabImage, tabSaved).forEachIndexed { index, view ->
            val selected = index == tab.ordinal
            view.setFillColor(ContextCompat.getColor(
                activity, if (selected) R.color.color_surface_card else android.R.color.transparent
            ))
            view.setTextColor(
                ContextCompat.getColor(activity, if (selected) R.color.text_primary else R.color.text_secondary)
            )
            view.invalidate()
        }
        pad.color = color
        edtSignature.setTextColor(color)
        edtSignature.typeface = SIGNATURE_TYPEFACE
        layoutColors.selectColorDot(color)

        layoutDraw.visibleOrGone(tab == Tab.Draw)
        edtSignature.visibleOrGone(tab == Tab.Type)
        ivSignature.visibleOrGone(tab == Tab.Image && picture != null)
        btnPickImage.visibleOrGone(tab == Tab.Image && picture == null)
        rcvSaved.visibleOrGone(tab == Tab.Saved && savedAdapter.isNotEmpty())
        tvSavedEmpty.visibleOrGone(tab == Tab.Saved && savedAdapter.isEmpty())
        btnClear.visibleOrGone(tab != Tab.Saved && !(tab == Tab.Image && picture == null))
        layoutColors.visibleOrGone(tab == Tab.Draw || tab == Tab.Type)
        btnSaveNext.visibleOrGone(tab != Tab.Saved)
        ivSignature.setImageBitmap(picture)
        btnSaveNext.setCompoundDrawablesSized(
            com.intuit.sdp.R.dimen._17sdp,
            start = if (saveNext) R.drawable.ic_app_checkbox_on else R.drawable.ic_app_checkbox_off
        )
    }

    private fun SheetSignatureBinding.use() {
        val bitmap = when (tab) {
            Tab.Draw -> pad.signature()
            Tab.Type -> typedSignature(edtSignature.text.toString().trim())
            Tab.Image -> picture
            Tab.Saved -> pickedSaved?.let(store::load)
        }
        if (bitmap == null) {
            activity.toastShort(activity.getString(R.string.sign_empty))
            return
        }
        if (saveNext && tab != Tab.Saved) store.save(bitmap)
        dismissSafe()
        onUse(bitmap)
    }

    /** [text] written in the signature typeface on a transparent bitmap; null when blank. */
    private fun typedSignature(text: String): Bitmap? {
        if (text.isEmpty()) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@SignatureBottomSheet.color
            textSize = activity.dpToPx(64).toFloat()
            typeface = SIGNATURE_TYPEFACE
        }
        val margin = paint.textSize * 0.2f
        val metrics = paint.fontMetrics
        val width = (paint.measureText(text) + margin * 2).toInt().coerceAtLeast(1)
        val height = (metrics.descent - metrics.ascent + margin * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, margin, margin - metrics.ascent, paint)
        return bitmap
    }

    private fun pickPicture() {
        pictureLauncher?.unregister()
        pictureLauncher = activity.activityResultRegistry.register(
            "signature-picture-" + System.nanoTime(), ActivityResultContracts.GetContent()
        ) { uri ->
            pictureLauncher?.unregister()
            pictureLauncher = null
            if (uri != null) loadPicture(uri)
        }
        pictureLauncher?.launch("image/*")
    }

    private fun loadPicture(uri: Uri) {
        activity.lifecycleScope.launch {
            val bitmap = activity.loadPicture(uri, MAX_PICTURE_PX)
            if (bitmap == null) {
                activity.toastShort(activity.getString(R.string.error_file_open_generic))
                return@launch
            }
            picture = bitmap
            // The sheet may be gone by the time the picture is read.
            if (isShowing) binding.render()
        }
    }

    private companion object {
        val COLORS = listOf(0xFF20243A.toInt(), 0xFF1D3FA8.toInt(), 0xFFD93A2F.toInt(), 0xFF16A34A.toInt())
        val SIGNATURE_TYPEFACE: Typeface = Typeface.create("cursive", Typeface.ITALIC)
        const val MAX_PICTURE_PX = 1600
    }
}

class SavedSignatureAdapter : BaseAdapter<File, ItemSignatureBinding>() {
    override fun createBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ) = ItemSignatureBinding.inflate(inflater, parent, false)

    private var onItemClick: ((File) -> Unit) = {}
    fun onSignatureClick(listener: (File) -> Unit) = apply {
        onItemClick = listener
    }

    private var onRemove: ((File) -> Unit) = {}
    fun onRemoveClick(listener: (File) -> Unit) = apply {
        onRemove = listener
    }

    var selected: File? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun ItemSignatureBinding.bind(item: File, position: Int) {
        val context = root.context
        val isSelected = item == selected
        ivSignature.setImageBitmap(BitmapFactory.decodeFile(item.path))
        card.setStroke(context.dpToPx(if (isSelected) 2 else 1), ContextCompat.getColor(
            context, if (isSelected) R.color.primary else R.color.color_stroke
        ))
        card.invalidate()
        root.setOnClickListener { onItemClick.invoke(item) }
        btnRemove.setOnClickListener { onRemove.invoke(item) }
    }
}
