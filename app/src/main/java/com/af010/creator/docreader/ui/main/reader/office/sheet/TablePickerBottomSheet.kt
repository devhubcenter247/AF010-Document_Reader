package com.af010.creator.docreader.ui.main.reader.office.sheet

import com.af010.creator.docreader.ui.dialog.fillHostAccent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetTablePickerBinding
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle

/** Size of a new table; [onInsert] gets the rows and columns. */
class TablePickerBottomSheet(
    private val activity: FragmentActivity,
    private val onInsert: (rows: Int, columns: Int) -> Unit,
    private val onCancel: () -> Unit
) : BaseBottomSheet<SheetTablePickerBinding>(activity, SheetTablePickerBinding::inflate) {

    private var rows = DEFAULT_SIZE
    private var columns = DEFAULT_SIZE
    private var inserted = false

    override fun SheetTablePickerBinding.onBind() {
        btnInsert.fillHostAccent()
        header.tvTitle.setText(R.string.insert_table)
        header.btnClose.click { dismissSafe() }

        btnColumnsLess.click { change(columnsBy = -1) }
        btnColumnsMore.click { change(columnsBy = 1) }
        btnRowsLess.click { change(rowsBy = -1) }
        btnRowsMore.click { change(rowsBy = 1) }
        btnInsert.clickSingle {
            inserted = true
            dismissSafe()
            onInsert(rows, columns)
        }
        onDismissListener = { if (!inserted) onCancel() }
        render()
    }

    private fun SheetTablePickerBinding.change(rowsBy: Int = 0, columnsBy: Int = 0) {
        rows = (rows + rowsBy).coerceIn(1, MAX_ROWS)
        columns = (columns + columnsBy).coerceIn(1, MAX_COLUMNS)
        render()
    }

    private fun SheetTablePickerBinding.render() {
        tvSize.text = activity.getString(R.string.table_size, columns, rows)
        tvColumns.text = columns.toString()
        tvRows.text = rows.toString()
        gridPreview.setSize(rows, columns)
    }

    private companion object {
        const val DEFAULT_SIZE = 3
        const val MAX_ROWS = 50
        const val MAX_COLUMNS = 12
    }
}

/** A small picture of a table with the chosen rows and columns. */
class TableGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var rows = 3
    private var columns = 3
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_bg_selected)
    }

    fun setSize(rows: Int, columns: Int) {
        this.rows = rows
        this.columns = columns
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // No more cells than can be told apart: a long table is drawn as its first rows.
        val shownRows = rows.coerceAtMost(MAX_SHOWN)
        val shownColumns = columns.coerceAtMost(MAX_SHOWN)
        val inset = density
        val cellWidth = (width - 2 * inset) / shownColumns
        val cellHeight = (height - 2 * inset) / shownRows
        canvas.drawRect(inset, inset, width - inset, height - inset, fill)
        for (row in 0..shownRows) {
            val y = inset + row * cellHeight
            canvas.drawLine(inset, y, width - inset, y, line)
        }
        for (column in 0..shownColumns) {
            val x = inset + column * cellWidth
            canvas.drawLine(x, inset, x, height - inset, line)
        }
    }

    private companion object {
        const val MAX_SHOWN = 12
    }
}
