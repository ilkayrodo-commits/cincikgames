package com.cincikgames.dost

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Karakteri ve işlerinin aksesuarlarını çizer (piksel birimleriyle).
 *
 * Pencere düzeni (piksel birimi = [pixel]):
 *   [padL] [2 boşluk][16 karakter][2 boşluk] [padR]
 * İşler sırasında padL/padR büyür (şövale, tencere, süpürge, toz için yer açılır).
 */
@SuppressLint("ViewConstructor")
class BuddyView(context: Context, private val sprites: Sprites, val pixel: Float) : View(context) {

    enum class Tool { NONE, BRUSH, SPOON, BROOM }
    enum class Hat { NONE, BERET, CHEF }
    enum class Prop { NONE, EASEL, POT, BOWL }

    class Puff(val x: Float, val y: Float, var age: Float = 0f)

    var frame = "stand"
    var facingLeft = false
    var lift = 0f          // zıplama yüksekliği (piksel)
    var bob = 0f           // yürürken hafif sallanma
    var scale = 1f         // kapanırken küçülme
    var heart = 0f         // 0..1 kalp balonu
    var sleepy = 0f        // 0..1 uyku "z"leri
    var notes = 0f         // 0..1 dans notaları
    var sparkle = 0f       // 0..1 temizlik parıltısı
    var rotationDeg = 0f   // duvara tırmanırken ±90
    var time = 0f

    var tool = Tool.NONE
    var toolPhase = 0
    var hat = Hat.NONE
    var prop = Prop.NONE
    var propSide = 1       // +1 sağda, -1 solda
    var picture: Array<String>? = null
    var paintProgress = 0f // 0..1
    var paintColor = Color.parseColor("#3D7DD8")

    var padL = 0f
    var padR = 0f
    /** Pencerenin ekrandaki sol üst köşesi (toz bulutlarını yerinde tutmak için). */
    var winX = 0f
    var winY = 0f
    val puffs = ArrayList<Puff>()

    val spriteW get() = sprites.width * pixel
    val spriteH get() = sprites.height * pixel
    val headroom get() = pixel * 10f
    val baseUnits get() = sprites.width + 4f
    val viewW get() = ((baseUnits + padL + padR) * pixel).toInt()
    val viewH get() = (spriteH + headroom + pixel * 3f).toInt()
    /** Karakterin yatay merkezi (pencere içinde). */
    val spriteCx get() = (padL + baseUnits / 2f) * pixel
    val groundY get() = viewH - pixel * 2f

