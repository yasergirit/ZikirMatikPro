package com.yasergirit.zikirmasterpro.qibla

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Provides the user's location using FusedLocationProviderClient.
 *
 * Exposes location as a StateFlow for clean ViewModel consumption.
 * Must call [startUpdates] after permission is granted, and [stopUpdates] when done.
 */
class QiblaLocationProvider(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<Location?>(null)

    /** Current best known location. Null until a location fix is obtained. */
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 10_000L
    ).setMinUpdateIntervalMillis(5_000L).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _location.value = it }
        }
    }

    /** Whether device location services are enabled (GPS or Network). */
    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Start location updates. Only call after location permission is granted.
     * Also fetches last known location for immediate availability.
     */
    @SuppressLint("MissingPermission")
    fun startUpdates() {
        // Get last known location immediately
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) _location.value = loc
        }
        // Request ongoing updates
        fusedClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    /** Stop location updates to save battery. */
    fun stopUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }
}
