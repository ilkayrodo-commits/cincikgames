package com.cincikgames.dost

import android.content.Context
import android.graphics.Bitmap

/** Kareler + giydirme motoru (uygulama içinde tek örnek). */
class Sprites private constructor(context: Context) {

    val sheet = SpriteSheet(context.assets.open("sprites.txt").bufferedReader(Charsets.UTF_8).use { it.readText() })
    val dresser = Dresser(sheet)
    val width get() = sheet.width
    val height get() = sheet.height

    /** Giydirilmiş kareyi bitmap'e çevirir (galeri küçük resimleri için). */
    fun bitmap(frame: String, outfit: Outfit, scale: Int, outline: Boolean = true): Bitmap {
        val g = dresser.dress(frame, outfit)
        val bmp = Bitmap.createBitmap(g.w * scale, g.h * scale, Bitmap.Config.ARGB_8888)
        val row = IntArray(g.w * scale)
        for (y in 0 until g.h) {
            for (x in 0 until g.w) {
                val c = g.px[y * g.w + x]
                val col = when {
                    c != 0 -> c
                    outline && g.outline[y * g.w + x] -> 0x9628120A.toInt()
                    else -> 0
                }
                for (k in 0 until scale) row[x * scale + k] = col
            }
            for (k in 0 until scale) bmp.setPixels(row, 0, row.size, 0, y * scale + k, row.size, 1)
        }
        return bmp
    }

    companion object {
        @Volatile private var inst: Sprites? = null
        fun get(context: Context): Sprites = inst ?: synchronized(this) {
            inst ?: Sprites(context.applicationContext).also { inst = it }
        }
    }
}
