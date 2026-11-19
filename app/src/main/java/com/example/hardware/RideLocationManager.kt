package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class LiveRideTelemetry(
    val trackingActive: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val speedKmh: Int = 0,
    val maxSpeedKmh: Int = 0,
    val headingDegrees: Float = 0f,
    val tripDistanceKm: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val gpsAccuracyMeters: Float? = null,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val startedAtMs: Long? = null,
    val statusText: String = "Tap Start Live GPS Ride for speed & trip distance"
) {
    val compassCardinal: String
        get() {
            val normalized = ((headingDegrees % 360f) + 360f) % 360f
            val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            return dirs[((normalized + 22.5f) / 45f).toInt() % 8]
        }
}

class RideLocationManager(context: Context) {
    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val _state = MutableStateFlow(LiveRideTelemetry())
    val state: StateFlow<LiveRideTelemetry> = _state.asStateFlow()

    private var lastLocation: Location? = null
    private var locationCallback: LocationCallback? = null

    init {
        refreshPermission()
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun refreshPermission() {
        _state.update { it.copy(locationPermissionGranted = hasLocationPermission()) }
    }

    @SuppressLint("MissingPermission")
    fun startRideTracking() {
        if (!hasLocationPermission()) {
            _state.update {
                it.copy(
                    locationPermissionGranted = false,
                    statusText = "Location permission required for live GPS speedometer"
                )
            }
            return
        }
        stopRideTracking()
        lastLocation = null

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt().coerceAtLeast(0) else 0
                val deltaMeters = lastLocation?.let { prev ->
                    val d = prev.distanceTo(loc)
                    if (d in 2.5f..500f) d.toDouble() else 0.0
                } ?: 0.0
                lastLocation = loc

                _state.update { current ->
                    current.copy(
                        speedKmh = speedKmh,
                        maxSpeedKmh = maxOf(current.maxSpeedKmh, speedKmh),
                        headingDegrees = if (loc.hasBearing()) loc.bearing else current.headingDegrees,
                        tripDistanceKm = current.tripDistanceKm + (deltaMeters / 1000.0),
                        altitudeMeters = if (loc.hasAltitude()) loc.altitude else current.altitudeMeters,
                        gpsAccuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                        currentLatitude = loc.latitude,
                        currentLongitude = loc.longitude,
                        statusText = "GPS lock active (±${loc.accuracy.roundToInt()}m)"
                    )
                }
            }
        }

        locationCallback = callback
        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            _state.update {
                LiveRideTelemetry(
                    trackingActive = true,
                    locationPermissionGranted = true,
                    startedAtMs = System.currentTimeMillis(),
                    statusText = "Acquiring GPS satellites…"
                )
            }
        } catch (e: SecurityException) {
            _state.update { it.copy(statusText = "Location permission denied") }
        }
    }

    fun stopRideTracking() {
        val cb = locationCallback
        if (cb != null) {
            fusedClient.removeLocationUpdates(cb)
            locationCallback = null
        }
        _state.update {
            it.copy(
                trackingActive = false,
                speedKmh = 0,
                statusText = if (it.tripDistanceKm > 0.05) {
                    "Ride paused • ${String.format("%.1f", it.tripDistanceKm)} km recorded"
                } else {
                    "Tap Start Live GPS Ride for speed & trip distance"
                }
            )
        }
    }

    fun resetTrip() {
        lastLocation = null
        _state.update {
            it.copy(
                tripDistanceKm = 0.0,
                maxSpeedKmh = 0,
                speedKmh = 0,
                startedAtMs = if (it.trackingActive) System.currentTimeMillis() else null
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLastKnownLocation(onResult: (Pair<Double, Double>?) -> Unit) {
        val cached = lastLocation
        if (cached != null) {
            onResult(cached.latitude to cached.longitude)
            return
        }
        val stateLat = _state.value.currentLatitude
        val stateLng = _state.value.currentLongitude
        if (stateLat != null && stateLng != null) {
            onResult(stateLat to stateLng)
            return
        }
        if (!hasLocationPermission()) {
            onResult(null)
            return
        }
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        lastLocation = loc
                        _state.update {
                            it.copy(
                                currentLatitude = loc.latitude,
                                currentLongitude = loc.longitude
                            )
                        }
                        onResult(loc.latitude to loc.longitude)
                    } else {
                        onResult(null)
                    }
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } catch (_: SecurityException) {
            onResult(null)
        }
    }

    fun release() {
        stopRideTracking()
    }
}
