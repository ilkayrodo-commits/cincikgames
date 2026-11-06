package com.cincikgames.dost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** Gardırop sahnesi: karakter seçilen kombinle yürür, el sallar, dans eder. */
class StageView(context: Context) : View(context), Choreographer.FrameCallback {

    var outfit = Outfit()
        set(v) { field = v; invalidate() }

    private val sprites = Sprites.get(context)
    private val d = resources.displayMetrics.density
    private val paint = Paint()
    private val outlinePaint = Paint().apply { color = Color.argb(150, 40, 18, 10) }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 0, 0, 0) }
    private val rect = RectF()
    private var t = 0f
    private var last = 0L
    private var running = false

    /** (kare, süre, sallanma) */
    private val script = buildList {
        repeat(8) { add(Triple(if (it % 2 == 0) "walk1" else "walk2", 0.18f, it % 2 == 1)) }
        add(Triple("stand", 0.8f, false)); add(Triple("blink", 0.15f, false)); add(Triple("stand", 0.5f, false))
        repeat(6) { add(Triple(if (it % 2 == 0) "wave1" else "wave2", 0.22f, false)) }
        add(Triple("happy", 0.7f, false))
        repeat(8) { add(Triple(if (it % 2 == 0) "dance1" else "dance2", 0.3f, true)) }
        add(Triple("hop", 0.4f, false)); add(Triple("stand", 0.6f, false))
    }
    private val total = script.sumOf { it.second.toDouble() }.toFloat()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        last = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (last != 0L) t += ((frameTimeNanos - last) / 1e9f).coerceAtMost(0.1f)
        last = frameTimeNanos
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        bg.color = Color.parseColor("#1C1F28")
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, 22 * d, 22 * d, bg)
        bg.color = Color.parseColor("#252A36")
        rect.set(0f, h * 0.72f, w, h)
        canvas.drawRoundRect(rect, 22 * d, 22 * d, bg)

        var tt = t % total
        var cur = script[0]
        var idx = 0
        for ((i, s) in script.withIndex()) {
            if (tt < s.second) { cur = s; idx = i; break }
            tt -= s.second
        }
        val g = sprites.dresser.dress(cur.first, outfit)
        val px = min((h * 0.78f) / g.h, (w * 0.6f) / g.w).toInt().toFloat().coerceAtLeast(2f)
        val ground = h * 0.80f
        // yürürken sahnede sağa-sola gidip gelir
        val walkPhase = sin(t * 0.6f)
        val cx = w / 2f + if (idx < 8) walkPhase * w * 0.12f else 0f
        val facingLeft = idx < 8 && kotlin.math.cos(t * 0.6f) < 0f
        val bob = if (cur.third) -px * 0.6f else 0f
        val lift = if (cur.first == "hop") px * 4f else if (cur.first.startsWith("dance")) abs(sin(t * 10f)) * px * 1.2f else 0f

        rect.set(cx - px * 7f, ground - px * 0.9f, cx + px * 7f, ground + px * 0.9f)
        canvas.drawOval(rect, shadow)

        // karakterin 16x14 karesinin sol üstü
        val left = cx - sprites.width * px / 2f
        val top = ground - sprites.height * px - lift + bob
        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, cx, 0f)
        val ox = left - g.ox * px
        val oy = top - g.oy * px
        for (y in 0 until g.h) for (x in 0 until g.w) {
            val c = g.px[y * g.w + x]
            val x0 = ox + x * px
            val y0 = oy + y * px
            if (c == 0) {
                if (g.outline[y * g.w + x]) canvas.drawRect(x0, y0, x0 + px + 0.5f, y0 + px + 0.5f, outlinePaint)
                continue
            }
            paint.color = c
            canvas.drawRect(x0, y0, x0 + px + 0.5f, y0 + px + 0.5f, paint)
        }
        canvas.restore()
    }
}
