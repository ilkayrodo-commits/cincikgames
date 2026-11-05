package com.cincikgames.dost

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ekranda gezen karakter. Diğer uygulamaların üzerinde küçük bir pencere olarak
 * durur; dokunmalar yalnızca karakterin üzerindeyse yakalanır, geri kalanı alttaki
 * ekrana geçer.
 */
class BuddyService : Service(), Choreographer.FrameCallback {

    private enum class State { WALK, IDLE, SIT, SLEEP, HOP, DRAG, FALL, CLOSING }

    private lateinit var wm: WindowManager
    private lateinit var buddy: BuddyView
    private lateinit var target: CloseTargetView
    private lateinit var buddyLp: WindowManager.LayoutParams
    private lateinit var targetLp: WindowManager.LayoutParams
    private var attached = false
    private var targetShown = false
    private var running = false

    private val rnd = Random(System.nanoTime())
    private var d = 1f
    private var screenW = 1
    private var screenH = 1

    // karakterin "zemin" konumu: pencerenin sol üst köşesi
    private var x = 0f
    private var y = 0f
    private var goalX = 0f
    private var goalY = 0f
    private var speed = 0f
    private var state = State.IDLE
    private var stateTime = 0f
    private var stateDur = 1f
    private var walkClock = 0f
    private var blinkIn = 2f
    private var blinkLeft = 0f
    private var lookFlipAt = -1f
    private var heartLeft = 0f
    private var fallVy = 0f
    private var lastNs = 0L
    private var t = 0f
    private var frameDt = 0.016f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        d = resources.displayMetrics.density
        readScreen()
        startInForeground()

