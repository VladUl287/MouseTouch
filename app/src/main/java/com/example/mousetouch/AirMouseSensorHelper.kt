package com.example.mousetouch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class AirMouseSensorHelper(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val onMovement: (dx: Int, dy: Int) -> Unit
) : LifecycleObserver, SensorEventListener {
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private var lastYaw = 0f
    private var lastPitch = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val sensitivity = 250f

    init {
        lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val yaw = orientation[0] // azimuth (Z axis)
            val pitch = orientation[1] // pitch (X axis)

            val dx = ((yaw - lastYaw) * sensitivity).toInt()
            val dy = ((pitch - lastPitch) * sensitivity).toInt()

            lastYaw = yaw
            lastPitch = pitch

            onMovement(dx, dy)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}