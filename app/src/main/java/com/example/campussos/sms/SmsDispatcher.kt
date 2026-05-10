package com.example.campussos.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager

class SmsDispatcher(private val context: Context) {
    fun sendEmergencySms(
        phoneNumbers: List<String>,
        message: String
    ): SmsSendResult {
        if (!hasSmsPermission()) {
            return SmsSendResult.MissingPermission
        }

        val contacts = phoneNumbers
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (contacts.isEmpty()) {
            return SmsSendResult.NoContacts
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        return try {
            contacts.forEach { phoneNumber ->
                val messageParts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    null,
                    null
                )
            }
            SmsSendResult.Success(contacts.size)
        } catch (exception: RuntimeException) {
            SmsSendResult.Failure(exception.message.orEmpty())
        }
    }

    private fun hasSmsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
