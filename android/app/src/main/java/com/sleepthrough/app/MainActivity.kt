package com.sleepthrough.app

import android.os.Bundle
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import com.getcapacitor.BridgeActivity
import com.getcapacitor.PluginCall
import com.getcapacitor.JSObject

class MainActivity : BridgeActivity() {
    private var permissionCall: PluginCall? = null
    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val permissionLauncher = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        permissionCall?.resolve(JSObject().put("granted", granted.contains(sleepPermission)))
        permissionCall = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(SleepHealthPlugin::class.java)
        registerPlugin(SecureVaultPlugin::class.java)
        registerPlugin(SleepToolsPlugin::class.java)
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.rgb(11,27,43))
        findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(android.graphics.Color.rgb(11,27,43))
    }
    fun requestSleepPermission(call: PluginCall) {
        if (permissionCall != null) { call.reject("A permission request is already open", "busy"); return }
        permissionCall = call
        permissionLauncher.launch(setOf(sleepPermission))
    }
}
