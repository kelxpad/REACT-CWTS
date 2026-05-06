package com.example.campussos.emergency

import com.example.campussos.location.LocationResult

class EmergencyMessageBuilder {
    fun buildEmergencyMessage(
        baseMessage: String,
        includeLocation: Boolean,
        locationResult: LocationResult?
    ): String {
        val message = baseMessage.ifBlank { DEFAULT_MESSAGE }
        if (!includeLocation || locationResult !is LocationResult.Available) {
            return message
        }

        return "$message\nLocation: ${locationResult.mapsLink}"
    }

    companion object {
        const val DEFAULT_MESSAGE = "Emergency SOS: I need help on campus."
    }
}
