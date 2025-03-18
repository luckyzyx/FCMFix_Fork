package com.luckyzyx.fcmfix.hook

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.fcmfix.BuildConfig
import java.io.File

object HookUtils {

    fun isAllowPackage(list: Set<String>, packName: String): Boolean {
        return list.contains(packName) || packName == BuildConfig.APPLICATION_ID
    }

    private fun createFcmfixChannel(notificationManager: NotificationManager) {
        if (notificationManager.getNotificationChannel("fcmfix") == null) {
            val channel = NotificationChannel(
                "fcmfix", "FCMFix", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(
        context: Context, title: CharSequence, content: CharSequence, pendingIntent: PendingIntent?
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        createFcmfixChannel(notificationManager)
        val notification = NotificationCompat.Builder(context, "fcmfix").apply {
            setSmallIcon(android.R.drawable.ic_dialog_info)
            if (title.isBlank()) {
                setContentTitle("[FCMFix] $content")
                setContentText("")
            } else {
                setContentTitle("[FCMFix] $title")
                setContentText("[FCMFix] $content")
            }
            priority = NotificationCompat.PRIORITY_DEFAULT
            if (pendingIntent != null) {
                setContentIntent(pendingIntent).setAutoCancel(true)
            }
        }
        notificationManager.notify(System.currentTimeMillis().toInt(), notification.build())
    }

    fun Context.sendGsmLogBroadcast(text: String, throwable: Throwable? = null) {
        YLog.debug(text, throwable)
        val log = Intent("${BuildConfig.APPLICATION_ID}.log")
        log.putExtra("text", text)
        try {
            sendBroadcast(log)
        } catch (_: Throwable) {

        }
    }

    fun isFCMIntent(intent: Intent): Boolean {
        val action = intent.action
        return action != null && (action.endsWith(".android.c2dm.intent.RECEIVE") ||
                "com.google.firebase.MESSAGING_EVENT" == action ||
                "com.google.firebase.INSTANCE_ID_EVENT" == action)
    }

    fun fileIsExists(strFile: String): Boolean {
        return try {
            val file = File(strFile)
            file.exists()
        } catch (e: Exception) {
            false
        }
    }
}