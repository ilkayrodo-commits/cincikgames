package com.cincikgames.duvar

import android.content.Context

/**
 * Bir duvar kağıdı: hangi shader, hangi dokular ve hangi çözünürlük ölçeğinde
 * çizileceği. Ağır sahneler pil için düşük çözünürlükte çizilip büyütülür.
 */
data class WallpaperDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val shader: String,
    val textures: List<String> = emptyList(),
    val renderScale: Float = 0.6f,
    /** Telefon eğimine göre derinlik (paralaks) kullanır mı. */
    val usesTilt: Boolean = false,
)

object Wallpapers {
    val all = listOf(
        WallpaperDef("galata", "Galata Saçları", "Zeminden gökyüzüne yükselen saç spirali · telefonu eğ, dokun",
            "galata", listOf("textures/galata.jpg", "textures/galata_flow.png", "textures/galata_aux.png"), 1.0f, usesTilt = true),
        WallpaperDef("istanbul", "İstanbul Gecesi", "Hilal, aydınlatılmış camiler, boğazda vapur", "istanbul", renderScale = 0.75f),
        WallpaperDef("aurora", "Kuzey Işıkları", "Karlı dağlar ve gölde dans eden aurora", "aurora", renderScale = 0.5f),
        WallpaperDef("synthwave", "Retro Gün Batımı", "80'ler neon ızgara ve çizgili güneş", "synthwave", renderScale = 0.75f),
        WallpaperDef("nebula", "Derin Uzay", "Akan bulutsular ve kayan yıldızlar", "nebula", renderScale = 0.5f),
        WallpaperDef("rain", "Yağmurlu Cam", "Camda kayan damlalar, bulanık şehir ışıkları", "rain", renderScale = 0.6f),
        WallpaperDef("ocean", "Okyanus Altı", "Işık huzmeleri, kabarcıklar, yosunlar", "ocean", renderScale = 0.5f),
        WallpaperDef("sakura", "Sakura", "Rüzgarda süzülen kiraz çiçeği yaprakları", "sakura", renderScale = 0.6f),
        WallpaperDef("marble", "Altın Mermer", "Siyah mermerde akan altın damarlar", "marble", renderScale = 0.6f),
        WallpaperDef("lava", "Lav Lambası", "Yavaşça birleşip ayrılan sıcak damlalar", "lava", renderScale = 0.5f),
    )

    fun byId(id: String?): WallpaperDef = all.firstOrNull { it.id == id } ?: all.first()

    private const val PREFS = "wallpaper"
    const val KEY_SELECTED = "selected"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(context: Context): WallpaperDef = byId(prefs(context).getString(KEY_SELECTED, null))

    fun select(context: Context, def: WallpaperDef) {
        prefs(context).edit().putString(KEY_SELECTED, def.id).apply()
    }
}
