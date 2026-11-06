package com.cincikgames.dost

import android.content.Context
import android.content.SharedPreferences

/** Kalıcı ayarlar: isim, kombin, kayıtlı kombinler. */
object Store {
    const val KEY_NAME = "name"
    const val KEY_OUTFIT = "outfit"
    private const val KEY_SAVED = "saved"

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("gezgin", Context.MODE_PRIVATE)

    fun name(c: Context): String? = prefs(c).getString(KEY_NAME, null)
    fun displayName(c: Context): String = name(c) ?: "Minik Gezgin"
    fun setName(c: Context, n: String) = prefs(c).edit().putString(KEY_NAME, n.trim().take(16)).apply()

    fun outfit(c: Context): Outfit = Outfit.decode(prefs(c).getString(KEY_OUTFIT, null))
    fun setOutfit(c: Context, o: Outfit) = prefs(c).edit().putString(KEY_OUTFIT, o.encode()).apply()

    /** Kayıtlı kombinler: "ad\tkod" satırları, en yenisi başta. */
    fun saved(c: Context): List<Pair<String, Outfit>> =
        (prefs(c).getString(KEY_SAVED, "") ?: "").lines().filter { it.contains('\t') }.map {
            val (n, o) = it.split('\t', limit = 2)
            n to Outfit.decode(o)
        }

    fun save(c: Context, name: String, o: Outfit) {
        val list = listOf(name to o) + saved(c).filter { it.first != name }
        prefs(c).edit().putString(KEY_SAVED, list.take(20).joinToString("\n") { "${it.first}\t${it.second.encode()}" }).apply()
    }

    fun delete(c: Context, name: String) {
        val list = saved(c).filter { it.first != name }
        prefs(c).edit().putString(KEY_SAVED, list.joinToString("\n") { "${it.first}\t${it.second.encode()}" }).apply()
    }

    /** Kaos modu için isteğe bağlı duvar kağıdı dosyası. */
    fun wallpaperFile(c: Context) = java.io.File(c.filesDir, "wallpaper.png")
}
