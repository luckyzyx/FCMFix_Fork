package com.luckyzyx.fcmfix.hook.scopes.android

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.luckyzyx.fcmfix.hook.HookUtils.createFcmfixChannel
import com.luckyzyx.fcmfix.hook.HookUtils.getAppIcon
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.utils.safeOf

object BroadcastNotification : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null

    override fun onHook() {
        var allowList = prefs("config").getStringSet("allowList", ArraySet())
        var noRN = prefs("config").getBoolean("noResponseNotification", false)

        @Suppress("UNCHECKED_CAST")
        KeepNotification.callback = { key: String, value: Any ->
            when (key) {
                "allowList" -> allowList = value as Set<String>
                "noResponseNotification" -> noRN = value as Boolean
            }
        }

        //Source BroadcastQueueModernImpl
        "com.android.server.am.BroadcastQueueModernImpl".toClass().apply {
            method { name = "scheduleResultTo" }.hook {
                before {
                    val ams = field {
                        type = "com.android.server.am.ActivityManagerService";superClass()
                    }.get(instance).any() ?: return@before
                    val context = ams.current().field { type = ContextClass }.cast<Context>()
                        ?: return@before
                    val pm = context.packageManager
                    val broadcastRecord = args().first().any() ?: return@before
                    val intent = broadcastRecord.current().field { name = "intent" }
                        .cast<Intent>() ?: return@before
                    val resultCode = broadcastRecord.current().field { name = "resultCode" }.int()
                    val packName = intent.`package` ?: return@before
                    if (noRN && resultCode != -1 && isAllowPackage(allowList, packName)) {
                        try {
                            val notifyIntent = pm.getLaunchIntentForPackage(packName)
                            if (notifyIntent != null) {
                                notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val pendingIntent = PendingIntent.getActivity(
                                    context, 0, notifyIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                val appName = safeOf("") {
                                    pm.getApplicationInfo(packName, 0)
                                        .loadLabel(context.packageManager)
                                }
                                val notificationManager =
                                    context.getSystemService(NotificationManager::class.java)
                                createFcmfixChannel(notificationManager)
                                val notification = Notification.Builder(context, "fcmfix").apply {
                                    setSmallIcon(android.R.drawable.ic_dialog_info)
                                    setContentTitle("[FCMFix] $appName")
                                    setContentText("[FCMFix] $packName")
                                    getAppIcon(context, packName)?.let { setLargeIcon(it) }
                                    setContentIntent(pendingIntent)
                                    setAutoCancel(true)
                                }
                                notificationManager.notify(
                                    System.currentTimeMillis().toInt(), notification.build()
                                )
                                YLog.debug("$packName Send FCM Notification")
                            } else {
                                YLog.debug("$packName Target App Launch Intent Error")
                            }
                        } catch (e: Exception) {
                            YLog.error(e.message.toString(), throwable)
                        }
                    }
                }
            }
        }
    }
}