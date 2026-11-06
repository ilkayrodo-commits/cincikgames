package com.cincikgames.dost

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * Uygulamanın içi: izin yoksa izin ekranı; varsa karakteri ekrana çağırır ve
 * gardırobu gösterir (isim, hazır/kayıtlı kombinler, 6 kategori x 10 parça).
 */
class MainActivity : AppCompatActivity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Number) = (v.toFloat() * d).toInt()

    private lateinit var sprites: Sprites
    private var outfit = Outfit()
    private var category = Category.HAT
    private var built = false

    private lateinit var nameView: TextView
    private lateinit var stage: StageView
    private lateinit var tabs: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var savedRow: LinearLayout
    private lateinit var savedTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sprites = Sprites.get(this)
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.canDrawOverlays(this)) {
            if (!built) showPermission()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, BuddyService::class.java))
        if (!built) {
            buildWardrobe()
            built = true
            if (Store.name(this) == null) askName(first = true)
        } else {
            outfit = Store.outfit(this)
            refresh()
        }
    }

    // ------------------------------------------------------------ izin ekranı

    private fun showPermission() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), 0, dp(28), 0)
        }
        root.addView(text(getString(R.string.perm_title), 26f, bold = true).apply { gravity = Gravity.CENTER })
        root.addView(text(getString(R.string.perm_text), 16f, color = 0xFFD0D4DC.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(24))
        })
        root.addView(button(getString(R.string.perm_button), primary = true) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(text(getString(R.string.perm_xiaomi), 13f, color = 0xFF8A90A0.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        })
        setContentView(root)
    }

    // ------------------------------------------------------------ gardırop

    private fun buildWardrobe() {
        outfit = Store.outfit(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }

        // isim
        nameView = text(Store.displayName(this), 28f, bold = true).apply {
            setOnClickListener { askName(first = false) }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(nameView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        head.addView(button("✏️ İsim", primary = false) { askName(first = false) })
        col.addView(head)
        col.addView(text("Gardırop · Seç, giydir, ekranda gezsin", 13f, color = 0xFF9AA0B0.toInt()).apply {
            setPadding(0, dp(2), 0, dp(12))
        })

        // sahne
        stage = StageView(this).apply { outfit = this@MainActivity.outfit }
        col.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, dp(230)))

        // düğmeler
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        actions.addView(button("🎲 Rastgele", primary = true) { randomOutfit() }, weight())
        actions.addView(space(8))
        actions.addView(button("💾 Kaydet", primary = false) { saveOutfit() }, weight())
        actions.addView(space(8))
        actions.addView(button("🧺 Çıkar", primary = false) { apply(Outfit()) }, weight())
        col.addView(actions)

        // hazır kombinler
        col.addView(sectionTitle("Hazır kombinler"))
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (p in Catalog.presets) presets.addView(outfitChip("${p.emoji} ${p.name}", p.outfit, null))
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(presets) })

        // kayıtlı kombinler
        savedTitle = sectionTitle("Kayıtlı kombinlerim")
        col.addView(savedTitle)
        savedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(savedRow) })

        // kategoriler
        col.addView(sectionTitle("Parçalar"))
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) })
        grid = GridLayout(this).apply { columnCount = 4; setPadding(0, dp(8), 0, 0) }
        col.addView(grid)

        // ipuçları
        col.addView(sectionTitle("Ekranda neler yapabilir?"))
        col.addView(text(
            "• Dokun: kalple zıplar\n" +
                "• Basılı tut: iş menüsü — 🎨 resim, 🍳 yemek, 🧹 temizlik, 💃 dans, 😴 uyku, 🌪 kaos\n" +
                "• Sürükle ve üstteki ✕'e bırak: kapanır\n" +
                "• Uygulamayı tekrar açınca yine gelir ve el sallar",
            14f, color = 0xFFC9CDD6.toInt(),
        ))

        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun refresh() {
        nameView.text = Store.displayName(this)
        stage.outfit = outfit
        // sekmeler
        tabs.removeAllViews()
        for (c in Category.values()) {
            val on = c == category
            tabs.addView(pill("${c.emoji} ${c.title}", on) { category = c; refresh() })
        }
        // parça ızgarası
        grid.removeAllViews()
        val cell = (resources.displayMetrics.widthPixels - dp(32)) / 4
        grid.addView(itemCell(null, "Yok", outfit.get(category) == null, cell) { apply(outfit.with(category, null)) })
        for (it in Catalog.of(category)) {
            grid.addView(itemCell(it, it.name, outfit.get(category) == it.id, cell) { apply(outfit.with(category, it.id)) })
        }
        // kayıtlılar
        val saved = Store.saved(this)
        savedRow.removeAllViews()
        savedTitle.visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
        for ((n, o) in saved) savedRow.addView(outfitChip("⭐ $n", o, n))
    }

    private fun apply(o: Outfit) {
        outfit = o
        Store.setOutfit(this, o)   // ekrandaki karakter anında giyinir
        refresh()
    }

    private fun randomOutfit() {
        val r = Random(System.nanoTime())
        var o = Outfit()
        for (c in Category.values()) {
            // her parça %75 ihtimalle giyilir, ceket biraz daha seyrek
            val chance = if (c == Category.JACKET || c == Category.GLASSES) 0.55f else 0.8f
            if (r.nextFloat() < chance) o = o.with(c, Catalog.of(c).random(r).id)
        }
        apply(o)
    }

    private fun saveOutfit() {
        val input = EditText(this).apply {
            hint = "Kombin adı"
            setText("Kombin ${Store.saved(this@MainActivity).size + 1}")
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(this)
            .setTitle("Kombini kaydet")
            .setView(FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) })
            .setPositiveButton("Kaydet") { _, _ ->
                val n = input.text.toString().trim().ifEmpty { "Kombin" }.take(18)
                Store.save(this, n, outfit)
                refresh()
                Toast.makeText(this, "Kaydedildi ⭐", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun askName(first: Boolean) {
        val input = EditText(this).apply {
            setText(Store.name(this@MainActivity) ?: NameGen.next())
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle(if (first) "Merhaba! Bana bir isim ver 🙂" else "Adım ne olsun?")
            .setMessage("İstersen kendin yaz, istersen 🎲 ile ben uydurayım.")
            .setView(FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) })
            .setPositiveButton("Tamam") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) Store.setName(this, n)
                refresh()
            }
            .setNeutralButton("🎲 Uydur", null)
            .setCancelable(!first)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                input.setText(NameGen.next())
                input.selectAll()
            }
        }
        dlg.show()
    }

    // ------------------------------------------------------------ küçük parçalar

    private fun itemCell(item: Item?, label: String, selected: Boolean, size: Int, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            background = rounded(if (selected) 0xFF3A2A24.toInt() else 0xFF1C1F28.toInt(), if (selected) 0xFFD97757.toInt() else 0)
            setOnClickListener { onClick() }
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (item != null) {
                setImageBitmap(sprites.bitmap("stand", Outfit().with(item.category, item.id), 4))
            } else {
                setImageBitmap(sprites.bitmap("stand", Outfit(), 4))
                alpha = 0.45f
            }
        }
        box.addView(img, LinearLayout.LayoutParams(MATCH_PARENT, (size * 0.72f).toInt()))
        box.addView(text(label, 11f, color = if (selected) Color.WHITE else 0xFFC9CDD6.toInt()).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        })
        box.layoutParams = GridLayout.LayoutParams().apply {
            width = size - dp(6)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        return box
    }

    private fun outfitChip(label: String, o: Outfit, savedName: String?): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(if (o == outfit) 0xFF3A2A24.toInt() else 0xFF1C1F28.toInt(), if (o == outfit) 0xFFD97757.toInt() else 0)
            setOnClickListener { apply(o) }
            if (savedName != null) setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("\"$savedName\" silinsin mi?")
                    .setPositiveButton("Sil") { _, _ -> Store.delete(this@MainActivity, savedName); refresh() }
                    .setNegativeButton("Vazgeç", null)
                    .show()
                true
            }
        }
        box.addView(ImageView(this).apply { setImageBitmap(sprites.bitmap("happy", o, 3)) }, LinearLayout.LayoutParams(dp(66), dp(66)))
        box.addView(text(label, 12f, color = Color.WHITE))
        box.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }
        return box
    }

    private fun pill(label: String, on: Boolean, onClick: () -> Unit) = text(label, 14f, bold = on, color = Color.WHITE).apply {
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = rounded(if (on) 0xFFD97757.toInt() else 0xFF262A35.toInt(), 0, radius = 20f)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(8) }
    }

    private fun sectionTitle(s: String) = text(s, 16f, bold = true).apply { setPadding(0, dp(20), 0, dp(8)) }

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit) = text(label, 15f, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(if (primary) 0xFFD97757.toInt() else 0xFF262A35.toInt(), 0, radius = 24f)
        setOnClickListener { onClick() }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float = 14f) = GradientDrawable().apply {
        cornerRadius = radius * d
        setColor(fill)
        if (stroke != 0) setStroke(dp(2), stroke)
    }

    private fun weight() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
    private fun space(w: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(w), 1) }
}
