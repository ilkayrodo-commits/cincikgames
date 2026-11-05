package com.cincikgames.duvar

import android.content.Context
import android.content.SharedPreferences
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowManager

/** Canlı duvar kağıdı: seçili sahneyi ~30 fps'te çizer, görünmezken durur. */
class LiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = GLEngine()

    inner class GLEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {

        /** GLSurfaceView'ı pencere yerine duvar kağıdı yüzeyine bağlayan küçük hile. */
        inner class WallpaperGLView(context: Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder = surfaceHolder
            fun release() = super.onDetachedFromWindow()
        }

        private var glView: WallpaperGLView? = null
        private lateinit var renderer: ShaderRenderer
        private var def: WallpaperDef = Wallpapers.all.first()
        private var visible = false
        private var lastFrameNs = 0L
        private var screenW = 1
        private var screenH = 1
        private lateinit var tilt: TiltTracker

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val ctx = this@LiveWallpaperService
            def = Wallpapers.selected(ctx)
            readScreenSize()
            applySurfaceScale()
            renderer = ShaderRenderer(ctx, def)
            glView = WallpaperGLView(ctx).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            }
            tilt = TiltTracker(ctx) { x, y ->
                renderer.tiltX = x
                renderer.tiltY = y
            }
            setTouchEventsEnabled(true)
            Wallpapers.prefs(ctx).registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            Choreographer.getInstance().removeFrameCallback(this)
            tilt.stop()
            Wallpapers.prefs(this@LiveWallpaperService).unregisterOnSharedPreferenceChangeListener(this)
            glView?.release()
            glView = null
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            val v = glView ?: return
            if (visible) {
                if (def.usesTilt) tilt.start()
                v.onResume()
                Choreographer.getInstance().removeFrameCallback(this)
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                tilt.stop()
                Choreographer.getInstance().removeFrameCallback(this)
                v.onPause()
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            // ~30 fps: pil için her ekran yenilemesinde değil, 33 ms'de bir çiz.
            if (frameTimeNanos - lastFrameNs >= 30_000_000L) {
                lastFrameNs = frameTimeNanos
                glView?.requestRender()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                renderer.touch(event.x / screenW, event.y / screenH)
            }
            super.onTouchEvent(event)
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            renderer.offset = if (xStep > 0f) xOffset else 0.5f
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            if (key != Wallpapers.KEY_SELECTED) return
            val newDef = Wallpapers.selected(this@LiveWallpaperService)
            if (newDef.id == def.id) return
            def = newDef
            applySurfaceScale()
            if (newDef.usesTilt && visible) tilt.start() else tilt.stop()
            renderer.tiltX = 0f
            renderer.tiltY = 0f
            glView?.queueEvent { renderer.setWallpaper(newDef) }
        }

        private fun applySurfaceScale() {
            if (def.renderScale >= 0.999f) {
                surfaceHolder.setSizeFromLayout()
            } else {
                surfaceHolder.setFixedSize(
                    (screenW * def.renderScale).toInt().coerceAtLeast(1),
                    (screenH * def.renderScale).toInt().coerceAtLeast(1),
                )
            }
        }

        @Suppress("DEPRECATION")
        private fun readScreenSize() {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            screenW = m.widthPixels.coerceAtLeast(1)
            screenH = m.heightPixels.coerceAtLeast(1)
        }
    }
}
