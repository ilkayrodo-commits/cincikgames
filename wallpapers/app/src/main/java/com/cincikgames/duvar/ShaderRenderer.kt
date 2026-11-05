package com.cincikgames.duvar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Tam ekran bir dörtgen üzerine seçili duvar kağıdının shader'ını çizer. */
class ShaderRenderer(private val context: Context, initial: WallpaperDef) : GLSurfaceView.Renderer {

    @Volatile private var pending: WallpaperDef? = initial
    private var current: WallpaperDef = initial

    private var program = 0
    private var textures = IntArray(0)
    private var tex0W = 1f
    private var tex0H = 1f
    private var width = 1
    private var height = 1
    private val startNs = System.nanoTime()

    /** Ana ekran sayfa kaydırma (0..1). */
    @Volatile var offset = 0.5f

    @Volatile private var touchNX = -10f
    @Volatile private var touchNY = -10f
    @Volatile private var touchAtNs = 0L

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun setWallpaper(def: WallpaperDef) {
        pending = def
    }

    /** nx, ny: ekran üzerinde 0..1 (y aşağı doğru). */
    fun touch(nx: Float, ny: Float) {
        touchNX = nx
        touchNY = ny
        touchAtNs = System.nanoTime()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Yeni bağlam: eski program/dokular geçersiz, yeniden yükle.
        program = 0
        textures = IntArray(0)
        if (pending == null) pending = current
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        pending?.let {
            pending = null
            load(it)
            current = it
        }
        if (program == 0) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }
        GLES20.glUseProgram(program)
        val now = System.nanoTime()
        val t = (((now - startNs) / 1e9) % 3600.0).toFloat()
        val touchAge = if (touchAtNs == 0L) 999f else ((now - touchAtNs) / 1e9f)

        GLES20.glUniform2f(loc("u_res"), width.toFloat(), height.toFloat())
        GLES20.glUniform1f(loc("u_time"), t)
        GLES20.glUniform3f(loc("u_touch"), touchNX * width, (1f - touchNY) * height, touchAge)
        GLES20.glUniform1f(loc("u_offset"), offset)
        GLES20.glUniform2f(loc("u_tex0Size"), tex0W, tex0H)
        for (i in textures.indices) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glUniform1i(loc("u_tex$i"), i)
        }
        val pos = GLES20.glGetAttribLocation(program, "a_pos")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun loc(name: String) = GLES20.glGetUniformLocation(program, name)

    private fun load(def: WallpaperDef) {
        if (program != 0) GLES20.glDeleteProgram(program)
        if (textures.isNotEmpty()) GLES20.glDeleteTextures(textures.size, textures, 0)
        program = 0
        textures = IntArray(0)

        val common = readAsset("shaders/common.glsl")
        val frag = common + "\n" + readAsset("shaders/${def.shader}.frag")
        program = buildProgram(VERTEX, frag)

        textures = IntArray(def.textures.size)
        if (textures.isNotEmpty()) GLES20.glGenTextures(textures.size, textures, 0)
        def.textures.forEachIndexed { i, path ->
            val bmp = loadBitmap(path)
            if (i == 0) {
                tex0W = bmp.width.toFloat()
                tex0H = bmp.height.toFloat()
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
        }
    }

    private fun readAsset(path: String) =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun loadBitmap(path: String): Bitmap {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts)!! }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        if (v == 0 || f == 0) return fallbackProgram()
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        if (ok[0] == 0) {
            Log.e(TAG, "Bağlama hatası: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteProgram(p)
            return fallbackProgram()
        }
        return p
    }

    private fun fallbackProgram(): Int {
        // Shader bu GPU'da derlenemezse düz renkli bir gradyan göster (çökme yerine).
        val fs = "precision mediump float; varying vec2 v_uv; uniform float u_time;" +
            "void main(){ gl_FragColor = vec4(mix(vec3(0.1,0.05,0.2), vec3(0.9,0.4,0.3), v_uv.y)*(0.9+0.1*sin(u_time)), 1.0); }"
        val v = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e(TAG, "Derleme hatası: " + GLES20.glGetShaderInfoLog(s))
            GLES20.glDeleteShader(s)
            return 0
        }
        return s
    }

    companion object {
        private const val TAG = "CincikDuvar"
        private const val VERTEX =
            "attribute vec2 a_pos; varying vec2 v_uv;" +
                "void main(){ v_uv = a_pos * 0.5 + 0.5; gl_Position = vec4(a_pos, 0.0, 1.0); }"
    }
}
