package com.cincikgames.not

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.digitalink.Ink

/** Parmakla yazılan çizgileri hem ekrana çizer hem de ML Kit için Ink olarak toplar. */
class InkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onStrokeStarted: (() -> Unit)? = null
    var onStrokeFinished: (() -> Unit)? = null

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.hint)
        textSize = resources.displayMetrics.scaledDensity * 18f
        textAlign = Paint.Align.CENTER
    }

    private val paths = mutableListOf<Path>()
    private var currentPath: Path? = null
    private var inkBuilder = Ink.builder()
    private var strokeBuilder: Ink.Stroke.Builder? = null
    private var strokeCount = 0

    val isEmpty: Boolean get() = strokeCount == 0 && strokeBuilder == null

    fun buildInk(): Ink = inkBuilder.build()

    fun clear() {
        paths.clear()
        currentPath = null
        strokeBuilder = null
        inkBuilder = Ink.builder()
        strokeCount = 0
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val now = System.currentTimeMillis()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onStrokeStarted?.invoke()
                currentPath = Path().apply { moveTo(event.x, event.y) }
                strokeBuilder = Ink.Stroke.builder().apply {
                    addPoint(Ink.Point.create(event.x, event.y, now))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val path = currentPath ?: return true
                val sb = strokeBuilder ?: return true
                for (i in 0 until event.historySize) {
                    val hx = event.getHistoricalX(i)
                    val hy = event.getHistoricalY(i)
                    path.lineTo(hx, hy)
                    sb.addPoint(Ink.Point.create(hx, hy, event.getHistoricalEventTime(i) - event.eventTime + now))
                }
                path.lineTo(event.x, event.y)
                sb.addPoint(Ink.Point.create(event.x, event.y, now))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val path = currentPath
                val sb = strokeBuilder
                if (path != null && sb != null) {
                    path.lineTo(event.x, event.y)
                    sb.addPoint(Ink.Point.create(event.x, event.y, now))
                    paths.add(path)
                    inkBuilder.addStroke(sb.build())
                    strokeCount++
                }
                currentPath = null
                strokeBuilder = null
                onStrokeFinished?.invoke()
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (paths.isEmpty() && currentPath == null) {
            canvas.drawText(
                context.getString(R.string.pad_hint),
                width / 2f,
                height / 2f,
                hintPaint,
            )
        }
        for (p in paths) canvas.drawPath(p, strokePaint)
        currentPath?.let { canvas.drawPath(it, strokePaint) }
    }
}
