package com.example.campussos.location

import android.location.Location
import java.util.Locale

object LocationFormatter {
    fun toGoogleMapsLink(latitude: Double, longitude: Double): String {
        val coordinates = String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
        return "https://maps.google.com/?q=$coordinates"
    }

    fun toGoogleMapsLink(location: Location): String {
        return toGoogleMapsLink(location.latitude, location.longitude)
    }
}
