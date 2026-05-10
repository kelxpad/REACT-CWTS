package com.example.campussos.location

sealed class LocationResult {
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val mapsLink: String
    ) : LocationResult()

    object Unavailable : LocationResult()
    object PermissionDenied : LocationResult()
    object Timeout : LocationResult()
}
