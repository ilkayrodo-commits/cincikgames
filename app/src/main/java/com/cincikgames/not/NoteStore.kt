package com.cincikgames.not

import android.content.Context

object NoteStore {
    private const val PREFS = "notes"
    private const val KEY = "note"

    fun load(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun save(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).apply()
    }
}
