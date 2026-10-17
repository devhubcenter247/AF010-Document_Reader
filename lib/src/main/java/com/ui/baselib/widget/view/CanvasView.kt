package com.ui.baselib.widget.view
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin


class CanvasView @JvmOverloads constructor(
context: Context,
attrs: AttributeSet? = null,
defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val builder = CanvasBuilder()
    private var viewWidth = 0f
    private var viewHeight = 0f
    init {
        setWillNotDraw(false)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewWidth == 0f || viewHeight == 0f) return

        builder.width = viewWidth
        builder.height = viewHeight

        builder.canvasDrawCommands.forEach { it(canvas) }
    }

    fun drawCanvas(builderConfig: CanvasBuilder.() -> Unit) {
        post {
            builder.canvasDrawCommands.clear()
            builder.apply(builderConfig)
            invalidate()
        }
    }
    inner class CanvasBuilder {
        val canvasDrawCommands = mutableListOf<(Canvas) -> Unit>()
        var width = 0f
            internal set
        var height = 0f
            internal set

        fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            canvasDrawCommands.add { canvas -> canvas.drawCircle(cx, cy, radius, paint) }
        }

        fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            canvasDrawCommands.add { canvas -> canvas.drawRect(left, top, right, bottom, paint) }
        }

        fun drawLine(startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
            canvasDrawCommands.add { canvas -> canvas.drawLine(startX, startY, endX, endY, paint) }
        }

        fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            canvasDrawCommands.add { canvas -> canvas.drawText(text, x, y, paint) }
        }

        fun drawPath(path: Path, paint: Paint) {
            canvasDrawCommands.add { canvas -> canvas.drawPath(path, paint) }
        }

        fun drawDashedLine(
            startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint, dashSize: Float = 10f
        ) {
            paint.pathEffect = DashPathEffect(floatArrayOf(dashSize, dashSize), 0f)
            drawLine(startX, startY, endX, endY, paint)
            paint.pathEffect = null // Reset to avoid affecting other drawings
        }

        fun drawBezierCurve(
            start: PointF, control1: PointF, control2: PointF, end: PointF, paint: Paint
        ) {
            val path = Path()
            path.moveTo(start.x, start.y)
            path.cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
            drawPath(path, paint)
        }
        fun drawTriangle(
            borderPaint: Paint,
            firstPoint: PointF,
            secondPoint: PointF,
            thirdPoint: PointF
        ) {
            val path = Path().apply {
                moveTo(firstPoint.x, firstPoint.y)
                lineTo(secondPoint.x, secondPoint.y)
                lineTo(thirdPoint.x, thirdPoint.y)
                close()
            }
            drawPath(path, borderPaint)
        }
        fun drawShapeWithPoints(points: List<PointF>, paint: Paint) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
                close()
            }
            drawPath(path, paint)
        }
        fun drawRoundedPolygon(points: List<PointF>, radius: Float, paint: Paint) {
            if (points.size < 3) return
            drawRoundedShape(points, radius, paint)
        }
        fun drawRoundedShape(points: List<PointF>, radius: Float, paint: Paint) {
            if (points.size < 2) return
            val path = Path()
            val pointCount = points.size
            for (i in points.indices) {
                val current = points[i]
                val next = points[(i + 1) % pointCount]
                val previous = points[(i - 1 + pointCount) % pointCount]

                val vectorIn = PointF(current.x - previous.x, current.y - previous.y)
                val vectorOut = PointF(next.x - current.x, next.y - current.y)

                val lengthIn = vectorIn.vectorLength()
                val lengthOut = vectorOut.vectorLength()

                val scaledIn = vectorIn.scale(radius / lengthIn)
                val scaledOut = vectorOut.scale(radius / lengthOut)

                val cornerStart = PointF(current.x - scaledIn.x, current.y - scaledIn.y)
                val cornerEnd = PointF(current.x + scaledOut.x, current.y + scaledOut.y)

                if (i == 0) {
                    path.moveTo(cornerStart.x, cornerStart.y)
                } else {
                    path.lineTo(cornerStart.x, cornerStart.y)
                }
                path.quadTo(current.x, current.y, cornerEnd.x, cornerEnd.y)
            }
            path.close()
            drawPath(path, paint)
        }
        fun Paint.applyShadow(
            radius: Float = 10f, dx: Float = 5f, dy: Float = 5f, shadowColor: Int = Color.BLACK
        ) {
            this.setShadowLayer(radius, dx, dy, shadowColor)
        }
        fun Paint.applyGradient(
            colors: IntArray, positions: FloatArray? = null, angle: Float = 0f, bounds: RectF
        ) {
            val radian = Math.toRadians(angle.toDouble())
            val x0 = bounds.left
            val y0 = bounds.top
            val x1 = bounds.right * cos(radian).toFloat()
            val y1 = bounds.bottom * sin(radian).toFloat()

            this.shader = LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
        }
        private fun PointF.vectorLength(): Float {
            return hypot(this.x.toDouble(), this.y.toDouble()).toFloat()
        }
        private fun PointF.scale(scalar: Float): PointF {
            return PointF(this.x * scalar, this.y * scalar)
        }
    }
}
