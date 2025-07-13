package com.luckyzyx.fcmfix.hook

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.fcmfix.BuildConfig
import java.io.File

object HookUtils {

    fun isAllowPackage(list: Set<String>, packName: String): Boolean {
        return list.contains(packName) || packName == BuildConfig.APPLICATION_ID
    }

    fun createFcmfixChannel(notificationManager: NotificationManager) {
        if (notificationManager.getNotificationChannel("fcmfix") == null) {
            val channel = NotificationChannel(
                "fcmfix", "FCMFix", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun getPackageUid(context: Context, packageName: String): Int {
        return try {
            val pm: PackageManager = context.packageManager
            return pm.getPackageUid(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            -1
        }
    }

    fun getAppIcon(context: Context, packageName: String): Bitmap? {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            return if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                bitmap
            }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
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

    fun isFCMAction(action: String?): Boolean {
        return action != null && (action.endsWith(".android.c2dm.intent.RECEIVE") ||
                "com.google.firebase.MESSAGING_EVENT" == action ||
                "com.google.firebase.INSTANCE_ID_EVENT" == action)
    }

    fun isFCMIntent(intent: Intent): Boolean {
        val action = intent.action
        return isFCMAction(action)
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