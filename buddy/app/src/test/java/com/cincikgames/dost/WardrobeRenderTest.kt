package com.cincikgames.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gardırobu bilgisayarda çizer: her kombin her pozda ve her parça tek başına.
 * Çıktı: build/wardrobe klasöründe PPM resimleri (gözle kontrol için) + temel doğrulamalar.
 */
class WardrobeRenderTest {

    private val sheet = SpriteSheet(File("src/main/assets/sprites.txt").readText())
    private val dresser = Dresser(sheet)
    private val out = File("build/wardrobe").apply { mkdirs() }
    private val poses = listOf("stand", "walk1", "walk2", "blink", "happy", "hop", "sit", "dangle", "wave1", "dance1", "dance2", "stretch")
    private val S = 8

    /** Basit RGB tuval; PPM olarak yazılır (Android test ortamında java.awt yok). */
    private class Img(val w: Int, val h: Int) {
        val px = IntArray(w * h) { 0x1E3C50 }
        fun set(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) px[y * w + x] = c }
        fun write(f: File) {
            val header = "P6\n$w $h\n255\n".toByteArray()
            val body = ByteArray(w * h * 3)
            for (i in px.indices) {
                body[i * 3] = (px[i] shr 16).toByte(); body[i * 3 + 1] = (px[i] shr 8).toByte(); body[i * 3 + 2] = px[i].toByte()
            }
            f.writeBytes(header + body)
        }
    }

    private fun draw(img: Img, g: PixelGrid, x0: Int, y0: Int) {
        for (y in 0 until g.h) for (x in 0 until g.w) {
            val c = g.px[y * g.w + x]
            val col = when {
                c != 0 -> c
                g.outline[y * g.w + x] -> 0x28120A
                else -> continue
            }
            for (dy in 0 until S) for (dx in 0 until S) img.set(x0 + x * S + dx, y0 + y * S + dy, col and 0xFFFFFF)
        }
    }

    private fun sheetOf(name: String, rows: List<List<PixelGrid>>) {
        val cw = (sheet.width + Dresser.ML + Dresser.MR) * S + 10
        val ch = (sheet.height + Dresser.MT + Dresser.MB) * S + 10
        val img = Img(cw * rows.maxOf { it.size }, ch * rows.size)
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, g -> draw(img, g, c * cw + 5, r * ch + 5) } }
        img.write(File(out, "$name.ppm"))
    }

    @Test
    fun catalogHasTenPerCategory() {
        for (c in Category.values()) assertEquals(c.title, 10, Catalog.of(c).size)
        assertEquals(60, Catalog.byId.size)
        for (p in Catalog.presets) for (c in Category.values()) p.outfit.get(c)?.let { assertNotNull(it, Catalog.byId[it]) }
    }

    @Test
    fun outfitEncodeRoundTrip() {
        val o = Catalog.presets[2].outfit
        assertEquals(o, Outfit.decode(o.encode()))
        assertEquals(Outfit(), Outfit.decode(""))
    }

    @Test
    fun renderPresetsInAllPoses() {
        val rows = Catalog.presets.map { p -> poses.map { dresser.dress(it, p.outfit) } }
        sheetOf("presets", rows)
        // giydirilmiş kare çıplak kareden farklı olmalı
        val naked = dresser.dress("stand", Outfit())
        for (p in Catalog.presets) assertTrue(p.name, !dresser.dress("stand", p.outfit).px.contentEquals(naked.px))
    }

    @Test
    fun renderEveryItem() {
        for (c in Category.values()) {
            val rows = listOf(
                Catalog.of(c).map { dresser.dress("stand", Outfit().with(c, it.id)) },
                Catalog.of(c).map { dresser.dress("sit", Outfit().with(c, it.id)) },
            )
            sheetOf("items_${c.name.lowercase()}", rows)
        }
    }

    @Test
    fun namesLookFine() {
        val r = kotlin.random.Random(1)
        repeat(20) { val n = NameGen.next(r); assertTrue(n, n.length in 2..12) }
    }
}
