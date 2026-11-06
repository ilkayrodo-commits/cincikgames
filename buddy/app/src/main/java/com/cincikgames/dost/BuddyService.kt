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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ekranda gezen karakter. Diğer uygulamaların üzerinde küçük bir pencere olarak
 * durur; dokunmalar yalnızca karakterin üzerindeyse yakalanır.
 *
 * Basılı tutunca bir iş menüsü açılır (resim, yemek, temizlik, dans, uyku).
 * Sürükleyince ekranın üstünde ✕ belirir; oraya bırakılınca kapanır.
 */
class BuddyService : Service(), Choreographer.FrameCallback {

    private enum class State {
        WALK, IDLE, SIT, SLEEP, STRETCH, HOP, WAVE, DANCE, CLIMB, HANG,
        DRAG, FALL, MENU, PAINT, COOK, CLEAN, SHOWOFF, CLOSING,
        CHAOS_MOVE, CHAOS_ACT, CHAOS_REST, CHAOS_CLEAN,
    }

    private enum class Walk { NORMAL, TO_WALL }

    private lateinit var wm: WindowManager
    private lateinit var buddy: BuddyView
    private lateinit var target: CloseTargetView
    private lateinit var buddyLp: WindowManager.LayoutParams
    private lateinit var targetLp: WindowManager.LayoutParams
    private var menu: View? = null
    private var attached = false
    private var targetShown = false
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    private val rnd = Random(System.nanoTime())
    private var d = 1f
    private var screenW = 1
    private var screenH = 1

    // (x, y): taban bölgesinin (dolgular hariç) sol üst köşesi, ekran pikseli
    private var x = 0f
    private var y = 0f
    private var goalX = 0f
    private var goalY = 0f
    private var speed = 0f
    private var walkPurpose = Walk.NORMAL
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
    private var greetPending = true

    // tırmanma
    private var wallLeft = true
    private var climbStartY = 0f
    private var climbTopY = 0f

    // kaos
    private var chaos: ChaosView? = null
    private val chaosActs = ArrayList<String>()
    private var chaosAct = ""
    private var chaosDone = false

