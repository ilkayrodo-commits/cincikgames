package com.cincikgames.dost

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kaos katmanı: ekranın üstünde (gerçekte hiçbir şeye dokunmadan) domates ve
 * yumurta lekeleri, sprey boya çizimleri ve cam çatlakları çizer; sonunda sünger
 * soldan sağa geçerek hepsini siler.
 */
@SuppressLint("ViewConstructor")
class ChaosView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val rnd = Random(System.nanoTime())

    // ------------------------------------------------------------ öğeler

    private abstract inner class Thing {
        var age = 0f
        open fun update(dt: Float) { age += dt }
        abstract fun draw(c: Canvas)
    }

    /** Havada uçan domates / yumurta; varınca lekeye dönüşür. */
    private inner class Projectile(
        val egg: Boolean, val sx: Float, val sy: Float, val tx: Float, val ty: Float, val onHit: () -> Unit,
    ) : Thing() {
        val dur = 0.55f + hypot(tx - sx, ty - sy) / (2200f * d)
        var done = false
        override fun update(dt: Float) {
            super.update(dt)
            if (!done && age >= dur) { done = true; onHit() }
        }
        override fun draw(c: Canvas) {
            if (done) return
            val p = (age / dur).coerceIn(0f, 1f)
            val x = sx + (tx - sx) * p
            val y = sy + (ty - sy) * p - sin(p * PI.toFloat()) * 140f * d
            val sc = 1f + p * 0.8f   // ekrana doğru yaklaşıyor gibi büyür
            c.save()
            c.translate(x, y)
            c.rotate(age * 540f)
            c.scale(sc, sc)
            if (egg) {
                fill.color = Color.parseColor("#FFF8EC")
                rect.set(-9 * d, -12 * d, 9 * d, 12 * d)
                c.drawOval(rect, fill)
                fill.color = Color.parseColor("#E9DFC9")
                rect.set(-9 * d, 2 * d, 9 * d, 12 * d)
                c.drawArc(rect, 0f, 180f, false, fill)
            } else {
                fill.color = Color.parseColor("#E63B2E")
                c.drawCircle(0f, 0f, 12 * d, fill)
                fill.color = Color.parseColor("#FF8A7A")
                c.drawCircle(-4 * d, -4 * d, 3.5f * d, fill)
                fill.color = Color.parseColor("#2E9E4F")
                for (i in 0 until 5) {
                    val a = i * 72f * PI.toFloat() / 180f
                    c.drawCircle(cos(a) * 4 * d, -11 * d + sin(a) * 2.5f * d, 2.4f * d, fill)
                }
            }
            c.restore()
        }
    }

    /** Leke: düzensiz kenarlı yuvarlak, sıçrantılar ve aşağı süzülen damlalar. */
    private inner class Splat(val x: Float, val y: Float, val egg: Boolean) : Thing() {
        val r = (34 + rnd.nextFloat() * 18) * d
        val lobes = FloatArray(14) { 0.75f + rnd.nextFloat() * 0.45f }
        val drops = List(9) { Triple(rnd.nextFloat() * 6.28f, 1.2f + rnd.nextFloat() * 0.9f, (3 + rnd.nextFloat() * 6) * d) }
        val drips = List(3 + rnd.nextInt(3)) { Pair((rnd.nextFloat() - 0.5f) * r * 1.3f, (40 + rnd.nextFloat() * 90) * d) }
        val seeds = List(10) { Pair((rnd.nextFloat() - 0.5f) * r, (rnd.nextFloat() - 0.5f) * r) }
        val path = Path()

        override fun draw(c: Canvas) {
            val pop = (age / 0.12f).coerceIn(0f, 1f)
            val main = if (egg) Color.parseColor("#F7F1E3") else Color.parseColor("#D7261E")
            val dark = if (egg) Color.parseColor("#E8DCC2") else Color.parseColor("#A9150F")
            // damlalar: zamanla uzar, yavaşlayarak durur
            for ((dx, len) in drips) {
                val l = len * (1f - 1f / (1f + age * 0.9f))
                fill.color = dark
                c.drawRect(x + dx - 3 * d, y, x + dx + 3 * d, y + r * 0.4f + l, fill)
                c.drawCircle(x + dx, y + r * 0.4f + l, 4.5f * d, fill)
            }
            path.reset()
            for (i in 0..lobes.size) {
                val a = i / lobes.size.toFloat() * 6.2832f
                val rr = r * lobes[i % lobes.size] * pop
                val px = x + cos(a) * rr
                val py = y + sin(a) * rr
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            fill.color = main
            c.drawPath(path, fill)
            for ((a, k, s) in drops) {
                c.drawCircle(x + cos(a) * r * k * pop, y + sin(a) * r * k * pop, s, fill)
            }
            if (egg) {
                fill.color = Color.parseColor("#FFB400")
                c.drawCircle(x + r * 0.08f, y - r * 0.05f, r * 0.36f * pop, fill)
                fill.color = Color.parseColor("#FFD866")
                c.drawCircle(x - r * 0.04f, y - r * 0.16f, r * 0.12f * pop, fill)
            } else {
                fill.color = Color.parseColor("#F6C945")
                for ((sx, sy) in seeds) c.drawOval(x + sx - 2.2f * d, y + sy - 1.4f * d, x + sx + 2.2f * d, y + sy + 1.4f * d, fill)
                fill.color = Color.parseColor("#FF6F61")
                c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.14f, fill)
            }
        }
    }

    /** Sprey boya çizimi: yol boyunca yavaşça ilerleyen, kenarları püskürtme noktalı çizgi. */
    private inner class Graffiti(shape: Int, val cx: Float, val cy: Float, val ink: Int) : Thing() {
        val path = Path()
        val measure: PathMeasure
        val length: Float
        val dur = 2.2f
        val dots = ArrayList<FloatArray>()
        private val pos = FloatArray(2)
        private var sprayed = 0f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 11 * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = ink
        }

        init {
            val s = 70f * d
            when (shape) {
                0 -> { // kalp
                    path.moveTo(cx, cy + s * 0.9f)
                    path.cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 1.2f, cx, cy - s * 0.35f)
                    path.cubicTo(cx + s * 0.6f, cy - s * 1.2f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f)
                }
                1 -> { // gülen surat
                    path.addCircle(cx, cy, s, Path.Direction.CW)
                    path.addCircle(cx - s * 0.38f, cy - s * 0.3f, s * 0.08f, Path.Direction.CW)
                    path.addCircle(cx + s * 0.38f, cy - s * 0.3f, s * 0.08f, Path.Direction.CW)
                    path.moveTo(cx - s * 0.55f, cy + s * 0.2f)
                    path.quadTo(cx, cy + s * 0.75f, cx + s * 0.55f, cy + s * 0.2f)
                }
                2 -> { // yıldız
                    for (i in 0..5) {
                        val a = -PI.toFloat() / 2 + i * 4 * PI.toFloat() / 5
                        val px = cx + cos(a) * s
                        val py = cy + sin(a) * s
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                }
                else -> { // zikzak
                    path.moveTo(cx - s * 1.4f, cy)
                    for (i in 1..6) path.lineTo(cx - s * 1.4f + i * s * 0.47f, cy + if (i % 2 == 0) 0f else -s * 0.6f)
                }
            }
            measure = PathMeasure(path, false)
            var total = 0f
            do { total += measure.length } while (measure.nextContour())
            length = total
        }

        override fun update(dt: Float) {
            super.update(dt)
            // ilerleyen uç etrafına püskürtme noktaları ekle
            val target = (age / dur).coerceIn(0f, 1f) * length
            while (sprayed < target) {
                sprayed += 6f * d
                tipAt(sprayed)?.let { (x, y) ->
                    repeat(5) {
                        val a = rnd.nextFloat() * 6.28f
                        val rr = (7 + rnd.nextFloat() * 14) * d
                        dots.add(floatArrayOf(x + cos(a) * rr, y + sin(a) * rr, (0.8f + rnd.nextFloat() * 1.6f) * d))
                    }
                }
            }
        }

        fun tipAt(dist: Float): Pair<Float, Float>? {
            val m = PathMeasure(path, false)
            var left = dist
            do {
                if (left <= m.length) { m.getPosTan(left, pos, null); return pos[0] to pos[1] }
                left -= m.length
            } while (m.nextContour())
            return null
        }

        /** Boyanın şu an püskürdüğü nokta (karakter oraya bakar). */
        fun tip(): Pair<Float, Float>? = tipAt((age / dur).coerceIn(0f, 1f) * length)

        override fun draw(c: Canvas) {
            val target = (age / dur).coerceIn(0f, 1f) * length
            val seg = Path()
            val m = PathMeasure(path, false)
            var left = target
            do {
                val l = m.length
                m.getSegment(0f, minOf(left, l), seg, true)
                left -= l
            } while (left > 0 && m.nextContour())
            c.drawPath(seg, stroke)
            fill.color = ink
            fill.alpha = 150
            for (p in dots) c.drawCircle(p[0], p[1], p[2], fill)
            fill.alpha = 255
        }
    }

    /** Cam çatlağı: merkezden kırık kırık uzanan çizgiler ve aralarında halkalar. */
    private inner class Crack(val x: Float, val y: Float) : Thing() {
        val rays = List(9 + rnd.nextInt(4)) { i ->
            val base = i * 6.2832f / 10f + rnd.nextFloat() * 0.5f
            val pts = ArrayList<FloatArray>()
            var r = 0f
            var a = base
            pts.add(floatArrayOf(x, y))
            val maxR = (60 + rnd.nextFloat() * 110) * d
            while (r < maxR) {
                r += (12 + rnd.nextFloat() * 16) * d
                a += (rnd.nextFloat() - 0.5f) * 0.35f
                pts.add(floatArrayOf(x + cos(a) * r, y + sin(a) * r))
            }
            pts
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * d
            color = Color.argb(235, 255, 255, 255)
        }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.2f * d
            color = Color.argb(110, 0, 0, 0)
        }

        override fun draw(c: Canvas) {
            val grow = (age / 0.25f).coerceIn(0f, 1f)
            for (pts in rays) {
                val n = (pts.size * grow).toInt().coerceAtLeast(1)
                for (i in 1 until n) {
                    c.drawLine(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], shadow)
                    c.drawLine(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], line)
                }
            }
            // örümcek ağı halkaları
            if (grow >= 1f) {
                for (ring in 1..3) {
                    for (k in rays.indices) {
                        val a = rays[k]
                        val b = rays[(k + 1) % rays.size]
                        val ia = ring * 2
                        if (ia < a.size && ia < b.size) {
                            c.drawLine(a[ia][0], a[ia][1], b[ia][0], b[ia][1], shadow)
                            c.drawLine(a[ia][0], a[ia][1], b[ia][0], b[ia][1], line)
                        }
                    }
                }
            }
            // darbe noktası
            fill.color = Color.argb(200, 255, 255, 255)
            c.drawCircle(x, y, 5 * d, fill)
            // parıltı
            val gl = (0.5f + 0.5f * sin(age * 6f))
            fill.color = Color.argb((120 * gl).toInt(), 255, 255, 255)
            c.drawCircle(x + 26 * d, y - 30 * d, 3 * d, fill)
        }
    }

    private val things = ArrayList<Thing>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var shake = 0f

    /** Silme çizgisi (0..1); sol tarafı temizlenmiş. <0 ise silme yok. */
    var wipe = -1f
    var fade = 1f

    // ------------------------------------------------------------ dışarıdan

    fun throwThing(egg: Boolean, sx: Float, sy: Float, tx: Float, ty: Float) {
        things.add(Projectile(egg, sx, sy, tx, ty) {
            things.add(Splat(tx, ty, egg))
            shake = 0.12f
        })
    }

    private var spray: Graffiti? = null

    fun startSpray(cx: Float, cy: Float): Float {
        val colors = intArrayOf(
            Color.parseColor("#FF2E88"), Color.parseColor("#39FF14"),
            Color.parseColor("#00E5FF"), Color.parseColor("#FFE500"), Color.parseColor("#B026FF"),
        )
        val g = Graffiti(rnd.nextInt(4), cx, cy, colors[rnd.nextInt(colors.size)])
        things.add(g)
        spray = g
        return g.dur
    }

    /** Sprey ucunun şu anki yeri (yoksa null). */
    fun sprayTip(): Pair<Float, Float>? = spray?.let { if (it.age < it.dur) it.tip() else null }

    fun crack(x: Float, y: Float) {
        things.add(Crack(x, y))
        shake = 0.35f
    }

    fun update(dt: Float) {
        for (i in things.indices) things[i].update(dt)
        shake = (shake - dt).coerceAtLeast(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        if (shake > 0f) {
            val k = shake * 22f * d
            canvas.translate((rnd.nextFloat() - 0.5f) * k, (rnd.nextFloat() - 0.5f) * k)
        }
        if (wipe >= 0f) canvas.clipRect(width * wipe, 0f, width.toFloat(), height.toFloat())
        if (fade < 1f) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (fade * 255).toInt())
        for (t in things) t.draw(canvas)
        if (fade < 1f) canvas.restore()
        canvas.restore()
        // süngerin bıraktığı ıslak iz
        if (wipe in 0f..1f) {
            val x = width * wipe
            fill.color = Color.argb(70, 200, 230, 255)
            canvas.drawRect(x - 26 * d, 0f, x, height.toFloat(), fill)
            fill.color = Color.argb(140, 255, 255, 255)
            canvas.drawRect(x - 3 * d, 0f, x, height.toFloat(), fill)
        }
    }
}
