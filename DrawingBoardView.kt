package com.example.arabicfloatingboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.Stroke

class DrawingBoardView(context: Context) : View(context) {
    private data class PointData(val x: Float, val y: Float, val t: Long)
    private data class DrawStroke(val points: MutableList<PointData>, val path: Path)
    private val strokes = mutableListOf<DrawStroke>()
    private var active: DrawStroke? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15,23,42); style = Paint.Style.STROKE; strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val backgroundPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    fun setPenColor(color: Int) { paint.color = color; invalidate() }
    fun clear() { strokes.clear(); active = null; invalidate() }
    fun hasInk() = strokes.any { it.points.size > 1 }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        canvas.drawColor(0xFFF8FAFC.toInt())
        strokes.forEach { canvas.drawPath(it.path, paint) }
        active?.let { canvas.drawPath(it.path, paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y; val time = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = DrawStroke(mutableListOf(PointData(x, y, time)), Path().apply { moveTo(x, y) })
                active = s; strokes.add(s); parent?.requestDisallowInterceptTouchEvent(true); invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val s = active ?: return true
                for (i in 0 until event.historySize) addPoint(s, event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                addPoint(s, x, y, event.eventTime); invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { active?.let { addPoint(it, x, y, event.eventTime) }; active = null; invalidate(); return true }
        }
        return true
    }

    private fun addPoint(s: DrawStroke, x: Float, y: Float, t: Long) {
        s.points.add(PointData(x, y, t))
        s.path.lineTo(x, y)
    }

    fun toInk(): Ink {
        val inkBuilder = Ink.builder()
        strokes.filter { it.points.isNotEmpty() }.forEach { ds ->
            val sb = Stroke.builder()
            ds.points.forEach { p -> sb.addPoint(Ink.Point.create(p.x, p.y, p.t)) }
            inkBuilder.addStroke(sb.build())
        }
        return inkBuilder.build()
    }
}
