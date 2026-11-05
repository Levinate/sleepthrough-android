package com.sleepthrough.app

import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.changes.DeletionChange
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.*
import java.time.Instant

@CapacitorPlugin(name = "SleepHealth")
class SleepHealthPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private fun client() = HealthConnectClient.getOrCreate(context)
    private fun launch(call: PluginCall, block: suspend () -> JSObject) {
        scope.launch {
            try {
                if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) { call.reject("Health Connect needs installation or an update", "unavailable"); return@launch }
                if (!client().permissionController.getGrantedPermissions().contains(permission)) { call.reject("Sleep permission is not granted", "permission_denied"); return@launch }
                call.resolve(block())
            } catch (e: SecurityException) { call.reject("Sleep permission was removed", "permission_denied") }
              catch (e: Exception) { call.reject("Health Connect could not finish this request. Please retry.", "health_error") }
        }
    }
    @PluginMethod fun getAvailability(call: PluginCall) {
        val status = HealthConnectClient.getSdkStatus(context)
        call.resolve(JSObject().put("status", when(status) { HealthConnectClient.SDK_AVAILABLE -> "available"; HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"; else -> "unavailable" }))
    }
    @PluginMethod fun getSleepPermission(call: PluginCall) {
        scope.launch {
            try { call.resolve(JSObject().put("granted", HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE && client().permissionController.getGrantedPermissions().contains(permission))) }
            catch(e: Exception) { call.resolve(JSObject().put("granted", false)) }
        }
    }
    @PluginMethod fun requestSleepPermission(call: PluginCall) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) { call.reject("Install or update Health Connect first", "unavailable"); return }
        activity.runOnUiThread { (activity as MainActivity).requestSleepPermission(call) }
    }
    @PluginMethod fun openSettings(call: PluginCall) {
        activity.runOnUiThread {
            try {
                val intent = if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
                activity.startActivity(intent); call.resolve()
            } catch(e: Exception) { call.reject("Open Health Connect in your phone settings", "unavailable") }
        }
    }
    private fun serialize(record: SleepSessionRecord): JSObject {
        val stages = JSArray()
        record.stages.forEach { s -> stages.put(JSObject().put("start", s.startTime.toString()).put("end", s.endTime.toString()).put("stage", s.stage)) }
        return JSObject().put("id", record.metadata.id).put("origin", record.metadata.dataOrigin.packageName)
            .put("modified", record.metadata.lastModifiedTime.toString()).put("start", record.startTime.toString()).put("end", record.endTime.toString())
            .put("endOffsetSeconds", record.endZoneOffset?.totalSeconds ?: org.json.JSONObject.NULL).put("stages", stages)
    }
    @PluginMethod fun readSleepSessions(call: PluginCall) = launch(call) {
        val from = Instant.parse(call.getString("from"))
        val to = Instant.parse(call.getString("to"))
        require(from < to && java.time.Duration.between(from,to).toDays() <= 32)
        val result = client().readRecords(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(from, to), pageSize = 100, pageToken = call.getString("pageToken")))
        JSObject().put("records", JSArray().apply { result.records.forEach { put(serialize(it)) } }).put("nextPageToken", result.pageToken ?: org.json.JSONObject.NULL)
    }
    @PluginMethod fun getChangesToken(call: PluginCall) = launch(call) {
        JSObject().put("token", client().getChangesToken(ChangesTokenRequest(setOf(SleepSessionRecord::class))))
    }
    @PluginMethod fun readChanges(call: PluginCall) = launch(call) {
        val result = client().getChanges(call.getString("token") ?: "")
        val upserts = JSArray(); val deletions = JSArray()
        result.changes.forEach { change -> when(change) {
            is UpsertionChange -> (change.record as? SleepSessionRecord)?.let { upserts.put(serialize(it)) }
            is DeletionChange -> deletions.put(change.recordId)
        } }
        JSObject().put("upserts", upserts).put("deletions", deletions).put("nextToken", result.nextChangesToken).put("hasMore", result.hasMore).put("expired", result.changesTokenExpired)
    }
    override fun handleOnDestroy() { scope.cancel(); super.handleOnDestroy() }
}
