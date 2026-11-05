package com.cincikgames.dost

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Simgeye dokununca açılır. İzin varsa karakteri ekrana çıkarıp hemen kapanır;
 * yoksa izni nasıl açacağını anlatır.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.canDrawOverlays(this)) {
            launchBuddy()
            return
        }
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((28 * d).toInt(), 0, (28 * d).toInt(), 0)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.perm_title)
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.perm_text)
            textSize = 16f
            setTextColor(0xFFD0D4DC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (16 * d).toInt(), 0, (24 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.perm_button)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.button_bg)
            setPadding((40 * d).toInt(), (14 * d).toInt(), (40 * d).toInt(), (14 * d).toInt())
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.perm_xiaomi)
            textSize = 13f
            setTextColor(0xFF8A90A0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, (28 * d).toInt(), 0, 0)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) launchBuddy()
    }

    private fun launchBuddy() {
        ContextCompat.startForegroundService(this, Intent(this, BuddyService::class.java))
        finish()
        overridePendingTransition(0, 0)
    }
}
