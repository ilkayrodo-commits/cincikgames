package com.cincikgames.dost

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Karakteri çizer: piksel karesi, gölge, kalp ve "z" balonları.
 * Pencere boyutu sabittir; zıplama vb. pencere içinde çizilir.
 */
@SuppressLint("ViewConstructor")
class BuddyView(context: Context, private val sprites: Sprites, val pixel: Float) : View(context) {

    var frame = "stand"
    var facingLeft = false
    var lift = 0f          // zıplama yüksekliği (piksel)
    var bob = 0f           // yürürken hafif sallanma
    var scale = 1f         // kapanırken küçülme
    var heart = 0f         // 0..1 kalp balonu
    var sleepy = 0f        // 0..1 uyku "z"leri
    var time = 0f

    val spriteW get() = sprites.width * pixel
    val spriteH get() = sprites.height * pixel
    /** Karakterin üstünde balonlar ve zıplama için boşluk. */
    val headroom get() = pixel * 10f
    val viewW get() = (spriteW + pixel * 4f).toInt()
    val viewH get() = (spriteH + headroom + pixel * 3f).toInt()

    private val paint = Paint().apply { isAntiAlias = false }
    private val outlinePaint = Paint().apply { color = Color.argb(150, 40, 18, 10) }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val heartPaint = Paint().apply { color = Color.parseColor("#FF5A7A") }
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewW, viewH)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val groundY = height - pixel * 2f
        canvas.save()
        canvas.scale(scale, scale, cx, groundY)

        // gölge: zıpladıkça küçülür
        val sw = spriteW * 0.42f * (1f - (lift / (headroom * 2f)).coerceIn(0f, 0.5f))
        rect.set(cx - sw, groundY - pixel * 0.9f, cx + sw, groundY + pixel * 0.9f)
        canvas.drawOval(rect, shadow)

        val f = sprites.frames[frame] ?: sprites.frames.getValue("stand")
        val left = cx - spriteW / 2f
        val top = groundY - spriteH - lift + bob
        canvas.save()
        if (facingLeft) canvas.scale(-1f, 1f, cx, 0f)
        for (y in 0 until f.h) {
            for (x in 0 until f.w) {
                val c = f.pixels[y * f.w + x]
                if (Color.alpha(c) == 0) {
                    if (f.outline[y * f.w + x]) {
                        val ox = left + x * pixel
                        val oy = top + y * pixel
                        canvas.drawRect(ox, oy, ox + pixel + 0.5f, oy + pixel + 0.5f, outlinePaint)
                    }
                    continue
                }
                paint.color = c
                val px = left + x * pixel
                val py = top + y * pixel
                canvas.drawRect(px, py, px + pixel + 0.5f, py + pixel + 0.5f, paint)
            }
        }
        canvas.restore()

        if (heart > 0f) drawHeart(canvas, cx + pixel * 5f, (top - pixel * 3f - heart * pixel * 3f).coerceAtLeast(0f), heart)
        if (sleepy > 0f) drawZs(canvas, cx + pixel * 6f, top + pixel * 2f)
        canvas.restore()
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, a: Float) {
        val rows = arrayOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
        heartPaint.alpha = (255 * (1f - (a - 0.7f).coerceAtLeast(0f) / 0.3f)).toInt().coerceIn(0, 255)
        val p = pixel * 0.7f
        val ox = cx - 3.5f * p
        rows.forEachIndexed { y, r ->
            r.forEachIndexed { x, ch ->
                if (ch == 'X') canvas.drawRect(ox + x * p, cy + y * p, ox + (x + 1) * p + 0.5f, cy + (y + 1) * p + 0.5f, heartPaint)
            }
        }
    }

    private fun drawZs(canvas: Canvas, x: Float, y: Float) {
        for (i in 0 until 3) {
            val ph = ((time * 0.5f + i / 3f) % 1f)
            zPaint.alpha = (255 * sleepy * (1f - ph)).toInt().coerceIn(0, 255)
            zPaint.textSize = pixel * (2.5f + ph * 2.5f)
            canvas.drawText("z", x + ph * pixel * 4f, y - ph * pixel * 10f, zPaint)
        }
    }
}
