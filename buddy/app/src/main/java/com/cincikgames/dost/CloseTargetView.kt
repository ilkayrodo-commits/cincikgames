package com.cincikgames.dost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Sürüklerken ekranın üstünde beliren ✕ hedefi. */
class CloseTargetView(context: Context) : View(context) {

    var active = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    var appear = 0f
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = Color.argb(200, 255, 255, 255)
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3.5f * d
        strokeCap = Paint.Cap.ROUND
    }

    val sizePx get() = (96 * d).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        val r = (if (active) 38f else 30f) * d * appear
        if (r <= 0f) return
        bg.color = if (active) Color.argb(235, 229, 72, 77) else Color.argb(170, 20, 20, 24)
        canvas.drawCircle(c, c, r, bg)
        canvas.drawCircle(c, c, r, ring)
        val k = r * 0.38f
        canvas.drawLine(c - k, c - k, c + k, c + k, cross)
        canvas.drawLine(c + k, c - k, c - k, c + k, cross)
    }
}
