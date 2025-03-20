package com.luckyzyx.fcmfix.hook.scopes.gms

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.luckyzyx.fcmfix.hook.HookUtils.createFcmfixChannel
import com.luckyzyx.fcmfix.hook.HookUtils.getAppIcon
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast
import com.luckyzyx.fcmfix.utils.safeOf

object BroadcastNotify : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null

    override fun onHook() {
        var allowList = prefs("config").getStringSet("allowList", ArraySet())
        var noRN = prefs("config").getBoolean("noResponseNotification", false)

        @Suppress("UNCHECKED_CAST")
        callback = { key: String, value: Any ->
            when (key) {
                "allowList" -> allowList = value as Set<String>
                "noResponseNotification" -> noRN = value as Boolean
            }
        }

        //Source DataMessageManager -> BroadcastDoneReceiver
        "com.google.android.gms.gcm.DataMessageManager\$BroadcastDoneReceiver".toClass().apply {
            method { param(ContextClass, IntentClass) }.hook {
                before {
                    val ins = instance<BroadcastReceiver>()
                    val context = args().first().cast<Context>() ?: return@before
                    val pm = context.packageManager
                    val intent = args().last().cast<Intent>() ?: return@before
                    val resultCode = ins.resultCode
                    val packName = intent.getPackage() ?: return@before
                    if (resultCode != -1 && noRN && isAllowPackage(allowList, packName)) {
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
                                context.sendGsmLogBroadcast("$packName Send FCM Notification")
                            } else {
                                context.sendGsmLogBroadcast("$packName Target App Launch Intent Error")
                            }
                        } catch (e: Exception) {
                            context.sendGsmLogBroadcast("GSM Send Notification Error", e)
                        }
                    }
                }
            }
        }
    }
}