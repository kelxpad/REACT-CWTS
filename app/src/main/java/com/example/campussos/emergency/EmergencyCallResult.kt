package com.example.campussos.emergency

sealed class EmergencyCallResult {
    object Started : EmergencyCallResult()
    object Skipped : EmergencyCallResult()
    object NoNumber : EmergencyCallResult()
    object MissingPermission : EmergencyCallResult()
    data class Failure(val reason: String) : EmergencyCallResult()
}
