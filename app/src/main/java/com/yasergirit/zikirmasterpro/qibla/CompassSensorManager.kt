package com.yasergirit.zikirmasterpro.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages compass sensor registration, lifecycle, and azimuth computation.
 *
 * Strategy:
 *   1. Prefer TYPE_ROTATION_VECTOR (fuses gyro+accel+mag, most stable)
 *   2. Fallback to TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD
 *
 * Exposes azimuth as a StateFlow for clean ViewModel consumption.
 * Must call [start] and [stop] to manage sensor lifecycle.
 */
class CompassSensorManager(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val useRotationVector = rotationVectorSensor != null
    private val useFallback = !useRotationVector && accelerometer != null && magnetometer != null

    /** True if any usable compass sensor combination exists on this device. */
    val isSensorAvailable: Boolean = useRotationVector || useFallback

    private val _azimuth = MutableStateFlow<Float?>(null)

    /** Device azimuth in degrees (0..360, 0=North). Null until first sensor reading. */
    val azimuth: StateFlow<Float?> = _azimuth.asStateFlow()

    // Low-pass filter buffers for fallback sensors
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    // Smoothing: exponential low-pass on final azimuth
    private var smoothedAzimuth = -1f
    private val smoothingAlpha = 0.15f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            var rawAzimuth: Float? = null

            when {
                useRotationVector && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR -> {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    rawAzimuth = QiblaCalculator.normalizeDegrees(
                        Math.toDegrees(orientation[0].toDouble()).toFloat()
                    )
                }

                !useRotationVector && event.sensor.type == Sensor.TYPE_ACCELEROMETER -> {
                    lowPass(event.values, gravity)
                    hasGravity = true
                    rawAzimuth = computeFallbackAzimuth()
                }

                !useRotationVector && event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD -> {
                    lowPass(event.values, geomagnetic)
                    hasMagnetic = true
                    rawAzimuth = computeFallbackAzimuth()
                }
            }

            if (rawAzimuth != null) {
                val smoothed = applySmoothing(rawAzimuth)
                _azimuth.value = smoothed
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** Start listening to compass sensors. Call from lifecycle-aware scope. */
    fun start() {
        if (useRotationVector) {
            sensorManager.registerListener(
                listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME
            )
        } else if (useFallback) {
            sensorManager.registerListener(
                listener, accelerometer, SensorManager.SENSOR_DELAY_GAME
            )
            sensorManager.registerListener(
                listener, magnetometer, SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    /** Stop listening. Call when screen is not visible. */
    fun stop() {
        sensorManager.unregisterListener(listener)
        // Reset fallback state for clean restart
        hasGravity = false
        hasMagnetic = false
        smoothedAzimuth = -1f
    }

    private fun computeFallbackAzimuth(): Float? {
        if (!hasGravity || !hasMagnetic) return null
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (!SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) return null
        val orientation = FloatArray(3)
        SensorManager.getOrientation(r, orientation)
        return QiblaCalculator.normalizeDegrees(
            Math.toDegrees(orientation[0].toDouble()).toFloat()
        )
    }

    /**
     * Low-pass filter on raw sensor arrays.
     * Alpha 0.97 retains 97% of previous value -> very smooth.
     */
    private fun lowPass(input: FloatArray, output: FloatArray) {
        val alpha = 0.97f
        for (i in input.indices) {
            output[i] = alpha * output[i] + (1 - alpha) * input[i]
        }
    }

    /**
     * Exponential smoothing on final azimuth value.
     * Uses shortest-path interpolation to handle 0/360 wraparound.
     */
    private fun applySmoothing(raw: Float): Float {
        if (smoothedAzimuth < 0f) {
            smoothedAzimuth = raw
            return raw
        }
        val diff = QiblaCalculator.sanitizeForAnimation(raw, smoothedAzimuth) - smoothedAzimuth
        smoothedAzimuth = QiblaCalculator.normalizeDegrees(smoothedAzimuth + diff * smoothingAlpha)
        return smoothedAzimuth
    }
}
