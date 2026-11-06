package com.cincikgames.dost

/**
 * Karakteri giydirir. Kıyafetler her kare için ayrı çizilmez: karenin harflerinden
 * gövde, kollar, bel, bacaklar, ayaklar, gözler ve başın tepesi bulunur; kumaşlar bu
 * bölgelere boyanır, şapka ve gözlük bu noktalara yerleştirilir. Böylece kıyafet
 * yürürken, otururken, dans ederken ve tırmanırken de doğru yerde durur.
 */
class Dresser(private val sheet: SpriteSheet) {

    private val cache = HashMap<String, PixelGrid>()

    fun clear() = cache.clear()

    fun dress(frame: String, outfit: Outfit, hideHat: Boolean = false): PixelGrid {
        val key = frame + "#" + outfit.encode() + "#" + hideHat
        return cache.getOrPut(key) { build(frame, outfit, hideHat) }
    }

    private fun build(name: String, outfit: Outfit, hideHat: Boolean): PixelGrid {
        val rows = sheet.frames[name] ?: sheet.frames.getValue("stand")
        val sw = sheet.width
        val sh = sheet.height
        val gw = sw + ML + MR
        val gh = sh + MT + MB
        val px = IntArray(gw * gh)

        fun inGrid(x: Int, y: Int) = x + ML in 0 until gw && y + MT in 0 until gh
        fun set(x: Int, y: Int, c: Int) { if (inGrid(x, y)) px[(y + MT) * gw + x + ML] = c }
        fun get(x: Int, y: Int): Int = if (inGrid(x, y)) px[(y + MT) * gw + x + ML] else 0
        fun ch(x: Int, y: Int): Char = if (x in 0 until sw && y in 0 until sh) rows[y][x] else '.'

        for (y in 0 until sh) for (x in 0 until sw) set(x, y, sheet.palette[ch(x, y)] ?: 0)

        // ---- karenin anatomisi
        val eyes = ArrayList<Pair<Int, Int>>()
        for (y in 0 until sh) for (x in 0 until sw) if (ch(x, y) == 'E') eyes.add(x to y)
        var headTop = sh
        for (y in 0 until sh) for (x in 2..13) if ((ch(x, y) == 'O' || ch(x, y) == 'L') && y < headTop) headTop = y
        var band = sh
        for (y in 0 until sh) if (rows[y].count { it == 'S' } >= 8) { band = y; break }
        val eyeBottom = eyes.maxOfOrNull { it.second } ?: (headTop + 3)
        fun isTorso(x: Int, y: Int) = ch(x, y) == 'O' && x in 2..13 && y > eyeBottom && y < band
        fun isArm(x: Int, y: Int) = ch(x, y) == 'O' && (x < 2 || x > 13)
        // her bacak sütununun en alt pikseli ayakkabı, bir üstü konç
        val shoeBottom = HashMap<Int, Int>()
        for (x in 0 until sw) for (y in sh - 1 downTo band + 1) {
            if (ch(x, y) == 'S') { shoeBottom[x] = y; break }
        }
        fun isLeg(x: Int, y: Int) = ch(x, y) == 'S' && y > band

        // ---- pantolon
        val pants = item(outfit.pants)?.look as? Look.Fabric
        if (pants != null) {
            for (x in 0 until sw) if (ch(x, band) == 'S') set(x, band, shade(fabric(pants, x, band), 0.9f))
            for (y in band + 1 until sh) for (x in 0 until sw) {
                if (!isLeg(x, y) || shoeBottom[x] == y) continue
                if (pants.shorts && y > band + 1) continue
                set(x, y, fabric(pants, x, y))
            }
        }

        // ---- ayakkabı
        val shoe = item(outfit.shoes)?.look as? Look.Shoe
        if (shoe != null) {
            for ((x, by) in shoeBottom) {
                val upper = by - 1
                val hasUpper = isLeg(x, upper)
                fun toe(dx: Int, c: Int) {
                    if (ch(x + dx, by) == '.' && get(x + dx, by) == 0) set(x + dx, by, c)
                }
                when (shoe.style) {
                    ShoeStyle.SNEAKER -> { set(x, by, shoe.base); toe(1, shoe.second) }
                    ShoeStyle.BOOT -> { set(x, by, shoe.base); if (hasUpper) set(x, upper, shoe.second); toe(1, shoe.base) }
                    ShoeStyle.SLIPPER -> { set(x, by, shoe.second); toe(1, shoe.base) }
                    ShoeStyle.RAIN -> { set(x, by, shoe.base); if (hasUpper) set(x, upper, shoe.base); toe(1, shoe.second) }
                    ShoeStyle.PATENT -> { set(x, by, shoe.base); toe(1, shoe.second) }
                    ShoeStyle.CHECKER -> {
                        set(x, by, if (x % 2 == 0) shoe.base else shoe.second)
                        toe(1, if (x % 2 == 0) shoe.second else shoe.base)
                    }
                    ShoeStyle.SOCK -> {
                        set(x, by, shoe.base)
                        if (hasUpper) set(x, upper, shoe.second)
                    }
                    ShoeStyle.FLIPPER -> { set(x, by, shoe.base); toe(1, shoe.base); toe(2, shoe.second) }
                    ShoeStyle.HEEL -> { set(x, by, shoe.base); if (hasUpper) set(x, upper, shoe.second); toe(1, shoe.base) }
                    ShoeStyle.LIGHT -> { set(x, by, shoe.base); toe(1, shoe.second) }
                }
            }
        }

        // ---- tişört
        val shirt = item(outfit.shirt)?.look as? Look.Fabric
        if (shirt != null) {
            for (y in 0 until sh) for (x in 0 until sw) {
                if (isTorso(x, y)) {
                    val c = fabric(shirt, x, y)
                    set(x, y, if (y == band - 1) shade(c, 0.88f) else c)
                } else if (isArm(x, y) && (shirt.longSleeve || x == 1 || x == 14)) {
                    set(x, y, fabric(shirt, x, y))
                }
            }
            shirt.glyph?.let { g -> stamp(g, shirt.glyphColor, eyeBottom + 1, ::isTorso, ::set) }
        }

        // ---- ceket
        val jacket = item(outfit.jacket)?.look as? Look.Fabric
        if (jacket != null) {
            for (y in 0 until sh) for (x in 0 until sw) {
                if (isTorso(x, y) && (!jacket.open || x <= 5 || x >= 10)) {
                    val c = fabric(jacket, x, y)
                    set(x, y, if (y == band - 1) shade(c, 0.88f) else c)
                } else if (isArm(x, y)) {
                    set(x, y, jacket.sleeve ?: fabric(jacket, x, y))
                }
            }
            if (jacket.open) {
                // yaka: açık önün kenarları biraz koyu
                for (y in eyeBottom + 1 until band) {
                    if (isTorso(5, y)) set(5, y, shade(fabric(jacket, 5, y), 0.8f))
                    if (isTorso(10, y)) set(10, y, shade(fabric(jacket, 10, y), 0.8f))
                }
            }
        }

        // ---- gözlük
        val glasses = item(outfit.glasses)?.look as? Look.Glasses
        if (glasses != null && eyes.isNotEmpty()) drawGlasses(glasses, eyes, ::set, ::get)

        // ---- şapka
        val hat = item(outfit.hat)?.look as? Look.Hat
        if (hat != null && !hideHat && headTop < sh) {
            hat.art.forEachIndexed { r, line ->
                line.forEachIndexed { cx, chr ->
                    val c = hat.colors[chr] ?: return@forEachIndexed
                    set(hat.x0 + cx, headTop - hat.baseRow + r, c)
                }
            }
        }

        // ---- dış çizgi
        val outline = BooleanArray(gw * gh)
        for (y in 0 until gh) for (x in 0 until gw) {
            if (px[y * gw + x] != 0) continue
            outline[y * gw + x] = (x > 0 && px[y * gw + x - 1] != 0) || (x < gw - 1 && px[y * gw + x + 1] != 0) ||
                (y > 0 && px[(y - 1) * gw + x] != 0) || (y < gh - 1 && px[(y + 1) * gw + x] != 0)
        }
        return PixelGrid(gw, gh, ML, MT, px, outline)
    }