    // ayarlar değişince (gardırop, isim) anında güncelle
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Store.KEY_OUTFIT -> { buddy.outfit = Store.outfit(this); buddy.invalidate() }
            Store.KEY_NAME -> startInForeground()
        }
    }

    // işler
    private var cleanStops = 0
    private var puffTimer = 0f
    private var afterShowoff: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        d = resources.displayMetrics.density
        readScreen()
        startInForeground()

        buddy = BuddyView(this, Sprites.get(this), pixel = 4.2f * d)
        buddy.outfit = Store.outfit(this)
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

        // Ekranın kenarından yürüyerek gelsin.
        x = if (rnd.nextBoolean()) -buddy.viewW.toFloat() else screenW.toFloat()
        y = screenH * (0.55f + rnd.nextFloat() * 0.25f)
        pickGoal()
        speed = 70f * d
        enter(State.WALK, 0f)

        setupTouch()
        try {
            wm.addView(buddy, buddyLp)
            attached = true
        } catch (e: Exception) {
            stopSelf()
            return
        }
        Store.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            beginClose()
        } else if (attached && state != State.CLOSING && !inJob()) {
            // Simgeye tekrar dokunuldu: el salla.
            closeMenu()
            buddy.rotationDeg = 0f
            clampToScreen()
            enter(State.WAVE, 1.8f)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        removeChaos()
        closeMenu()
        if (targetShown) try { wm.removeView(target) } catch (_: Exception) {}
        if (attached) try { wm.removeView(buddy) } catch (_: Exception) {}
        attached = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readScreen()
        if (state == State.CLIMB || state == State.HANG) jumpOffWall()
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
        if (state != State.DANCE) buddy.notes = (buddy.notes - dt * 2f).coerceAtLeast(0f)
        if (state != State.SLEEP) buddy.sleepy = (buddy.sleepy - dt * 2f).coerceAtLeast(0f)
        if (state != State.SHOWOFF) buddy.sparkle = (buddy.sparkle - dt * 2f).coerceAtLeast(0f)
        updatePuffs(dt)
        chaos?.update(dt)

        when (state) {
            State.WALK -> {
                if (moveToGoal(dt)) {
                    if (walkPurpose == Walk.TO_WALL) startClimb() else afterArrive()
                }
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
                    val sleepChance = if (isNight()) 0.85f else 0.4f
                    if (rnd.nextFloat() < sleepChance) enter(State.SLEEP, sleepDuration()) else chooseNext()
                }
            }
            State.SLEEP -> {
                buddy.frame = "sit"
                buddy.sleepy = min(1f, stateTime / 0.8f)
                buddy.bob = sin(t * 2.2f) * buddy.pixel * 0.35f
                if (stateTime >= stateDur) enter(State.STRETCH, 1.5f)
            }
            State.STRETCH -> {
                // uyanınca gerinir
                buddy.frame = if (stateTime < 0.2f || stateTime > 1.3f) "stand" else "stretch"
                buddy.lift = if (buddy.frame == "stretch") buddy.pixel * 0.8f else 0f
                if (stateTime >= stateDur) chooseNext()
            }
            State.HOP -> {
                val p = (stateTime / stateDur).coerceIn(0f, 1f)
                buddy.lift = sin(p * Math.PI.toFloat()) * buddy.pixel * 7f
                buddy.frame = if (p < 0.1f || p > 0.9f) "stand" else if (heartLeft > 0f) "happy" else "hop"
                if (p >= 1f) chooseNext()
            }
            State.WAVE -> {
                buddy.frame = if (((stateTime / 0.22f).toInt() % 2) == 0) "wave1" else "wave2"
                if (stateTime >= stateDur) {
                    enter(State.IDLE, 0.8f)
                    lookFlipAt = -1f
                }
            }
            State.DANCE -> {
                val beat = (stateTime / 0.3f).toInt()
                buddy.frame = if (beat % 2 == 0) "dance1" else "dance2"
                buddy.bob = -abs(sin(stateTime * Math.PI.toFloat() / 0.3f)) * buddy.pixel * 1.2f
                buddy.facingLeft = ((stateTime / 1.2f).toInt() % 2) == 1
                buddy.notes = min(1f, stateTime * 2f)
                if (stateTime >= stateDur) startHop(happy = true)
            }
            State.CLIMB -> {
                y -= speed * dt
                walkClock += dt * 1.6f
                val phase = (walkClock * 5.5f).toInt() % 2
                buddy.frame = if (phase == 0) "walk1" else "walk2"
                if (y <= climbTopY) {
                    y = climbTopY
                    enter(State.HANG, 1.5f + rnd.nextFloat() * 1.5f)
                }
            }
            State.HANG -> {
                buddy.frame = blinkFrame("stand")
                if (stateTime >= stateDur) jumpOffWall()
            }
            State.DRAG -> {
                buddy.frame = "dangle"
                buddy.bob = sin(t * 9f) * buddy.pixel * 0.4f
                updateTarget()
            }
            State.FALL -> {
                fallVy += 2600f * d * dt
                y += fallVy * dt
                buddy.frame = "dangle"
                if (y >= goalY) {
                    y = goalY
                    startHop(happy = false)
                }
            }
            State.MENU -> {
                buddy.frame = blinkFrame("stand")
                if (stateTime > 9f) {
                    closeMenu()
                    chooseNext()
                }
            }
            State.PAINT -> {
                buddy.paintProgress = (stateTime / stateDur).coerceIn(0f, 1f)
                buddy.toolPhase = ((stateTime / 0.22f).toInt() % 2)
                buddy.frame = blinkFrame("stand")
                // ara sıra geri çekilip eserine bakar
                buddy.bob = if (((stateTime / 2.5f).toInt() % 3) == 2) buddy.pixel * 0.4f else 0f
                if (stateTime >= stateDur) {
                    buddy.paintProgress = 1f
                    buddy.tool = BuddyView.Tool.NONE
                    showoff(3.5f) { clearJob() }
                }
            }
            State.COOK -> {
                buddy.toolPhase = ((stateTime / 0.3f).toInt() % 2)
                buddy.frame = blinkFrame("stand")
                if (stateTime >= stateDur) {
                    buddy.prop = BuddyView.Prop.BOWL
                    buddy.tool = BuddyView.Tool.NONE
                    showoff(3.5f) { clearJob() }
                }
            }
            State.CLEAN -> {
                buddy.toolPhase = ((stateTime / 0.18f).toInt() % 2)
                if (moveToGoal(dt)) {
                    cleanStops--
                    if (cleanStops <= 0) {
                        buddy.tool = BuddyView.Tool.NONE
                        showoff(2.2f) { clearJob() }
                        buddy.sparkle = 1f
                    } else {
                        pickGoal(lowerHalf = true, minDist = 110f)
                    }
                } else {
                    puffTimer -= dt
                    if (puffTimer <= 0f) {
                        puffTimer = 0.09f
                        emitPuff()
                    }
                }
            }
            State.SHOWOFF -> {
                // önce sevinçle zıplar, sonra eserini gösterir
                val hopT = 0.55f
                if (stateTime < hopT) {
                    buddy.lift = sin(stateTime / hopT * Math.PI.toFloat()) * buddy.pixel * 6f
                    buddy.frame = "happy"
                } else {
                    buddy.frame = blinkFrame("happy")
                }
                if (buddy.sparkle > 0f) buddy.sparkle = (1f - (stateTime - stateDur + 0.6f).coerceAtLeast(0f) / 0.6f).coerceIn(0f, 1f)
                if (stateTime >= stateDur) {
                    afterShowoff?.invoke()
                    afterShowoff = null
                    chooseNext()
                }
            }
            State.CHAOS_MOVE -> if (moveToGoal(dt)) beginChaosAct()
            State.CHAOS_ACT -> updateChaosAct()
            State.CHAOS_REST -> {
                buddy.frame = blinkFrame("sit")
                if (stateTime >= stateDur) beginChaosClean()
            }
            State.CHAOS_CLEAN -> {
                val p = (stateTime / stateDur).coerceIn(0f, 1f)
                chaos?.wipe = p
                buddy.toolPhase = ((stateTime / 0.15f).toInt() % 2)
                walkClock += dt * 2f
                buddy.frame = if ((walkClock * 5.5f).toInt() % 2 == 0) "walk1" else "walk2"
                x = p * screenW - buddy.spriteCx - buddy.spriteW * 0.1f
                if (p >= 1f) {
                    removeChaos()
                    buddy.tool = BuddyView.Tool.NONE
                    buddy.sparkle = 1f
                    showoff(2.2f) { clearJob() }
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
        applyPosition()
        buddy.invalidate()
    }

    /** Hedefe doğru yürür; vardıysa true. */
    private fun moveToGoal(dt: Float): Boolean {
        val dx = goalX - x
        val dy = goalY - y
        val dist = hypot(dx, dy)
        val step = speed * dt
        walkClock += dt * (speed / (70f * d))
        val phase = (walkClock * 5.5f).toInt() % 2
        buddy.frame = if (phase == 0) "walk1" else "walk2"
        buddy.bob = if (phase == 0) 0f else -buddy.pixel * 0.6f
        if (dist <= step) {
            x = goalX
            y = goalY
            return true
        }
        x += dx / dist * step
        y += dy / dist * step
        if (abs(dx) > 2f) buddy.facingLeft = dx < 0f
        return false
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

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 23 || h < 7
    }

    private fun sleepDuration(): Float =
        if (isNight()) 15f + rnd.nextFloat() * 25f else 5f + rnd.nextFloat() * 6f

    private fun afterArrive() {
        if (greetPending) {
            greetPending = false
            enter(State.WAVE, 1.8f)
            return
        }
        val r = rnd.nextFloat()
        if (isNight()) {
            when {
                r < 0.35f -> enter(State.IDLE, 2f + rnd.nextFloat() * 3f)
                r < 0.80f -> enter(State.SIT, 1.5f + rnd.nextFloat() * 2f)
                else -> chooseNext()
            }
            return
        }
        when {
            r < 0.34f -> {
                enter(State.IDLE, 1.2f + rnd.nextFloat() * 2.8f)
                lookFlipAt = if (rnd.nextFloat() < 0.6f) stateDur * (0.3f + rnd.nextFloat() * 0.4f) else -1f
            }
            r < 0.46f -> startHop(happy = rnd.nextFloat() < 0.3f)
            r < 0.62f -> enter(State.SIT, 2.5f + rnd.nextFloat() * 3f)
            r < 0.70f -> enter(State.DANCE, 4f + rnd.nextFloat() * 3f)
            r < 0.79f -> goToWall()
            else -> chooseNext()
        }
    }

    private fun chooseNext() {
        walkPurpose = Walk.NORMAL
        pickGoal()
        val night = isNight()
        speed = when {
            night -> 40f
            rnd.nextFloat() < 0.15f -> 150f
            else -> 55f + rnd.nextFloat() * 35f
        } * d
        enter(State.WALK, 0f)
    }

    private fun startHop(happy: Boolean) {
        if (happy) heartLeft = HEART_TIME
        enter(State.HOP, 0.55f)
    }

    private fun pickGoal(lowerHalf: Boolean = false, minDist: Float = 160f) {
        val minX = buddy.padL * buddy.pixel
        val maxX = (screenW - buddy.viewW).toFloat() + buddy.padL * buddy.pixel
        val minY = if (lowerHalf) screenH * 0.35f else 70f * d
        val maxY = (screenH - buddy.viewH - 70f * d)
        var gx: Float
        var gy: Float
        var tries = 0
        do {
            gx = minX + rnd.nextFloat() * (maxX - minX).coerceAtLeast(1f)
            gy = minY + rnd.nextFloat() * (maxY - minY).coerceAtLeast(1f)
            tries++
        } while (hypot(gx - x, gy - y) < minDist * d && tries < 8)
        goalX = gx
        goalY = gy
    }

    private fun clampToScreen() {
        val minX = buddy.padL * buddy.pixel
        val maxX = (screenW - buddy.viewW).toFloat() + buddy.padL * buddy.pixel
        x = x.coerceIn(minX, maxX.coerceAtLeast(minX))
        y = y.coerceIn(0f, (screenH - buddy.viewH).toFloat().coerceAtLeast(0f))
    }

    private fun applyPosition() {
        if (!attached) return
        val nx = (x - buddy.padL * buddy.pixel).toInt()
        val ny = y.toInt()
        val nw = buddy.viewW
        val nh = buddy.viewH
        buddy.winX = nx.toFloat()
        buddy.winY = ny.toFloat()
        if (nx != buddyLp.x || ny != buddyLp.y || nw != buddyLp.width || nh != buddyLp.height) {
            val resized = nw != buddyLp.width || nh != buddyLp.height
            buddyLp.x = nx
            buddyLp.y = ny
            buddyLp.width = nw
            buddyLp.height = nh
            if (resized) buddy.requestLayout()
            try { wm.updateViewLayout(buddy, buddyLp) } catch (_: Exception) {}
        }
    }

    // ------------------------------------------------------------ duvar

    private fun goToWall() {
        wallLeft = (x + buddy.viewW / 2f) < screenW / 2f
        goalX = if (wallLeft) 0f else (screenW - buddy.viewW).toFloat()
        goalY = y
        walkPurpose = Walk.TO_WALL
        speed = 70f * d
        enter(State.WALK, 0f)
    }

    private fun startClimb() {
        walkPurpose = Walk.NORMAL
        val half = buddy.spriteH / 2f
        val cx = buddy.spriteCx
        // Ayaklar duvara değsin: döndürülmüş karakterin tabanı ekran kenarında.
        if (wallLeft) {
            buddy.rotationDeg = 90f
            buddy.facingLeft = true
            x = half - cx
        } else {
            buddy.rotationDeg = -90f
            buddy.facingLeft = false
            x = screenW - cx - half
        }
        climbStartY = y
        climbTopY = (y - (180f + rnd.nextFloat() * 260f) * d).coerceAtLeast(90f * d)
        speed = 45f * d
        enter(State.CLIMB, 0f)
    }

    private fun jumpOffWall() {
        buddy.rotationDeg = 0f
        buddy.facingLeft = !wallLeft
        x = if (wallLeft) 8f * d else (screenW - buddy.viewW).toFloat() - 8f * d
        goalY = climbStartY.coerceAtMost((screenH - buddy.viewH - 40f * d))
        fallVy = -500f * d
        enter(State.FALL, 0f)
    }

    // ------------------------------------------------------------ işler

    private fun inJob() = state == State.PAINT || state == State.COOK || state == State.CLEAN || chaos != null ||
        (state == State.SHOWOFF && afterShowoff != null)

    private fun setPads(left: Float, right: Float) {
        buddy.padL = left
        buddy.padR = right
        clampToScreen()
    }

    private fun sideWithRoom(): Int = if (x + buddy.viewW / 2f > screenW / 2f) -1 else 1

    private fun startJob(job: String) {
        closeMenu()
        clearJob()
        buddy.rotationDeg = 0f
        when (job) {
            "paint" -> {
                val side = sideWithRoom()
                buddy.propSide = side
                if (side > 0) setPads(0f, BuddyView.EASEL_PAD) else setPads(BuddyView.EASEL_PAD, 0f)
                buddy.facingLeft = side < 0
                buddy.hat = BuddyView.Hat.BERET
                buddy.tool = BuddyView.Tool.BRUSH
                buddy.prop = BuddyView.Prop.EASEL
                val i = rnd.nextInt(BuddyView.PICTURES.size)
                buddy.picture = BuddyView.PICTURES[i]
                buddy.paintColor = BuddyView.PAINT_COLORS[i]
                buddy.paintProgress = 0f
                enter(State.PAINT, 10f)
            }
            "cook" -> {
                val side = sideWithRoom()
                buddy.propSide = side
                if (side > 0) setPads(0f, 12f) else setPads(12f, 0f)
                buddy.facingLeft = side < 0
                buddy.hat = BuddyView.Hat.CHEF
                buddy.tool = BuddyView.Tool.SPOON
                buddy.prop = BuddyView.Prop.POT
                enter(State.COOK, 8f)
            }
            "clean" -> {
                setPads(7f, 7f)
                buddy.tool = BuddyView.Tool.BROOM
                cleanStops = 4
                speed = 62f * d
                pickGoal(lowerHalf = true, minDist = 110f)
                enter(State.CLEAN, 0f)
            }
            "dance" -> enter(State.DANCE, 6.5f)
            "chaos" -> { startChaos(); return }
            "wardrobe" -> {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                enter(State.WAVE, 1.4f)
            }
            "sleep" -> enter(State.SLEEP, if (isNight()) 30f else 12f)
        }
        applyPosition()
    }

    private fun showoff(dur: Float, then: () -> Unit) {
        heartLeft = HEART_TIME
        afterShowoff = then
        enter(State.SHOWOFF, dur)
    }

    private fun clearJob() {
        buddy.tool = BuddyView.Tool.NONE
        buddy.hat = BuddyView.Hat.NONE
        buddy.prop = BuddyView.Prop.NONE
        buddy.picture = null
        afterShowoff = null
        setPads(0f, 0f)
    }

    private fun emitPuff() {
        val dir = if (buddy.facingLeft) -1f else 1f
        val lx = buddy.spriteCx + dir * (buddy.spriteW / 2f + buddy.pixel * 2.5f)
        val ly = buddy.groundY - buddy.pixel
        buddy.puffs.add(BuddyView.Puff(buddy.winX + lx, buddy.winY + ly))
    }

    private fun updatePuffs(dt: Float) {
        if (buddy.puffs.isEmpty()) return
        val it = buddy.puffs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age > BuddyView.PUFF_LIFE) it.remove()
        }
    }

    // ------------------------------------------------------------ kaos

    private fun startChaos() {
        removeChaos()
        clearJob()
        buddy.rotationDeg = 0f
        val v = ChaosView(this)
        val lp = WindowManager.LayoutParams(
            screenW, screenH, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Ekrana dokununca kaos hemen biter, temizliğe geçilir.
        v.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) skipToClean()
            true
        }
        try {
            wm.addView(v, lp)
        } catch (_: Exception) {
            return
        }
        chaos = v
        // karakter kaos katmanının üstünde kalsın
        try {
            wm.removeView(buddy)
            wm.addView(buddy, buddyLp)
        } catch (_: Exception) {}
        chaosActs.clear()
        val pool = mutableListOf("tomato", "egg", "spray", "crack")
        pool += List(3) { listOf("tomato", "egg", "spray", "crack", "tomato", "egg").random(rnd) }
        pool.shuffle(rnd)
        chaosActs.addAll(pool)
        nextChaosAct()
    }

    private fun nextChaosAct() {
        if (chaosActs.isEmpty()) {
            buddy.tool = BuddyView.Tool.NONE
            heartLeft = HEART_TIME
            enter(State.CHAOS_REST, 1.6f)
            return
        }
        chaosAct = chaosActs.removeAt(0)
        buddy.tool = when (chaosAct) {
            "tomato" -> BuddyView.Tool.TOMATO
            "egg" -> BuddyView.Tool.EGG
            "spray" -> BuddyView.Tool.SPRAY
            else -> BuddyView.Tool.HAMMER
        }
        buddy.toolPhase = 0
        walkPurpose = Walk.NORMAL
        pickGoal(lowerHalf = chaosAct != "crack", minDist = 120f)
        speed = 260f * d
        enter(State.CHAOS_MOVE, 0f)
    }

    private fun handX() = buddy.winX + buddy.spriteCx + (if (buddy.facingLeft) -1f else 1f) * buddy.spriteW * 0.55f
    private fun handY() = buddy.winY + buddy.groundY - buddy.spriteH * 0.8f

    private fun beginChaosAct() {
        chaosDone = false
        when (chaosAct) {
            "spray" -> {
                buddy.paintColor = BuddyView.PAINT_COLORS[rnd.nextInt(BuddyView.PAINT_COLORS.size)]
                val cx = (buddy.winX + buddy.spriteCx).coerceIn(110f * d, screenW - 110f * d)
                val cy = (buddy.winY - 120f * d).coerceIn(140f * d, screenH - 200f * d)
                val dur = chaos?.startSpray(cx, cy) ?: 1f
                enter(State.CHAOS_ACT, dur + 0.3f)
            }
            "crack" -> enter(State.CHAOS_ACT, 1.1f)
            else -> {
                // hedef: karakterden uzak, ekranın üst kısmında bir nokta
                enter(State.CHAOS_ACT, 0.9f)
            }
        }
    }

    private fun updateChaosAct() {
        val v = chaos ?: run { chooseNext(); return }
        when (chaosAct) {
            "tomato", "egg" -> {
                buddy.frame = if (stateTime < 0.35f) "stretch" else "hop"
                if (!chaosDone && stateTime >= 0.35f) {
                    chaosDone = true
                    val tx = 40f * d + rnd.nextFloat() * (screenW - 80f * d)
                    val ty = 90f * d + rnd.nextFloat() * (screenH * 0.6f)
                    buddy.facingLeft = tx < buddy.winX + buddy.spriteCx
                    v.throwThing(chaosAct == "egg", handX(), handY(), tx, ty)
                    buddy.tool = BuddyView.Tool.NONE
                }
                buddy.lift = if (stateTime > 0.35f) sin(((stateTime - 0.35f) / 0.55f).coerceIn(0f, 1f) * Math.PI.toFloat()) * buddy.pixel * 4f else 0f
            }
            "spray" -> {
                buddy.toolPhase = ((stateTime / 0.12f).toInt() % 2)
                buddy.frame = blinkFrame("stand")
                v.sprayTip()?.let { (tx, _) -> buddy.facingLeft = tx < buddy.winX + buddy.spriteCx - 4f * d }
            }
            "crack" -> {
                buddy.toolPhase = if (stateTime < 0.5f) 0 else 1
                buddy.frame = if (stateTime < 0.5f) "stand" else "hop"
                if (!chaosDone && stateTime >= 0.5f) {
                    chaosDone = true
                    val dir = if (buddy.facingLeft) -1f else 1f
                    v.crack(buddy.winX + buddy.spriteCx + dir * buddy.spriteW * 0.9f, buddy.winY + buddy.groundY - buddy.spriteH * 0.6f)
                }
            }
        }
        if (stateTime >= stateDur) nextChaosAct()
    }

    private fun beginChaosClean() {
        buddy.tool = BuddyView.Tool.SPONGE
        buddy.facingLeft = false
        y = (screenH - buddy.viewH - 90f * d)
        x = -buddy.spriteCx
        chaos?.wipe = 0f
        enter(State.CHAOS_CLEAN, 2.6f)
    }

    private fun skipToClean() {
        if (chaos == null || state == State.CHAOS_CLEAN) return
        chaosActs.clear()
        beginChaosClean()
        stateDur = 1.6f
    }

    private fun removeChaos() {
        val v = chaos ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
        chaos = null
    }

    // ------------------------------------------------------------ menü

    private fun openMenu() {
        if (chaos != null) return
        closeMenu()
        if (state == State.CLIMB || state == State.HANG) {
            buddy.rotationDeg = 0f
            clampToScreen()
        }
        clearJob()
        enter(State.MENU, 0f)
        buddy.facingLeft = false

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.menu_bg)
            setPadding((10 * d).toInt(), (10 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        }
        card.addView(TextView(this).apply {
            text = Store.displayName(this@BuddyService) + ": " + getString(R.string.menu_title)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), (6 * d).toInt())
        })
        val items = listOf(
            R.string.job_paint to "paint", R.string.job_cook to "cook", R.string.job_clean to "clean",
            R.string.job_dance to "dance", R.string.job_sleep to "sleep",
            R.string.job_chaos to "chaos", R.string.job_wardrobe to "wardrobe",
        )
        for ((label, job) in items) {
            card.addView(TextView(this).apply {
                text = getString(label)
                textSize = 15f
                setTextColor(0xFFF1F1F4.toInt())
                setBackgroundResource(R.drawable.menu_item_bg)
                setPadding((10 * d).toInt(), (9 * d).toInt(), (22 * d).toInt(), (9 * d).toInt())
                isClickable = true
                setOnClickListener { startJob(job) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        card.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                closeMenu()
                if (state == State.MENU) chooseNext()
            }
            false
        }
        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val mw = card.measuredWidth
        val mh = card.measuredHeight
        val bx = buddy.winX + buddy.spriteCx
        val btop = buddy.winY + buddy.groundY - buddy.spriteH
        var my = btop - mh - 6f * d
        if (my < 40f * d) my = buddy.winY + buddy.viewH + 6f * d
        my = my.coerceAtMost(screenH - mh - 20f * d)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (bx - mw / 2f).coerceIn(8f * d, (screenW - mw - 8f * d).coerceAtLeast(8f * d)).toInt()
            this.y = my.toInt()
        }
        try {
            wm.addView(card, lp)
            menu = card
        } catch (_: Exception) {}
    }

    private fun closeMenu() {
        val m = menu ?: return
        try { wm.removeView(m) } catch (_: Exception) {}
        menu = null
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
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            openMenu()
        }
        buddy.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = x
                    startY = y
                    dragging = false
                    longPressed = false
                    handler.postDelayed(longPress, 450L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = e.rawX - downX
                    val my = e.rawY - downY
                    if (!dragging && !longPressed && hypot(mx, my) > slop) {
                        handler.removeCallbacks(longPress)
                        dragging = true
                        closeMenu()
                        removeChaos()
                        clearJob()
                        if (buddy.rotationDeg != 0f) {
                            buddy.rotationDeg = 0f
                            clampToScreen()
                            startX = x
                            startY = y
                        }
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
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        val close = target.active
                        hideTarget()
                        if (close) {
                            beginClose()
                        } else {
                            goalY = (y + 24f * d).coerceAtMost((screenH - buddy.viewH - 40f * d))
                            fallVy = 0f
                            enter(State.FALL, 0f)
                        }
                    } else if (!longPressed && e.actionMasked == MotionEvent.ACTION_UP) {
                        if (inJob()) {
                            heartLeft = HEART_TIME      // iş başındayken sadece kalp
                        } else if (state != State.MENU) {
                            buddy.rotationDeg = 0f
                            clampToScreen()
                            startHop(happy = true)
                        }
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun targetCenter(): Pair<Float, Float> = Pair(screenW / 2f, 110f * d)

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
        val bx = buddy.winX + buddy.spriteCx
        val by = buddy.winY + buddy.viewH * 0.6f
        target.active = hypot(bx - cx, by - cy) < 85f * d
    }

    private fun beginClose() {
        if (state == State.CLOSING) return
        hideTarget()
        closeMenu()
        enter(State.CLOSING, 0.35f)
        if (!running) stopSelf()
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
            .setContentTitle(Store.displayName(this))
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
