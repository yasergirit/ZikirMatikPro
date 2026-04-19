package com.yasergirit.zikirmasterpro.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure math utility for Qibla direction calculation.
 * All functions are stateless and unit-testable.
 */
object QiblaCalculator {

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LNG = 39.8262

    /**
     * Calculates the initial bearing (azimuth) from the user's location to the Kaaba.
     * Uses the forward azimuth formula for great-circle navigation.
     *
     * @return Bearing in degrees, normalized to 0..360 where 0 = North, 90 = East.
     */
    fun calculateQiblaBearing(userLat: Double, userLng: Double): Float {
        val lat1 = Math.toRadians(userLat)
        val lat2 = Math.toRadians(KAABA_LAT)
        val dLng = Math.toRadians(KAABA_LNG - userLng)

        // Forward azimuth formula
        val x = sin(dLng) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        val bearing = Math.toDegrees(atan2(x, y))
        return normalizeDegrees(bearing.toFloat())
    }

    /**
     * Computes the angle the Qibla indicator should point at, relative to the device's top.
     *
     * When the device faces north (azimuth=0) and Qibla is at 150 degrees,
     * the indicator should point at 150 degrees clockwise from screen top.
     *
     * When the device faces east (azimuth=90) and Qibla is at 150 degrees,
     * the indicator should point at 60 degrees clockwise from screen top.
     *
     * @param qiblaBearing Qibla bearing from true/magnetic north (0..360)
     * @param deviceAzimuth Device heading from true/magnetic north (0..360)
     * @return Relative angle (0..360) for the Qibla indicator on screen
     */
    fun relativeQiblaAngle(qiblaBearing: Float, deviceAzimuth: Float): Float {
        return normalizeDegrees(qiblaBearing - deviceAzimuth)
    }

    /**
     * Normalizes any degree value to the range 0..360.
     * Handles negative values and values > 360 correctly.
     */
    fun normalizeDegrees(degrees: Float): Float {
        var v = degrees % 360f
        if (v < 0f) v += 360f
        return v
    }

}
