package com.cincikgames.duvar

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView

/** Galeri: 10 duvar kağıdının küçük resimleri, dokununca önizleme açılır. */
class MainActivity : AppCompatActivity() {

    private lateinit var grid: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(16), dp(20), dp(16), 0)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.gallery_subtitle)
            textSize = 14f
            setTextColor(0xFF9AA0B0.toInt())
            setPadding(0, dp(4), 0, dp(14))
        })
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        buildGrid()
    }

    private fun buildGrid() {
        grid.removeAllViews()
        val inUse = ourWallpaperActive()
        val selected = Wallpapers.selected(this).id
        Wallpapers.all.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, def ->
                val lp = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    if (i == 0) marginEnd = dp(6) else marginStart = dp(6)
                    bottomMargin = dp(12)
                }
                row.addView(card(def, inUse && def.id == selected), lp)
            }
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            grid.addView(row)
        }
        grid.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(24)))
    }

    private fun card(def: WallpaperDef, active: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(dp(8), dp(8), dp(8), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PreviewActivity::class.java).putExtra(PreviewActivity.EXTRA_ID, def.id))
            }
        }
        val frame = FrameLayout(this)
        val img = object : AppCompatImageView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, h)
                val width = measuredWidth
                setMeasuredDimension(width, (width * 2.1f).toInt())
            }
        }.apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundResource(R.drawable.thumb_clip)
            clipToOutline = true
            assets.open("thumbs/${def.id}.jpg").use { setImageBitmap(BitmapFactory.decodeStream(it)) }
        }
        frame.addView(img, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        if (active) {
            frame.addView(TextView(this).apply {
                text = getString(R.string.in_use)
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundResource(R.drawable.pill_bg)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(8), dp(8), 0, 0)
            })
        }
        card.addView(frame)
        card.addView(TextView(this).apply {
            text = def.title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(8), dp(4), 0)
        })
        card.addView(TextView(this).apply {
            text = def.subtitle
            textSize = 12f
            setTextColor(0xFF9AA0B0.toInt())
            maxLines = 2
            setPadding(dp(4), dp(2), dp(4), 0)
        })
        return card
    }

    private fun ourWallpaperActive(): Boolean =
        WallpaperManager.getInstance(this).wallpaperInfo?.packageName == packageName

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
