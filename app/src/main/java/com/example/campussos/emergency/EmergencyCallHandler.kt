package com.example.campussos.emergency

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

class EmergencyCallHandler(private val context: Context) {
    fun placeOptionalCall(
        enabled: Boolean,
        phoneNumber: String?
    ): EmergencyCallResult {
        if (!enabled) return EmergencyCallResult.Skipped

        val number = phoneNumber.orEmpty().trim()
        if (number.isBlank()) return EmergencyCallResult.NoNumber
        if (!hasCallPermission()) return EmergencyCallResult.MissingPermission

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(callIntent)
            EmergencyCallResult.Started
        } catch (exception: RuntimeException) {
            EmergencyCallResult.Failure(exception.message.orEmpty())
        }
    }

    private fun hasCallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
