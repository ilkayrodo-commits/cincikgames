package com.cincikgames.dost

import android.content.Context
import android.graphics.Color

/** assets/sprites.txt dosyasındaki piksel karelerini okur. */
class Sprites(context: Context) {

    /** outline: karakterin çevresindeki koyu dış çizgi pikselleri (her arka planda seçilsin). */
    class Frame(val w: Int, val h: Int, val pixels: IntArray, val outline: BooleanArray)

    val frames: Map<String, Frame>
    val width: Int
    val height: Int

    init {
        val palette = HashMap<Char, Int>()
        val rows = LinkedHashMap<String, MutableList<String>>()
        var current: String? = null
        context.assets.open("sprites.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trimEnd()
                if (line.isEmpty() || line.startsWith("#")) continue
                when {
                    line.startsWith("[") -> {
                        current = line.trim('[', ']')
                        rows[current!!] = mutableListOf()
                    }
                    current == null && line.length > 2 && line[1] == '=' -> {
                        val v = line.substring(2)
                        palette[line[0]] = if (v == "transparent") Color.TRANSPARENT else Color.parseColor(v)
                    }
                    current != null -> rows[current!!]!!.add(line)
                }
            }
        }
        width = rows.values.maxOf { r -> r.maxOf { it.length } }
        height = rows.values.maxOf { it.size }
        frames = rows.mapValues { (_, r) ->
            val px = IntArray(width * height)
            r.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch -> px[y * width + x] = palette[ch] ?: Color.TRANSPARENT }
            }
            val ol = BooleanArray(width * height)
            for (yy in 0 until height) for (xx in 0 until width) {
                if (Color.alpha(px[yy * width + xx]) != 0) continue
                var near = false
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = xx + dx
                    val ny = yy + dy
                    if (nx in 0 until width && ny in 0 until height && Color.alpha(px[ny * width + nx]) != 0) near = true
                }
                ol[yy * width + xx] = near
            }
            Frame(width, height, px, ol)
        }
    }
}
