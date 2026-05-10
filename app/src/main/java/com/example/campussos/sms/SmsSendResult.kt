package com.example.campussos.sms

sealed class SmsSendResult {
    data class Success(val requestedCount: Int) : SmsSendResult()
    object MissingPermission : SmsSendResult()
    object NoContacts : SmsSendResult()
    data class Failure(val reason: String) : SmsSendResult()
}
