package com.example.campussos.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import com.example.campussos.R
import com.example.campussos.emergency.EmergencyDispatchService
import com.example.campussos.permissions.PermissionChecker

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.main_sos_button).setOnClickListener {
            val permissionChecker = PermissionChecker(this)
            if (permissionChecker.needsSmsPermission()) {
                PermissionExplanationActivity.startForSms(this)
            } else {
                EmergencyDispatchService.start(this)
            }
        }

        findViewById<Button>(R.id.main_location_permission_button).setOnClickListener {
            if (PermissionChecker(this).needsLocationPermission()) {
                PermissionExplanationActivity.startForLocation(this)
            }
        }

        findViewById<Button>(R.id.main_call_permission_button).setOnClickListener {
            if (PermissionChecker(this).needsCallPermission()) {
                PermissionExplanationActivity.startForCall(this)
            }
        }
    }
}