    private val paint = Paint().apply { isAntiAlias = false }
    private val outlinePaint = Paint().apply { color = Color.argb(150, 40, 18, 10) }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(viewW, viewH)
    }

    // ---------------------------------------------------------------- çizim

    override fun onDraw(canvas: Canvas) {
        val cx = spriteCx
        val gy = groundY
        canvas.save()
        canvas.scale(scale, scale, cx, gy)

        if (rotationDeg == 0f) {
            val sw = spriteW * 0.42f * (1f - (lift / (headroom * 2f)).coerceIn(0f, 0.5f))
            rect.set(cx - sw, gy - pixel * 0.9f, cx + sw, gy + pixel * 0.9f)
            canvas.drawOval(rect, shadow)
        }

        val left = cx - spriteW / 2f
        val top = gy - spriteH - lift + bob
        drawProp(canvas, left, gy - spriteH)

        canvas.save()
        canvas.rotate(rotationDeg, cx, gy - spriteH / 2f)
        if (facingLeft) canvas.scale(-1f, 1f, cx, 0f)
        drawSprite(canvas, left, top)
        drawHat(canvas, left, top)
        drawTool(canvas, left, top)
        canvas.restore()

        drawPuffs(canvas)
        if (sparkle > 0f) drawSparkles(canvas, cx, top)
        if (notes > 0f) drawNotes(canvas, cx, top)
        if (heart > 0f) drawHeart(canvas, cx + pixel * 5f, (top - pixel * 3f - heart * pixel * 3f).coerceAtLeast(0f), heart)
        if (sleepy > 0f) drawZs(canvas, cx + pixel * 6f, top + pixel * 2f)
        canvas.restore()
    }

    private fun cell(canvas: Canvas, left: Float, top: Float, ux: Float, uy: Float, color: Int) {
        paint.color = color
        val x = left + ux * pixel
        val y = top + uy * pixel
        canvas.drawRect(x, y, x + pixel + 0.5f, y + pixel + 0.5f, paint)
    }

    private fun drawSprite(canvas: Canvas, left: Float, top: Float) {
        val f = sprites.frames[frame] ?: sprites.frames.getValue("stand")
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
                cell(canvas, left, top, x.toFloat(), y.toFloat(), c)
            }
        }
    }

    /** Desen: her karakter bir piksel; palette olmayan karakterler boş. */
    private fun art(canvas: Canvas, left: Float, top: Float, ox: Float, oy: Float, rows: Array<String>, pal: Map<Char, Int>) {
        rows.forEachIndexed { y, r ->
            r.forEachIndexed { x, ch ->
                val c = pal[ch]
                if (c != null) cell(canvas, left, top, ox + x, oy + y, c)
            }
        }
    }

    private fun drawHat(canvas: Canvas, left: Float, top: Float) {
        when (hat) {
            Hat.BERET -> art(canvas, left, top, 1f, -1f, arrayOf(
                "........BB....",
                "...BBBBBBBBB..",
                "..BBBBBBBBBBBB",
            ), mapOf('B' to C_BERET))
            Hat.CHEF -> art(canvas, left, top, 1f, -4f, arrayOf(
                "....WW..WW....",
                "...WWWWWWWWW..",
                "..WWWWWWWWWWW.",
                "...WWWWWWWWW..",
                "...WWWWWWWWW..",
                "...GGGGGGGGG..",
            ), mapOf('W' to Color.WHITE, 'G' to C_HAT_BAND))
            Hat.NONE -> {}
        }
    }

    private fun drawTool(canvas: Canvas, left: Float, top: Float) {
        val d = if (toolPhase == 0) 0f else 1f
        when (tool) {
            Tool.BRUSH -> {
                cell(canvas, left, top, 16f, 6f + d, C_WOOD)
                cell(canvas, left, top, 17f, 5f + d, C_WOOD)
                cell(canvas, left, top, 18f, 4f + d, paintColor)
            }
            Tool.SPOON -> {
                cell(canvas, left, top, 16f, 8f, C_METAL)
                cell(canvas, left, top, 17f + d, 9f, C_METAL)
                cell(canvas, left, top, 18f + d, 10f, C_METAL)
            }
            Tool.BROOM -> {
                cell(canvas, left, top, 16f, 8f, C_WOOD)
                cell(canvas, left, top, 16f, 9f, C_WOOD)
                cell(canvas, left, top, 17f, 10f, C_WOOD)
                cell(canvas, left, top, 17f, 11f, C_WOOD)
                art(canvas, left, top, 16f + d, 12f, arrayOf("YYYYY", "Y.Y.Y"), mapOf('Y' to C_STRAW))
            }
            Tool.NONE -> {}
        }
    }

    private fun drawProp(canvas: Canvas, left: Float, top: Float) {
        when (prop) {
            Prop.EASEL -> {
                val ox = if (propSide > 0) 19f else -17f
                art(canvas, left, top, ox, -1f, EASEL, mapOf('F' to C_EASEL, 'W' to C_CANVAS))
                val pic = picture ?: return
                val w = pic[0].length
                val shown = (paintProgress * pic.size * w).toInt()
                for (y in pic.indices) for (x in 0 until w) {
                    // fırça darbeleri satır satır, satır içinde karışık sırayla
                    val rank = y * w + PAINT_ORDER[x % PAINT_ORDER.size]
                    if (rank < shown) {
                        val c = PICTURE_PALETTE[pic[y][x]]
                        if (c != null) cell(canvas, left, top, ox + 1 + x, y.toFloat(), c)
                    }
                }
            }
            Prop.POT -> {
                val ox = if (propSide > 0) 18f else -12f
                val flick = ((time * 8f).toInt() % 2 == 0)
                art(canvas, left, top, ox, 7f, arrayOf(
                    ".GGGGGGGG.",
                    "GPPPPPPPPG",
                    "GPPPPPPPPG",
                    "GPPPPPPPPG",
                    ".PPPPPPPP.",
                    if (flick) "..F.FF.F.." else ".F.FF.F.F.",
                    ".KKKKKKKK.",
                ), mapOf('G' to C_POT_RIM, 'P' to C_POT, 'F' to if (flick) C_FLAME1 else C_FLAME2, 'K' to C_LOG))
                // kaynayan çorba yüzeyi
                for (x in 1..8) {
                    val bubble = sin(time * 6f + x * 1.7f) > 0.6f
                    cell(canvas, left, top, ox + x, 7f, if (bubble) C_SOUP_HI else C_SOUP)
                }
                drawSteam(canvas, left + (ox + 5f) * pixel, top + 6f * pixel)
            }
            Prop.BOWL -> {
                val ox = if (propSide > 0) 18f else -12f
                art(canvas, left, top, ox, 9f, arrayOf(
                    ".SSSSSSSS.",
                    "BBBBBBBBBB",
                    ".BBBBBBBB.",
                    "..BBBBBB..",
                ), mapOf('S' to C_SOUP, 'B' to C_BOWL))
                drawSteam(canvas, left + (ox + 5f) * pixel, top + 8f * pixel)
            }
            Prop.NONE -> {}
        }
    }

    private fun drawSteam(canvas: Canvas, x: Float, y: Float) {
        for (i in 0 until 3) {
            val ph = (time * 0.6f + i / 3f) % 1f
            soft.color = Color.argb((110 * (1f - ph)).toInt(), 255, 255, 255)
            val sx = x + sin(ph * 6f + i * 2f) * pixel * 1.5f
            canvas.drawCircle(sx, y - ph * pixel * 9f, pixel * (0.8f + ph * 1.6f), soft)
        }
    }

    private fun drawPuffs(canvas: Canvas) {
        for (p in puffs) {
            val a = 1f - p.age / PUFF_LIFE
            if (a <= 0f) continue
            soft.color = Color.argb((150 * a).toInt(), 190, 186, 178)
            val lx = p.x - winX
            val ly = p.y - winY - p.age * pixel * 4f
            canvas.drawCircle(lx, ly, pixel * (0.8f + p.age * 3f), soft)
        }
    }

    private fun drawSparkles(canvas: Canvas, cx: Float, top: Float) {
        text.textSize = pixel * 3.2f
        text.color = C_SPARKLE
        for (i in 0 until 5) {
            val ang = i * 1.256f + time * 1.5f
            val r = pixel * (9f + 2f * sin(time * 4f + i))
            text.alpha = (255 * sparkle * (0.5f + 0.5f * sin(time * 9f + i * 2f))).toInt().coerceIn(0, 255)
            canvas.drawText("✦", cx + cos(ang) * r - pixel, top + pixel * 7f + sin(ang) * r * 0.6f, text)
        }
        text.color = Color.WHITE
    }

    private fun drawNotes(canvas: Canvas, cx: Float, top: Float) {
        for (i in 0 until 3) {
            val ph = (time * 0.7f + i / 3f) % 1f
            text.alpha = (255 * notes * (1f - ph)).toInt().coerceIn(0, 255)
            text.textSize = pixel * (3f + ph)
            val side = if (i % 2 == 0) 1f else -1f
            canvas.drawText(if (i == 1) "♫" else "♪", cx + side * pixel * (6f + ph * 3f) - pixel, top + pixel * 2f - ph * pixel * 8f, text)
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, a: Float) {
        val rows = arrayOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
        paint.color = C_HEART
        paint.alpha = (255 * (1f - (a - 0.7f).coerceAtLeast(0f) / 0.3f)).toInt().coerceIn(0, 255)
        val p = pixel * 0.7f
        val ox = cx - 3.5f * p
        rows.forEachIndexed { y, r ->
            r.forEachIndexed { x, ch ->
                if (ch == 'X') canvas.drawRect(ox + x * p, cy + y * p, ox + (x + 1) * p + 0.5f, cy + (y + 1) * p + 0.5f, paint)
            }
        }
        paint.alpha = 255
    }

    private fun drawZs(canvas: Canvas, x: Float, y: Float) {
        for (i in 0 until 3) {
            val ph = ((time * 0.5f + i / 3f) % 1f)
            text.alpha = (255 * sleepy * (1f - ph)).toInt().coerceIn(0, 255)
            text.textSize = pixel * (2.5f + ph * 2.5f)
            canvas.drawText("z", x + ph * pixel * 4f, y - ph * pixel * 10f, text)
        }
    }

    companion object {
        const val PUFF_LIFE = 0.7f
        private val C_BERET = Color.parseColor("#8E1B2E")
        private val C_HAT_BAND = Color.parseColor("#D9D9D9")
        private val C_WOOD = Color.parseColor("#7A4A26")
        private val C_METAL = Color.parseColor("#B8BEC6")
        private val C_STRAW = Color.parseColor("#E2B356")
        private val C_EASEL = Color.parseColor("#6B4226")
        private val C_CANVAS = Color.parseColor("#F7F3E8")
        private val C_POT = Color.parseColor("#3B3F46")
        private val C_POT_RIM = Color.parseColor("#5A6069")
        private val C_FLAME1 = Color.parseColor("#FF9F1C")
        private val C_FLAME2 = Color.parseColor("#FFD166")
        private val C_LOG = Color.parseColor("#5C3A1E")
        private val C_SOUP = Color.parseColor("#E07A3F")
        private val C_SOUP_HI = Color.parseColor("#F4A261")
        private val C_BOWL = Color.parseColor("#F2E8CF")
        private val C_HEART = Color.parseColor("#FF5A7A")
        private val C_SPARKLE = Color.parseColor("#FFE27A")

        private val EASEL = arrayOf(
            "FFFFFFFFFFFFFF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FWWWWWWWWWWWWF",
            "FFFFFFFFFFFFFF",
            "..F...FF...F..",
            ".F....FF....F.",
            "F.....FF.....F",
        )
        private val PAINT_ORDER = intArrayOf(0, 3, 1, 5, 2, 7, 4, 9, 6, 11, 8, 10)

        val PICTURE_PALETTE = mapOf(
            'B' to Color.parseColor("#8EC5EC"), 'Y' to Color.parseColor("#FFD23F"),
            'C' to Color.parseColor("#2F6FB3"), 'W' to Color.parseColor("#FFFFFF"),
            'S' to Color.parseColor("#E9C98B"), 'P' to Color.parseColor("#F06A9B"),
            'G' to Color.parseColor("#3FA34D"), 'N' to Color.parseColor("#1F3550"),
            'R' to Color.parseColor("#E5484D"), 'O' to Color.parseColor("#D97757"),
            'K' to Color.parseColor("#2A1A14"), 'M' to Color.parseColor("#7A4A26"),
        )

        /** 12x9 küçük resimler: deniz, çiçek, kalp, Galata. */
        val PICTURES = arrayOf(
            arrayOf(
                "BBBBBBBBBBBB",
                "BBBBBBBYYBBB",
                "BBBBBBYYYYBB",
                "BBBBBBBYYBBB",
                "BBBBBBBBBBBB",
                "CCCCCCCCCCCC",
                "CWCCCCCWCCCC",
                "CCCCWCCCCCWC",
                "SSSSSSSSSSSS",
            ),
            arrayOf(
                "WWWWWWWWWWWW",
                "WWWWWPWWWWWW",
                "WWWWPYPWWWWW",
                "WWWWWPWWWWWW",
                "WWWWWGWWWWWW",
                "WWWGWGWWWWWW",
                "WWWWGGWWWWWW",
                "WWWWWGWWWWWW",
                "MMMMMMMMMMMM",
            ),
            arrayOf(
                "WWWWWWWWWWWW",
                "WWRRWWWRRWWW",
                "WRRRRWRRRRWW",
                "WRRRRRRRRRWW",
                "WWRRRRRRRWWW",
                "WWWRRRRRWWWW",
                "WWWWRRRWWWWW",
                "WWWWWRWWWWWW",
                "WWWWWWWWWWWW",
            ),
            arrayOf(
                "NNNNNNNNNNNN",
                "NNNNNYNNNNYN",
                "NNNNNONNNNNN",
                "NNNNOOONNYNN",
                "NNNNOWONNNNN",
                "NNNOOOOONNNN",
                "NNNOWOWONNNN",
                "NNNOOOOONNNN",
                "KKKKKKKKKKKK",
            ),
        )
        val PAINT_COLORS = intArrayOf(
            Color.parseColor("#2F6FB3"), Color.parseColor("#F06A9B"),
            Color.parseColor("#E5484D"), Color.parseColor("#D97757"),
        )
    }
}
