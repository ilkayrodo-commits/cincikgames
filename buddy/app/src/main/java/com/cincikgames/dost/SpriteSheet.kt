package com.cincikgames.dost

/**
 * sprites.txt'yi Android'e bağımlı olmadan okur (birim testlerinde de çalışır).
 * Kareler harf ızgarası olarak tutulur: O gövde, L parlaklık, E göz, S gölge/bacak, . boş.
 */
class SpriteSheet(text: String) {

    val palette = HashMap<Char, Int>()
    val frames = LinkedHashMap<String, List<String>>()
    val width: Int
    val height: Int

    init {
        var current: String? = null
        val rows = LinkedHashMap<String, MutableList<String>>()
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            if (line.isEmpty() || line.startsWith("#")) continue
            when {
                line.startsWith("[") -> {
                    current = line.trim('[', ']')
                    rows[current] = mutableListOf()
                }
                current == null && line.length > 2 && line[1] == '=' -> {
                    val v = line.substring(2)
                    palette[line[0]] = if (v == "transparent") 0 else argb(v)
                }
                current != null -> rows[current]!!.add(line)
            }
        }
        width = rows.values.maxOf { r -> r.maxOf { it.length } }
        height = rows.values.maxOf { it.size }
        for ((k, r) in rows) frames[k] = List(height) { y -> (r.getOrElse(y) { "" }).padEnd(width, '.') }
    }

    companion object {
        fun argb(hex: String): Int {
            val h = hex.removePrefix("#")
            val v = h.toLong(16).toInt()
            return if (h.length == 6) (0xFF shl 24) or v else v
        }
    }
}

/**
 * Çizime hazır piksel ızgarası. (ox, oy): karakterin 16x14'lük asıl karesinin bu
 * ızgara içindeki başlangıcı (şapka/palet gibi taşan parçalar için kenar payı).
 */
class PixelGrid(val w: Int, val h: Int, val ox: Int, val oy: Int, val px: IntArray, val outline: BooleanArray)
