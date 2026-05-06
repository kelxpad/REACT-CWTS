package com.example.campussos.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class LocationProvider(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun getEmergencyLocation(
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        callback: (LocationResult) -> Unit
    ) {
        if (!hasLocationPermission()) {
            callback(LocationResult.PermissionDenied)
            return
        }

        val completed = AtomicBoolean(false)
        val cancellationTokenSource = CancellationTokenSource()

        val timeoutRunnable = Runnable {
            if (completed.compareAndSet(false, true)) {
                cancellationTokenSource.cancel()
                callback(LocationResult.Timeout)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, timeoutMillis)

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener(mainExecutor) { location ->
                    if (location != null) {
                        completeWithLocation(
                            location = location,
                            completed = completed,
                            timeoutRunnable = timeoutRunnable,
                            callback = callback
                        )
                    } else {
                        requestCurrentLocation(
                            cancellationTokenSource = cancellationTokenSource,
                            completed = completed,
                            timeoutRunnable = timeoutRunnable,
                            callback = callback
                        )
                    }
                }
                .addOnFailureListener(mainExecutor) {
                    requestCurrentLocation(
                        cancellationTokenSource = cancellationTokenSource,
                        completed = completed,
                        timeoutRunnable = timeoutRunnable,
                        callback = callback
                    )
                }
        } catch (securityException: SecurityException) {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                callback(LocationResult.PermissionDenied)
            }
        }
    }

    private fun requestCurrentLocation(
        cancellationTokenSource: CancellationTokenSource,
        completed: AtomicBoolean,
        timeoutRunnable: Runnable,
        callback: (LocationResult) -> Unit
    ) {
        try {
            fusedLocationClient
                .getCurrentLocation(currentLocationRequest(), cancellationTokenSource.token)
                .addOnSuccessListener(mainExecutor) { location ->
                    if (location != null) {
                        completeWithLocation(location, completed, timeoutRunnable, callback)
                    } else if (completed.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        callback(LocationResult.Unavailable)
                    }
                }
                .addOnFailureListener(mainExecutor) {
                    if (completed.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        callback(LocationResult.Unavailable)
                    }
                }
        } catch (securityException: SecurityException) {
            if (completed.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                callback(LocationResult.PermissionDenied)
            }
        }
    }

    private fun completeWithLocation(
        location: Location,
        completed: AtomicBoolean,
        timeoutRunnable: Runnable,
        callback: (LocationResult) -> Unit
    ) {
        if (completed.compareAndSet(false, true)) {
            mainHandler.removeCallbacks(timeoutRunnable)
            callback(
                LocationResult.Available(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    mapsLink = LocationFormatter.toGoogleMapsLink(location)
                )
            )
        }
    }

    private fun currentLocationRequest(): CurrentLocationRequest {
        return CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(DEFAULT_TIMEOUT_MILLIS)
            .setMaxUpdateAgeMillis(LAST_LOCATION_MAX_AGE_MILLIS)
            .build()
    }

    private fun hasPermission(permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 4_000L
        private const val LAST_LOCATION_MAX_AGE_MILLIS = 120_000L
    }
}
