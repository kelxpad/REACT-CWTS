package com.example.campussos.emergency

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.campussos.R
import com.example.campussos.location.LocationProvider
import com.example.campussos.location.LocationResult
import com.example.campussos.sms.SmsDispatcher
import com.example.campussos.sms.SmsSendResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class EmergencyDispatchService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var countdownRunnable: Runnable? = null
    private var remainingSeconds = CONFIRMATION_SECONDS
    private var isCanceled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelEmergencyFlow()
            else -> startConfirmationCountdown()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        countdownRunnable?.let(mainHandler::removeCallbacks)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun startConfirmationCountdown() {
        isCanceled = false
        remainingSeconds = CONFIRMATION_SECONDS
        startForeground(FOREGROUND_NOTIFICATION_ID, buildCountdownNotification(remainingSeconds))

        countdownRunnable?.let(mainHandler::removeCallbacks)
        countdownRunnable = object : Runnable {
            override fun run() {
                if (isCanceled) return

                if (remainingSeconds <= 0) {
                    executeEmergencyTrigger()
                    return
                }

                notifyStatus(buildCountdownNotification(remainingSeconds))
                remainingSeconds -= 1
                mainHandler.postDelayed(this, ONE_SECOND_MILLIS)
            }
        }

        mainHandler.post(countdownRunnable!!)
    }

    private fun cancelEmergencyFlow() {
        isCanceled = true
        countdownRunnable?.let(mainHandler::removeCallbacks)
        notifyStatus(buildStatusNotification(
            title = getString(R.string.emergency_canceled_title),
            body = getString(R.string.emergency_canceled_body),
            ongoing = false
        ))
        stopForeground(false)
        stopSelf()
    }

    private fun executeEmergencyTrigger() {
        notifyStatus(buildStatusNotification(
            title = getString(R.string.emergency_dispatch_title),
            body = getString(R.string.emergency_dispatch_body),
            ongoing = true
        ))

        executor.execute {
            val smsStatus = sendEmergencySms()
            val callStatus = placeOptionalCall()
            val finalBody = listOfNotNull(smsStatus, callStatus).joinToString(separator = " ")

            mainHandler.post {
                notifyStatus(buildStatusNotification(
                    title = getString(R.string.emergency_complete_title),
                    body = finalBody.ifBlank { getString(R.string.emergency_complete_body) },
                    ongoing = false
                ))
                stopForeground(false)
                stopSelf()
            }
        }
    }

    private fun sendEmergencySms(): String {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val contacts = preferences
            .getStringSet(KEY_SMS_CONTACTS, emptySet())
            .orEmpty()

        val baseMessage = preferences
            .getString(KEY_SMS_MESSAGE, getString(R.string.default_emergency_message))
            .orEmpty()
            .ifBlank { getString(R.string.default_emergency_message) }

        val includeLocation = preferences.getBoolean(KEY_INCLUDE_LOCATION, true)
        val locationResult = if (includeLocation) awaitEmergencyLocation() else null
        val message = EmergencyMessageBuilder().buildEmergencyMessage(
            baseMessage = baseMessage,
            includeLocation = includeLocation,
            locationResult = locationResult
        )

        return when (val result = SmsDispatcher(applicationContext).sendEmergencySms(contacts.toList(), message)) {
            is SmsSendResult.Success -> resources.getQuantityString(
                R.plurals.emergency_sms_requested,
                result.requestedCount,
                result.requestedCount
            )
            SmsSendResult.MissingPermission -> getString(R.string.emergency_sms_permission_missing)
            SmsSendResult.NoContacts -> getString(R.string.emergency_sms_no_contacts)
            is SmsSendResult.Failure -> getString(R.string.emergency_sms_failed, result.reason)
        }
    }

    private fun awaitEmergencyLocation(): LocationResult {
        val result = AtomicReference<LocationResult>(LocationResult.Unavailable)
        val latch = CountDownLatch(1)

        LocationProvider(applicationContext).getEmergencyLocation(
            timeoutMillis = LOCATION_TIMEOUT_MILLIS
        ) { locationResult ->
            result.set(locationResult)
            latch.countDown()
        }

        latch.await(LOCATION_TIMEOUT_MILLIS + LOCATION_TIMEOUT_BUFFER_MILLIS, TimeUnit.MILLISECONDS)
        return result.get()
    }

    private fun placeOptionalCall(): String? {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val result = EmergencyCallHandler(applicationContext).placeOptionalCall(
            enabled = preferences.getBoolean(KEY_CALL_ENABLED, false),
            phoneNumber = preferences.getString(KEY_CALL_NUMBER, null)
        )

        return when (result) {
            EmergencyCallResult.Started -> getString(R.string.emergency_call_started)
            EmergencyCallResult.Skipped -> null
            EmergencyCallResult.NoNumber -> getString(R.string.emergency_call_no_number)
            EmergencyCallResult.MissingPermission -> getString(R.string.emergency_call_permission_missing)
            is EmergencyCallResult.Failure -> getString(R.string.emergency_call_failed, result.reason)
        }
    }

    private fun buildCountdownNotification(secondsRemaining: Int): Notification {
        val cancelIntent = Intent(this, EmergencyDispatchService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return notificationBuilder()
            .setContentTitle(getString(R.string.emergency_countdown_title))
            .setContentText(getString(R.string.emergency_countdown_body, secondsRemaining))
            .setOngoing(true)
            .addAction(
                R.drawable.ic_sos,
                getString(R.string.emergency_cancel_action),
                cancelPendingIntent
            )
            .build()
    }

    private fun buildStatusNotification(
        title: String,
        body: String,
        ongoing: Boolean
    ): Notification {
        return notificationBuilder()
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(ongoing)
            .build()
    }

    private fun notificationBuilder(): Notification.Builder {
        ensureNotificationChannel()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_sos)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_HIGH)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.emergency_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notifyStatus(notification: Notification) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
    }

    companion object {
        const val PREFERENCES_NAME = "emergency_config"
        const val KEY_SMS_CONTACTS = "sms_contacts"
        const val KEY_SMS_MESSAGE = "sms_message"
        const val KEY_INCLUDE_LOCATION = "include_location"
        const val KEY_CALL_ENABLED = "call_enabled"
        const val KEY_CALL_NUMBER = "call_number"

        private const val CHANNEL_ID = "emergency_dispatch"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val REQUEST_START = 2001
        private const val REQUEST_CANCEL = 2002
        private const val CONFIRMATION_SECONDS = 3
        private const val ONE_SECOND_MILLIS = 1_000L
        private const val LOCATION_TIMEOUT_MILLIS = 4_000L
        private const val LOCATION_TIMEOUT_BUFFER_MILLIS = 500L

        private const val ACTION_START = "com.example.campussos.emergency.action.START"
        private const val ACTION_CANCEL = "com.example.campussos.emergency.action.CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, EmergencyDispatchService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, EmergencyDispatchService::class.java).apply {
                action = ACTION_START
            }
            return PendingIntent.getService(
                context,
                REQUEST_START,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