        buddy = BuddyView(this, Sprites(this), pixel = 4.2f * d)
        target = CloseTargetView(this)
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        buddyLp = WindowManager.LayoutParams(
            buddy.viewW, buddy.viewH, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        targetLp = WindowManager.LayoutParams(
            target.sizePx, target.sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        // Ekranın alt kısmından, kenardan yürüyerek gelsin.
        x = if (rnd.nextBoolean()) -buddy.viewW.toFloat() else screenW.toFloat()
        y = screenH * (0.55f + rnd.nextFloat() * 0.25f)
        pickGoal()
        state = State.WALK

        setupTouch()
        try {
            wm.addView(buddy, buddyLp)
            attached = true
        } catch (e: Exception) {
            // İzin yoksa (kullanıcı kaldırmış olabilir) sessizce kapan.
            stopSelf()
            return
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            beginClose()
        } else if (attached && state != State.CLOSING) {
            // Simgeye tekrar dokunuldu: selam ver.
            startHop(happy = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        if (targetShown) try { wm.removeView(target) } catch (_: Exception) {}
        if (attached) try { wm.removeView(buddy) } catch (_: Exception) {}
        attached = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readScreen()
        clampToScreen()
        pickGoal()
    }

    // ------------------------------------------------------------ döngü

    private fun startLoop() {
        if (running) return
        running = true
        lastNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_SCREEN_OFF) stopLoop() else startLoop()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastNs == 0L) 0.016f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = frameTimeNanos
        t += dt
        frameDt = dt
        update(dt)
        if (running) Choreographer.getInstance().postFrameCallback(this)
    }

    private fun update(dt: Float) {
        stateTime += dt
        buddy.time = t
        heartLeft = (heartLeft - dt).coerceAtLeast(0f)
        buddy.heart = if (heartLeft > 0f) 1f - heartLeft / HEART_TIME else 0f
        buddy.bob = 0f
        buddy.lift = 0f

        when (state) {
            State.WALK -> {
                val dx = goalX - x
                val dy = goalY - y
                val dist = hypot(dx, dy)
                val step = speed * dt
                if (dist <= step) {
                    x = goalX
                    y = goalY
                    afterArrive()
                } else {
                    x += dx / dist * step
                    y += dy / dist * step
                    if (abs(dx) > 2f) buddy.facingLeft = dx < 0f
                }
                walkClock += dt * (speed / (70f * d))
                val phase = (walkClock * 5.5f).toInt() % 2
                buddy.frame = if (phase == 0) "walk1" else "walk2"
                buddy.bob = if (phase == 0) 0f else -buddy.pixel * 0.6f
            }
            State.IDLE -> {
                buddy.frame = blinkFrame("stand")
                if (lookFlipAt > 0f && stateTime >= lookFlipAt) {
                    buddy.facingLeft = !buddy.facingLeft
                    lookFlipAt = -1f
                }
                if (stateTime >= stateDur) chooseNext()
            }
            State.SIT -> {
                buddy.frame = if (stateTime < 0.25f) "stand" else blinkFrame("sit")
                if (stateTime >= stateDur) {
                    if (rnd.nextFloat() < 0.4f) enter(State.SLEEP, 5f + rnd.nextFloat() * 6f) else chooseNext()
                }
            }
            State.SLEEP -> {
                buddy.frame = "sit"
                buddy.sleepy = min(1f, stateTime / 0.8f)
                // nefes alırken hafifçe inip kalkar
                buddy.bob = sin(t * 2.2f) * buddy.pixel * 0.35f
                if (stateTime >= stateDur) {
                    buddy.sleepy = 0f
                    startHop(happy = false)
                }
            }
            State.HOP -> {
                val p = (stateTime / stateDur).coerceIn(0f, 1f)
                buddy.lift = sin(p * Math.PI.toFloat()) * buddy.pixel * 7f
                buddy.frame = if (p < 0.1f || p > 0.9f) "stand" else if (heartLeft > 0f) "happy" else "hop"
                if (p >= 1f) chooseNext()
            }
            State.DRAG -> {
                buddy.frame = "dangle"
                buddy.bob = sin(t * 9f) * buddy.pixel * 0.4f
                updateTarget()
            }
            State.FALL -> {
                // bırakılınca yere "düşer" (kısa bir yerçekimi)
                fallVy += 2600f * d * dt
                y += fallVy * dt
                buddy.frame = "dangle"
                if (y >= goalY) {
                    y = goalY
                    startHop(happy = false)
                }
            }
            State.CLOSING -> {
                val p = (stateTime / 0.35f).coerceIn(0f, 1f)
                buddy.scale = 1f - p
                buddy.frame = "happy"
                if (p >= 1f) {
                    stopLoop()
                    stopSelf()
                    return
                }
            }
        }
        if (state != State.SLEEP) buddy.sleepy = (buddy.sleepy - dt * 2f).coerceAtLeast(0f)
        applyPosition()
        buddy.invalidate()
    }

    private fun blinkFrame(base: String): String {
        blinkIn -= frameDt
        if (blinkLeft > 0f) {
            blinkLeft -= frameDt
            return "blink"
        }
        if (blinkIn <= 0f) {
            blinkLeft = 0.14f
            blinkIn = 1.8f + rnd.nextFloat() * 3f
        }
        return base
    }

    private fun enter(s: State, dur: Float) {
        state = s
        stateTime = 0f
        stateDur = dur
    }

    private fun afterArrive() {
        val r = rnd.nextFloat()
        when {
            r < 0.45f -> {
                enter(State.IDLE, 1.2f + rnd.nextFloat() * 2.8f)
                lookFlipAt = if (rnd.nextFloat() < 0.6f) stateDur * (0.3f + rnd.nextFloat() * 0.4f) else -1f
            }
            r < 0.62f -> startHop(happy = rnd.nextFloat() < 0.3f)
            r < 0.80f -> enter(State.SIT, 2.5f + rnd.nextFloat() * 3f)
            else -> chooseNext()
        }
    }

    private fun chooseNext() {
        pickGoal()
        // ara sıra koşar
        speed = (if (rnd.nextFloat() < 0.15f) 150f else 55f + rnd.nextFloat() * 35f) * d
        enter(State.WALK, 0f)
    }

    private fun startHop(happy: Boolean) {
        if (happy) heartLeft = HEART_TIME
        enter(State.HOP, 0.55f)
    }

    private fun pickGoal() {
        val minX = 0f
        val maxX = (screenW - buddy.viewW).toFloat()
        val minY = 70f * d
        val maxY = (screenH - buddy.viewH - 70f * d)
        // tercihen uzak bir hedef: ekranı tur atsın
        var gx: Float
        var gy: Float
        var tries = 0
        do {
            gx = minX + rnd.nextFloat() * (maxX - minX)
            gy = minY + rnd.nextFloat() * (maxY - minY)
            tries++
        } while (hypot(gx - x, gy - y) < 160f * d && tries < 8)
        goalX = gx
        goalY = gy
        if (speed == 0f) speed = 70f * d
    }

    private fun clampToScreen() {
        x = x.coerceIn(0f, (screenW - buddy.viewW).toFloat().coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenH - buddy.viewH).toFloat().coerceAtLeast(0f))
    }

