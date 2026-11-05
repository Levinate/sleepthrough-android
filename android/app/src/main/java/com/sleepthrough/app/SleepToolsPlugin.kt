package com.sleepthrough.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.util.Calendar

@CapacitorPlugin(name="SleepTools",permissions=[Permission(strings=[Manifest.permission.POST_NOTIFICATIONS],alias="notifications")])
class SleepToolsPlugin : Plugin() {
    @PluginMethod fun setNightMode(call: PluginCall) {
        val enabled=call.getBoolean("enabled",false)?:false
        activity.runOnUiThread {
            val controller=WindowCompat.getInsetsController(activity.window,activity.window.decorView)
            if(enabled)controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
            val attrs=activity.window.attributes;attrs.screenBrightness=if(enabled).08f else -1f;activity.window.attributes=attrs
            call.resolve()
        }
    }
    @PluginMethod fun configureReminders(call: PluginCall) {
        if(call.getBoolean("enabled",false)==true && Build.VERSION.SDK_INT>=33 && getPermissionState("notifications")!=PermissionState.GRANTED){requestPermissionForAlias("notifications",call,"notificationPermission");return}
        applyReminders(call)
    }
    @PermissionCallback private fun notificationPermission(call: PluginCall){applyReminders(call)}
    private fun applyReminders(call: PluginCall){
        try {
            val requested=call.getBoolean("enabled",false)==true
            val enabled=requested&&NotificationManagerCompat.from(context).areNotificationsEnabled()
            val evening=call.getString("windDown","21:30")?:"21:30"
            val morning=call.getString("morning","07:00")?:"07:00"
            val pattern=Regex("^(0[6-9]|1[0-9]|2[01]):[0-5][0-9]$")
            if(!pattern.matches(evening)||!pattern.matches(morning)){call.reject("Choose times outside quiet hours","quiet_hours");return}
            val selected=call.getArray("weekdays")?.toList<Int>()?.filter{it in 0..6}?.distinct()?:listOf(0,1,2,3,4,5,6)
            context.getSharedPreferences("sleepthrough-reminders",0).edit().putBoolean("enabled",enabled).putString("evening",evening).putString("morning",morning).putString("days",selected.joinToString(",")).apply()
            ReminderScheduler.configure(context)
            call.resolve(JSObject().put("enabled",enabled).put("approximate",true))
        }catch(e:Exception){call.reject("Reminders could not be scheduled","reminders_unavailable")}
    }
}
object ReminderScheduler {
    const val CHANNEL="sleepthrough-practice"
    private fun pending(context:Context,id:Int):PendingIntent=PendingIntent.getBroadcast(context,id,Intent(context,SleepReminderReceiver::class.java).putExtra("reminderId",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun configure(context:Context){
        if(Build.VERSION.SDK_INT>=26){val channel=NotificationChannel(CHANNEL,"Practice reminders",NotificationManager.IMPORTANCE_DEFAULT);channel.description="Optional evening and morning practice reminders";context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)}
        val prefs=context.getSharedPreferences("sleepthrough-reminders",0)
        val alarm=context.getSystemService(AlarmManager::class.java)
        for(id in listOf(7001,7002)){
            val action=pending(context,id);alarm.cancel(action)
            if(!prefs.getBoolean("enabled",false))continue
            val selected=prefs.getString("days","0,1,2,3,4,5,6")!!.split(",").mapNotNull{it.toIntOrNull()}.toSet();if(selected.isEmpty())continue
            val parts=prefs.getString(if(id==7001)"evening" else "morning",if(id==7001)"21:30" else "07:00")!!.split(":").map{it.toInt()}
            val now=System.currentTimeMillis();val candidate=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,parts[0]);set(Calendar.MINUTE,parts[1]);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
            for(i in 0..7){if(candidate.timeInMillis>now&&selected.contains(candidate.get(Calendar.DAY_OF_WEEK)-1))break;candidate.add(Calendar.DATE,1)}
            alarm.setWindow(AlarmManager.RTC_WAKEUP,candidate.timeInMillis,15*60*1000L,action)
        }
    }
}
class SleepReminderReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val prefs=context.getSharedPreferences("sleepthrough-reminders",0)
        if(intent.action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_TIME_CHANGED)){ReminderScheduler.configure(context);return}
        val delivery=Calendar.getInstance();val hour=delivery.get(Calendar.HOUR_OF_DAY)
        val selected=prefs.getString("days","0,1,2,3,4,5,6")!!.split(",").mapNotNull{it.toIntOrNull()}.toSet()
        val id=intent.getIntExtra("reminderId",7001)
        if(prefs.getBoolean("enabled",false)&&hour in 6..21&&selected.contains(delivery.get(Calendar.DAY_OF_WEEK)-1)&&NotificationManagerCompat.from(context).areNotificationsEnabled()){
            val launch=Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP;putExtra("reminderRoute",if(id==7001)"path" else "today")}
            val open=PendingIntent.getActivity(context,id,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(context,ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.sleepthrough_icon)
                .setContentTitle("SleepThrough").setContentText(if(id==7001)"A little practice for your evening." else "A moment for your morning check-in.")
                .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            try{NotificationManagerCompat.from(context).notify(id,notification)}catch(_:SecurityException){}
        }
        ReminderScheduler.configure(context)
    }
}
