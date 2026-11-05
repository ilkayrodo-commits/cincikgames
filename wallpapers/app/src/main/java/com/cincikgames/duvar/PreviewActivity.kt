package com.cincikgames.duvar

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Tam ekran canlı önizleme + "Uygula". */
class PreviewActivity : AppCompatActivity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: ShaderRenderer
    private lateinit var def: WallpaperDef

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        def = Wallpapers.byId(intent.getStringExtra(EXTRA_ID))
        renderer = ShaderRenderer(this, def)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) renderer.touch(e.x / v.width, e.y / v.height)
                true
            }
            addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
                val w = r - l
                val h = b - t
                if (w > 0 && h > 0 && def.renderScale < 0.999f) {
                    holder.setFixedSize((w * def.renderScale).toInt(), (h * def.renderScale).toInt())
                }
            }
        }

        val root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        root.addView(TextView(this).apply {
            text = getString(R.string.back)
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.pill_bg)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(16), dp(44), 0, 0)
        })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(40))
        }
        bottom.addView(TextView(this).apply {
            text = def.title
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        })
        bottom.addView(TextView(this).apply {
            text = def.subtitle + "\n" + getString(R.string.touch_hint)
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFE0E0E8.toInt())
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(0, dp(4), 0, dp(14))
        })
        bottom.addView(TextView(this).apply {
            text = getString(R.string.apply)
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.apply_bg)
            setPadding(dp(48), dp(14), dp(48), dp(14))
            setOnClickListener { apply() }
        })
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
    }

    override fun onPause() {
        glView.onPause()
        super.onPause()
    }

    private fun apply() {
        Wallpapers.select(this, def)
        val wm = WallpaperManager.getInstance(this)
        if (wm.wallpaperInfo?.packageName == packageName) {
            // Zaten bizim canlı duvar kağıdımız: servis seçimi anında değiştirir.
            Toast.makeText(this, R.string.applied, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val component = ComponentName(this, LiveWallpaperService::class.java)
        val intents = listOf(
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component),
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
        )
        for (i in intents) {
            try {
                startActivity(i)
                Toast.makeText(this, R.string.set_hint, Toast.LENGTH_LONG).show()
                return
            } catch (e: ActivityNotFoundException) {
                // sıradakini dene
            }
        }
        Toast.makeText(this, R.string.no_live_wallpaper, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ID = "id"
    }
}