    private fun applyPosition() {
        if (!attached) return
        val nx = x.toInt()
        val ny = y.toInt()
        if (nx != buddyLp.x || ny != buddyLp.y) {
            buddyLp.x = nx
            buddyLp.y = ny
            try { wm.updateViewLayout(buddy, buddyLp) } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------ dokunma

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouch() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        buddy.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = x
                    startY = y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - downX
                    val my = e.rawY - downY
                    if (!dragging && hypot(mx, my) > slop) {
                        dragging = true
                        enter(State.DRAG, 0f)
                        buddy.sleepy = 0f
                        showTarget()
                    }
                    if (dragging) {
                        x = startX + mx
                        y = startY + my
                        clampToScreen()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val close = target.active
                        hideTarget()
                        if (close) {
                            beginClose()
                        } else {
                            // bırakıldığı yerin biraz altına düşsün
                            goalY = (y + 24f * d).coerceAtMost((screenH - buddy.viewH - 40f * d))
                            fallVy = 0f
                            enter(State.FALL, 0f)
                        }
                    } else {
                        // dokunuldu: sevinçle zıpla
                        startHop(happy = true)
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun targetCenter(): Pair<Float, Float> =
        Pair(screenW / 2f, 110f * d)

    private fun showTarget() {
        if (targetShown) return
        val (cx, cy) = targetCenter()
        targetLp.x = (cx - target.sizePx / 2f).toInt()
        targetLp.y = (cy - target.sizePx / 2f).toInt()
        target.appear = 0f
        target.active = false
        try {
            wm.addView(target, targetLp)
            targetShown = true
        } catch (_: Exception) {}
    }

    private fun hideTarget() {
        if (!targetShown) return
        try { wm.removeView(target) } catch (_: Exception) {}
        targetShown = false
    }

    private fun updateTarget() {
        if (!targetShown) return
        target.appear = min(1f, target.appear + 0.12f)
        val (cx, cy) = targetCenter()
        val bx = x + buddy.viewW / 2f
        val by = y + buddy.viewH * 0.6f
        target.active = hypot(bx - cx, by - cy) < 85f * d
    }

    private fun beginClose() {
        if (state == State.CLOSING) return
        hideTarget()
        enter(State.CLOSING, 0.35f)
        if (!running) {
            stopSelf()
        }
    }

    // ------------------------------------------------------------ yardımcılar

    @Suppress("DEPRECATION")
    private fun readScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            screenW = b.width()
            screenH = b.height()
        } else {
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            screenW = m.widthPixels
            screenH = m.heightPixels
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BuddyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_close), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val ACTION_STOP = "com.cincikgames.dost.STOP"
        private const val CHANNEL = "gezgin"
        private const val NOTIF_ID = 7
        private const val HEART_TIME = 1.3f
    }
}