    private fun stamp(g: Glyph, color: Int, top: Int, ok: (Int, Int) -> Boolean, set: (Int, Int, Int) -> Unit) {
        g.rows.forEachIndexed { r, line ->
            line.forEachIndexed { cx, chr ->
                val x = 6 + cx
                val y = top + r
                if (chr == 'X' && ok(x, y)) set(x, y, color)
            }
        }
    }

    private fun drawGlasses(
        g: Look.Glasses,
        eyes: List<Pair<Int, Int>>,
        set: (Int, Int, Int) -> Unit,
        get: (Int, Int) -> Int,
    ) {
        class Lens(val x0: Int, val x1: Int)
        val left = eyes.filter { it.first < 8 }
        val right = eyes.filter { it.first >= 8 }
        val top = eyes.minOf { it.second }
        val bot = eyes.maxOf { it.second }
        val lenses = listOf(left, right).filter { it.isNotEmpty() }.map { e ->
            Lens(e.minOf { it.first } - 1, e.maxOf { it.first } + 1)
        }
        if (lenses.isEmpty()) return
        val l = lenses.first()
        val r = lenses.last()

        fun temples(row: Int) {
            for (x in 2 until l.x0) set(x, row, g.frame)
            for (x in r.x1 + 1..13) set(x, row, g.frame)
            for (x in l.x1 + 1 until r.x0) set(x, row, g.frame)
        }
        fun fill(lens: Lens, y0: Int, y1: Int, c: Int) { for (y in y0..y1) for (x in lens.x0..lens.x1) set(x, y, c) }
        fun ring(lens: Lens, y0: Int, y1: Int, c: Int) {
            for (x in lens.x0..lens.x1) { set(x, y0, c); set(x, y1, c) }
            for (y in y0..y1) { set(lens.x0, y, c); set(lens.x1, y, c) }
        }

        when (g.style) {
            LensStyle.SUN -> {
                lenses.forEach { fill(it, top, bot, g.lens); for (x in it.x0..it.x1) set(x, top - 1, g.frame) }
                temples(top)
            }
            LensStyle.NERD -> {
                lenses.forEach { ring(it, top - 1, bot + 1, g.frame) }
                temples(top)
            }
            LensStyle.HEART -> {
                lenses.forEach {
                    set(it.x0, top - 1, g.lens); set(it.x1, top - 1, g.lens)
                    for (x in it.x0..it.x1) set(x, top, g.lens)
                    for (x in it.x0 + 1 until it.x1) set(x, top + 1, g.lens)
                    if (bot > top + 1) for (x in it.x0 + 1 until it.x1) set(x, bot, g.lens)
                }
                temples(top)
            }
            LensStyle.STAR -> {
                lenses.forEach {
                    for (x in it.x0 + 1 until it.x1) { set(x, top - 1, g.lens); set(x, top + 1, g.lens) }
                    for (x in it.x0..it.x1) set(x, top, g.lens)
                    set((it.x0 + it.x1) / 2, top, g.frame)
                }
                temples(top)
            }
            LensStyle.SKI -> {
                for (y in top..bot) for (x in 2..13) set(x, y, if (x < 8) g.lens else g.lens2)
                for (y in top..bot) { set(2, y, g.frame); set(13, y, g.frame) }
                set(1, top, g.frame); set(14, top, g.frame)
                set(4, top, mix(g.lens2, 0xFFFFFFFF.toInt(), 0.6f))
            }
            LensStyle.MONOCLE -> {
                ring(r, top - 1, bot + 1, g.frame)
                set(r.x1, bot + 2, g.frame)
                set(r.x1 + 1, bot + 3, g.frame)
            }
            LensStyle.THREE_D -> {
                fill(l, top, bot, g.lens)
                fill(r, top, bot, g.lens2)
                lenses.forEach { for (x in it.x0..it.x1) set(x, top - 1, g.frame) }
                temples(top)
            }
            LensStyle.PILOT -> {
                lenses.forEach {
                    for (x in it.x0..it.x1) set(x, top - 1, g.frame)
                    fill(it, top, bot, g.lens)
                    for (x in it.x0 + 1 until it.x1) set(x, bot + 1, g.lens)
                }
                temples(top - 1)
            }
            LensStyle.DEAL -> {
                lenses.forEach {
                    fill(it, top, bot, g.lens)
                    set(it.x0, top, 0xFFFFFFFF.toInt())
                }
                for (x in 2..13) if (get(x, top) != 0xFFFFFFFF.toInt()) set(x, top, g.lens)
            }
            LensStyle.RETRO -> {
                lenses.forEach { lens ->
                    for (y in top..bot) for (x in lens.x0 + 1 until lens.x1) set(x, y, mix(get(x, y), g.lens, 0.55f))
                    ring(lens, top - 1, bot + 1, g.frame)
                }
                temples(top)
            }
        }
    }

