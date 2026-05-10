package com.example.campussos.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.campussos.R

class PermissionExplanationActivity : Activity() {
    private val permissionType: PermissionType by lazy {
        PermissionType.fromValue(intent.getStringExtra(EXTRA_PERMISSION_TYPE))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_explanation)

        findViewById<TextView>(R.id.permission_title).setText(permissionType.titleRes)
        findViewById<TextView>(R.id.permission_body).setText(permissionType.bodyRes)

        findViewById<Button>(R.id.permission_continue_button).setOnClickListener {
            requestPermissionIfNeeded()
        }

        findViewById<Button>(R.id.permission_skip_button).setOnClickListener {
            finish()
        }
    }

    private fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            finish()
            return
        }

        if (checkSelfPermission(permissionType.permission) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }

        requestPermissions(arrayOf(permissionType.permission), REQUEST_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            finish()
        }
    }

    private enum class PermissionType(
        val value: String,
        val permission: String,
        val titleRes: Int,
        val bodyRes: Int
    ) {
        Sms(
            value = "sms",
            permission = Manifest.permission.SEND_SMS,
            titleRes = R.string.permission_sms_title,
            bodyRes = R.string.permission_sms_body
        ),
        Call(
            value = "call",
            permission = Manifest.permission.CALL_PHONE,
            titleRes = R.string.permission_call_title,
            bodyRes = R.string.permission_call_body
        ),
        Location(
            value = "location",
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            titleRes = R.string.permission_location_title,
            bodyRes = R.string.permission_location_body
        );

        companion object {
            fun fromValue(value: String?): PermissionType {
                return values().firstOrNull { it.value == value } ?: Sms
            }
        }
    }

    companion object {
        private const val EXTRA_PERMISSION_TYPE = "permission_type"
        private const val REQUEST_PERMISSION = 501

        fun startForSms(context: Context) {
            start(context, PermissionType.Sms)
        }

        fun startForCall(context: Context) {
            start(context, PermissionType.Call)
        }

        fun startForLocation(context: Context) {
            start(context, PermissionType.Location)
        }

        private fun start(context: Context, permissionType: PermissionType) {
            val intent = Intent(context, PermissionExplanationActivity::class.java).apply {
                putExtra(EXTRA_PERMISSION_TYPE, permissionType.value)
            }
            context.startActivity(intent)
        }
    }
}
