package com.af010.creator.docreader.ui.main.reader.pdf.edit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Signatures drawn by the user, kept as transparent PNGs in the app's files to sign again. */
internal class SignatureStore(context: Context) {
    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }

    fun list(): List<File> = dir.listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun load(file: File): Bitmap? = BitmapFactory.decodeFile(file.path)

    fun save(bitmap: Bitmap): File? = try {
        File(dir, "signature-${System.currentTimeMillis()}.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    } catch (e: Exception) {
        null
    }

    fun delete(file: File) { file.delete() }
}

/** A pad to sign on with the finger; [signature] is the drawing cropped to its ink, transparent around it. */
@SuppressLint("ViewConstructor")
internal class SignaturePad(context: Context) : View(context) {
    var color: Int = Color.BLACK
        set(value) { field = value; paint.color = value; invalidate() }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3 * density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.BLACK
    }
    private val line = Paint().apply { color = 0xFFC5C9D6.toInt(); strokeWidth = density }
    private val path = Path()
    private val bounds = RectF()
    private var lastX = 0f
    private var lastY = 0f

    val isEmpty: Boolean get() = path.isEmpty

    fun clear() {
        path.reset()
        bounds.setEmpty()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                path.moveTo(x, y)
                path.lineTo(x + 0.1f, y)
                lastX = x; lastY = y
                grow(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) addPoint(event.getHistoricalX(i), event.getHistoricalY(i))
                addPoint(x, y)
            }
        }
        invalidate()
        return true
    }

    private fun addPoint(x: Float, y: Float) {
        path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
        lastX = x; lastY = y
        grow(x, y)
    }

    private fun grow(x: Float, y: Float) {
        if (bounds.isEmpty) bounds.set(x, y, x + 1, y + 1) else bounds.union(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        val base = height * 0.75f
        canvas.drawLine(width * 0.08f, base, width * 0.92f, base, line)
        canvas.drawPath(path, paint)
    }

    /** The drawing, cropped with a small margin, on a transparent background; null when empty. */
    fun signature(): Bitmap? {
        if (path.isEmpty) return null
        val pad = paint.strokeWidth * 2
        val left = max(0f, bounds.left - pad)
        val top = max(0f, bounds.top - pad)
        val right = min(width.toFloat(), bounds.right + pad)
        val bottom = min(height.toFloat(), bounds.bottom + pad)
        if (right - left < 2 || bottom - top < 2) return null
        // twice the screen size, sharp enough in print
        val scale = 2f
        val bitmap = Bitmap.createBitmap(((right - left) * scale).toInt(), ((bottom - top) * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-left, -top)
        canvas.drawPath(path, paint)
        return bitmap
    }
}
