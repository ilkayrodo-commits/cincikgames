package com.cincikgames.dost

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Gerçek Android çizimiyle ekran görüntüleri (build/shots). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    private val out = File("build/shots").apply { mkdirs() }

    private fun shot(v: View, w: Int, h: Int, name: String, bg: Int = Color.rgb(30, 60, 80)) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun chaosEffects() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val v = ChaosView(ctx)
        val w = 1080
        val h = 2400
        v.throwThing(false, 500f, 2000f, 300f, 700f)
        v.throwThing(true, 500f, 2000f, 800f, 1100f)
        v.throwThing(false, 500f, 2000f, 650f, 1700f)
        repeat(60) { v.update(1 / 30f) }       // 2 sn: lekeler oturdu, damlalar aktı
        v.startSpray(420f, 1350f)
        repeat(50) { v.update(1 / 30f) }
        v.crack(780f, 520f)
        repeat(20) { v.update(1 / 30f) }
        shot(v, w, h, "chaos")
        v.wipe = 0.45f
        shot(v, w, h, "chaos_wipe")
    }

    @Test
    fun buddyTools() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tools = listOf(BuddyView.Tool.TOMATO, BuddyView.Tool.EGG, BuddyView.Tool.SPRAY, BuddyView.Tool.HAMMER, BuddyView.Tool.SPONGE)
        for ((i, t) in tools.withIndex()) {
            val b = BuddyView(ctx, Sprites.get(ctx), 4.2f * 3f)
            b.outfit = Catalog.presets[i].outfit
            b.tool = t
            b.toolPhase = 1
            b.padL = 7f; b.padR = 7f
            b.frame = if (t == BuddyView.Tool.TOMATO || t == BuddyView.Tool.EGG) "stretch" else "stand"
            shot(b, b.viewW, b.viewH, "tool_$i")
        }
    }

    @Test
    fun wardrobeStage() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        for ((i, p) in Catalog.presets.take(4).withIndex()) {
            val v = StageView(ctx)
            v.outfit = p.outfit
            shot(v, 1000, 640, "stage_$i", Color.rgb(20, 22, 28))
        }
    }
}
