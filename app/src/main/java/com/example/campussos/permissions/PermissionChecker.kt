package com.example.campussos.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class PermissionChecker(private val context: Context) {
    fun needsSmsPermission(): Boolean = !canSendSms()

    fun needsCallPermission(): Boolean = !canCallPhone()

    fun needsLocationPermission(): Boolean = !canAccessLocation()

    fun canAccessLocation(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun canSendSms(): Boolean {
        return hasPermission(Manifest.permission.SEND_SMS)
    }

    fun canCallPhone(): Boolean {
        return hasPermission(Manifest.permission.CALL_PHONE)
    }

    private fun hasPermission(permission: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
