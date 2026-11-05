package com.cincikgames.duvar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Telefonun eğimini -1..1 aralığında verir. Telefonun tutulduğu açıyı yavaşça
 * "normal" kabul eder; böylece yalnızca hareket ettirince paralaks oluşur.
 */
class TiltTracker(context: Context, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var baseX = 0f
    private var baseY = 0f
    private var hasBase = false
    private var smoothX = 0f
    private var smoothY = 0f
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = true
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]
        val y = e.values[1]
        if (!hasBase) {
            baseX = x
            baseY = y
            hasBase = true
        }
        baseX += (x - baseX) * 0.008f
        baseY += (y - baseY) * 0.008f
        val tx = ((baseX - x) / 3.0f).coerceIn(-1f, 1f)
        val ty = ((y - baseY) / 3.0f).coerceIn(-1f, 1f)
        smoothX += (tx - smoothX) * 0.12f
        smoothY += (ty - smoothY) * 0.12f
        onTilt(smoothX, smoothY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
