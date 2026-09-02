package com.geozelot.homer.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Fires [onShake] when the device is deliberately shaken, to extend a running sleep timer without
 * unlocking the phone.
 *
 * Registered only while a countdown is running AND the reader has the feature on, so it costs
 * nothing at rest. It used to be registered for every countdown, because there was no way to turn
 * shake-to-extend off at all.
 *
 * Everything about what counts as a shake lives in [ShakeGesture], which is a plain object with
 * tests. This class is the part that cannot be unit-tested — a `SensorEvent` cannot be constructed —
 * so it is kept to the two things that need a device: reading three axes, and the clock.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gesture = ShakeGesture()

    fun start() {
        accelerometer?.let {
            gesture.reset()
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        gesture.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values[0] / SensorManager.GRAVITY_EARTH
        val gy = event.values[1] / SensorManager.GRAVITY_EARTH
        val gz = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gx * gx + gy * gy + gz * gz)
        if (gesture.onMagnitude(gForce, SystemClock.elapsedRealtime())) onShake()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
