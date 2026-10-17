package com.af010.creator.docreader.ui.main.reader.pdf.edit

import android.graphics.Canvas
import android.graphics.Paint
import com.reader.pdfviewer.pdfium.PdfFormField

/**
 * Filling in a PDF form: the fields of the pages on screen are outlined, a tap on one fills it
 * ([Host.edit] asks the value of a text field or a choice; check boxes and radio buttons
 * toggle at once). Elsewhere the page scrolls.
 */
internal class FormMode(private val overlay: PdfOverlayView, private val host: Host) : PdfOverlayView.Mode {

    interface Host {
        fun edit(field: PdfFormField)
        fun changed()
    }

    private var touched: PdfFormField? = null

    // pdfium is not asked on every frame: fields of a page are read once, again after an edit
    private val fields = HashMap<Int, List<PdfFormField>>()
    private fun fieldsOf(page: Int) = fields.getOrPut(page) { overlay.pdfView.getFormFields(page) }

    /** Values changed: read the fields again. */
    fun refresh() { fields.clear(); overlay.invalidate() }

    override fun onDown(x: Float, y: Float): Boolean {
        touched = overlay.pdfView.findFormFieldAt(x, y)?.takeIf { !it.readOnly }
        return touched != null
    }

    override fun onUp(x: Float, y: Float, tap: Boolean) {
        val field = touched ?: return
        touched = null
        if (!tap) return
        when (field.type) {
            PdfFormField.TYPE_CHECKBOX, PdfFormField.TYPE_RADIOBUTTON ->
                if (overlay.pdfView.toggleFormField(field)) { refresh(); host.changed() }
            PdfFormField.TYPE_PUSHBUTTON -> {}
            else -> host.edit(field)
        }
    }

    override fun onCancel() { touched = null }

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = overlay.dp(1.5f); color = PdfOverlayView.ACCENT
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x224850F4 }

    override fun draw(canvas: Canvas) {
        val pdf = overlay.pdfView
        val first = pdf.pageAt(overlay.width / 2f, 1f) ?: pdf.currentPage
        for (page in (first - 1)..(first + 2)) {
            if (page < 0 || page >= pdf.pageCount) continue
            for (field in fieldsOf(page)) {
                if (field.readOnly || field.type == PdfFormField.TYPE_PUSHBUTTON) continue
                val r = pdf.pageRectToView(page, field.rect) ?: continue
                if (r.bottom < 0 || r.top > overlay.height) continue
                canvas.drawRect(r, fill)
                canvas.drawRect(r, outline)
            }
        }
    }
}
