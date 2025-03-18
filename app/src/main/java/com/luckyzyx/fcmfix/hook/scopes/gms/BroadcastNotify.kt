package com.luckyzyx.fcmfix.hook.scopes.gms

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast
import com.luckyzyx.fcmfix.hook.HookUtils.sendNotification

object BroadcastNotify : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onHook() {
        var allowList = prefs("config").getStringSet("allowList", ArraySet())

        @Suppress("UNCHECKED_CAST")
        callback = { key: String, value: Any ->
            when (key) {
                "allowList" -> allowList = value as Set<String>
            }
        }

        //Source DataMessageManager -> BroadcastDoneReceiver
        "com.google.android.gms.gcm.DataMessageManager\$BroadcastDoneReceiver".toClass().apply {
            method { param(ContextClass, IntentClass) }.hook {
                before {
                    val ins = instance<BroadcastReceiver>()
                    val context = args().first().cast<Context>() ?: return@before
                    val intent = args().last().cast<Intent>() ?: return@before
                    val resultCode = ins.resultCode
                    val packName = intent.getPackage() ?: return@before
                    if (resultCode != -1 && isAllowPackage(allowList, packName)) {
                        try {
                            val notifyIntent =
                                context.packageManager.getLaunchIntentForPackage(packName)
                            if (notifyIntent != null) {
                                notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                val pendingIntent = PendingIntent.getActivity(
                                    context, 0, notifyIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                val appName = try {
                                    context.packageManager.getApplicationInfo(packName, 0)
                                        .loadLabel(context.packageManager)
                                } catch (t: Throwable) {
                                    ""
                                }
                                sendNotification(context, appName, packName, pendingIntent)
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