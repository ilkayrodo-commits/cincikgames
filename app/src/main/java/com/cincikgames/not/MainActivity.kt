package com.cincikgames.not

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea

class MainActivity : AppCompatActivity() {

    private lateinit var note: EditText
    private lateinit var status: TextView
    private lateinit var pad: InkView

    private var recognizer: DigitalInkRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizeLater = Runnable { recognizeInk() }

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                insertText(text)
                status.text = getString(R.string.status_speech_done)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        note = findViewById(R.id.note)
        status = findViewById(R.id.status)
        pad = findViewById(R.id.pad)

        note.setText(NoteStore.load(this))
        note.setSelection(note.text.length)
        note.showSoftInputOnFocus = false // Varsayılan: el yazısı. Klavye için ⌨ düğmesi.
        note.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = NoteStore.save(this@MainActivity, s.toString())
        })

        pad.onStrokeStarted = {
            handler.removeCallbacks(recognizeLater)
            hideKeyboard()
        }
        pad.onStrokeFinished = {
            handler.removeCallbacks(recognizeLater)
            handler.postDelayed(recognizeLater, 900)
        }

        findViewById<Button>(R.id.btn_mic).setOnClickListener { startSpeech() }
        findViewById<Button>(R.id.btn_calc).setOnClickListener { calculateCurrentLine(showErrors = true) }
        findViewById<Button>(R.id.btn_copy).setOnClickListener { copyAll() }
        findViewById<Button>(R.id.btn_space).setOnClickListener { insertRaw(" ") }
        findViewById<Button>(R.id.btn_enter).setOnClickListener { insertRaw("\n") }
        findViewById<Button>(R.id.btn_back).apply {
            setOnClickListener { backspace() }
            setOnLongClickListener { confirmClearNote(); true }
        }
        findViewById<Button>(R.id.btn_clear_pad).setOnClickListener {
            handler.removeCallbacks(recognizeLater)
            pad.clear()
        }
        findViewById<Button>(R.id.btn_keyboard).setOnClickListener { showKeyboard() }

        setupRecognizer()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        NoteStore.save(this, note.text.toString())
        NoteWidget.updateAll(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.close()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == NoteWidget.ACTION_SPEAK) {
            intent.action = null
            note.post { startSpeech() }
        }
    }

    // ---------- El yazısı tanıma ----------

    private fun setupRecognizer() {
        val id = try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("tr")
        } catch (e: Exception) {
            null
        }
        if (id == null) {
            status.text = getString(R.string.status_no_model)
            return
        }
        val model = DigitalInkRecognitionModel.builder(id).build()
        val manager = RemoteModelManager.getInstance()
        val create = {
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            status.text = getString(R.string.status_ready)
            if (!pad.isEmpty) recognizeInk()
        }
        manager.isModelDownloaded(model).addOnSuccessListener { downloaded ->
            if (downloaded) {
                create()
            } else {
                status.text = getString(R.string.status_downloading)
                manager.download(model, DownloadConditions.Builder().build())
                    .addOnSuccessListener { create() }
                    .addOnFailureListener { status.text = getString(R.string.status_download_failed, it.message) }
            }
        }.addOnFailureListener { status.text = getString(R.string.status_download_failed, it.message) }
    }

    private fun recognizeInk() {
        if (pad.isEmpty) return
        val r = recognizer
        if (r == null) {
            status.text = getString(R.string.status_wait_model)
            return
        }
        val ink = pad.buildInk()
        val before = note.text.substring(0, note.selectionStart.coerceAtLeast(0))
        val ctx = RecognitionContext.builder()
            .setPreContext(before.takeLast(20))
            .setWritingArea(WritingArea(pad.width.toFloat(), pad.height.toFloat()))
            .build()
        r.recognize(ink, ctx)
            .addOnSuccessListener { result ->
                val text = result.candidates.firstOrNull()?.text
                pad.clear()
                if (text.isNullOrBlank()) {
                    status.text = getString(R.string.status_not_understood)
                    return@addOnSuccessListener
                }
                insertText(text)
                val others = result.candidates.drop(1).take(3).joinToString("  ·  ") { it.text }
                status.text = if (others.isEmpty()) "✓ $text" else "✓ $text   (diğer: $others)"
                if (text.trim().endsWith("=")) calculateCurrentLine(showErrors = false)
            }
            .addOnFailureListener {
                status.text = getString(R.string.status_recognize_failed, it.message)
            }
    }

    // ---------- Ses ----------

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt))
        }
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.speech_missing, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Metin düzenleme ----------

    /** Kelimeyi imlecin olduğu yere ekler, gerekirse önüne boşluk koyar. */
    private fun insertText(text: String) {
        val start = note.selectionStart.coerceAtLeast(0)
        val prev = if (start > 0) note.text[start - 1] else null
        val needsSpace = prev != null && !prev.isWhitespace()
        insertRaw(if (needsSpace) " $text" else text)
    }

    private fun insertRaw(text: String) {
        val start = note.selectionStart.coerceAtLeast(0)
        val end = note.selectionEnd.coerceAtLeast(0)
        note.text.replace(minOf(start, end), maxOf(start, end), text)
    }

    private fun backspace() {
        val start = note.selectionStart
        val end = note.selectionEnd
        if (start != end) {
            note.text.delete(minOf(start, end), maxOf(start, end))
        } else if (start > 0) {
            note.text.delete(start - 1, start)
        }
    }

    private fun confirmClearNote() {
        AlertDialog.Builder(this)
            .setMessage(R.string.clear_note_question)
            .setPositiveButton(R.string.clear_note_yes) { _, _ -> note.setText("") }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun copyAll() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Not", note.text.toString()))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    /** İmlecin bulunduğu satırdaki işlemi hesaplayıp "= sonuç" yazar. */
    private fun calculateCurrentLine(showErrors: Boolean) {
        val text = note.text.toString()
        fun bounds(c: Int): Pair<Int, Int> {
            val start = text.lastIndexOf('\n', c - 1) + 1
            val end = text.indexOf('\n', c).let { if (it < 0) text.length else it }
            return start to end
        }
        var (lineStart, lineEnd) = bounds(note.selectionStart.coerceIn(0, text.length))
        // İmleç boş satırdaysa en son dolu satırı kullan.
        if (text.substring(lineStart, lineEnd).isBlank() && text.isNotBlank()) {
            val b = bounds(text.trimEnd().length)
            lineStart = b.first
            lineEnd = b.second
        }
        val line = text.substring(lineStart, lineEnd)
        try {
            val solved = MathEval.solveLine(line)
            note.text.replace(lineStart, lineEnd, solved)
            note.setSelection(lineStart + solved.length)
            status.text = getString(R.string.status_calc_done, solved.substringAfterLast("= "))
        } catch (e: MathEval.MathError) {
            if (showErrors) Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            status.text = "⚠ ${e.message}"
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(note.windowToken, 0)
        note.showSoftInputOnFocus = false
    }

    private fun showKeyboard() {
        note.showSoftInputOnFocus = true
        note.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(note, InputMethodManager.SHOW_IMPLICIT)
    }
}
