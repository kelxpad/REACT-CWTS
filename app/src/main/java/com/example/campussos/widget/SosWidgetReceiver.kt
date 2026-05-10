package com.example.campussos.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.campussos.emergency.EmergencyDispatchService
import com.example.campussos.widget.SosWidgetProvider.Companion.ACTION_SOS_WIDGET_CLICK

class SosWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SOS_WIDGET_CLICK) return

        EmergencyDispatchService.start(context.applicationContext)
    }
}