    private fun item(id: String?): Item? = id?.let { Catalog.byId[it] }

    companion object {
        const val ML = 3
        const val MR = 3
        const val MT = 6
        const val MB = 1

        private fun hash(x: Int, y: Int): Int = ((x * 73856093) xor (y * 19349663) xor 0x5bd1e995) and 0x7fffffff

        fun fabric(f: Look.Fabric, x: Int, y: Int): Int = when (f.pattern) {
            Pattern.SOLID -> f.base
            Pattern.STRIPES_H -> if (y % 2 == 0) f.base else f.second
            Pattern.STRIPES_V -> if (x % 2 == 0) f.base else f.second
            Pattern.DOTS -> if ((x * 3 + y * 5) % 7 == 0) f.second else f.base
            Pattern.CHECK -> if (((x / 2) + y) % 2 == 0) f.base else f.second
            Pattern.CAMO -> when ((hash(x / 2, y) % 3)) { 0 -> f.base; 1 -> f.second; else -> f.third }
            Pattern.FLORAL -> if ((x + 2 * y) % 5 == 0) f.second else if ((3 * x + y) % 7 == 0) f.third else f.base
            Pattern.KNIT -> if ((x + y) % 3 == 0) f.second else f.base
            Pattern.SPARKLE -> if (hash(x, y) % 4 == 0) f.second else f.base
            Pattern.PUFFER -> if (y % 2 == 1) f.second else f.base
            Pattern.SIDE_STRIPE -> if (x == 2 || x == 13 || x % 5 == 3) f.second else f.base
            Pattern.SEAMS -> if (x == 7 || x == 8) f.second else f.base
        }

        fun shade(c: Int, k: Float): Int {
            val a = c ushr 24
            val r = ((c shr 16 and 0xFF) * k).toInt().coerceIn(0, 255)
            val g = ((c shr 8 and 0xFF) * k).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * k).toInt().coerceIn(0, 255)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun mix(a: Int, b: Int, t: Float): Int {
            if (a == 0) return b
            fun ch(c: Int, s: Int) = c shr s and 0xFF
            val r = (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t).toInt()
            val g = (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t).toInt()
            val bl = (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }
}

/** İsim üretici: hazır sevimli isimler ve hece birleştirme. */
object NameGen {
    private val ready = listOf(
        "Pıtırcık", "Fıstık", "Zıpzıp", "Turunç", "Cıvıl", "Bıcır", "Pofuduk", "Minnoş", "Cıncık", "Tontiş",
        "Pişmaniye", "Lokum", "Badem", "Kıpır", "Şıpşıp", "Mandalina", "Biber", "Pamuk", "Cimcime", "Tostik",
        "Gofret", "Patates", "Pıtı", "Leblebi", "Kuki", "Zeytin", "Simit", "Fındık", "Ponçik", "Pırpır",
    )
    private val a = listOf("Pı", "Fı", "Zı", "Cı", "Bı", "Tu", "Mı", "Kı", "Po", "Lo", "Bü", "Şı", "Nu", "Pa", "Mi")
    private val b = listOf("tır", "cık", "pız", "nık", "rış", "fıs", "lu", "pi", "dik", "mış", "por", "zo", "tuş")
    private val c = listOf("cık", "tık", "şık", "iş", "o", "i", "pık", "ko", "ş", "")

    fun next(rnd: kotlin.random.Random = kotlin.random.Random): String =
        if (rnd.nextBoolean()) ready.random(rnd) else (a.random(rnd) + b.random(rnd) + c.random(rnd))
}
