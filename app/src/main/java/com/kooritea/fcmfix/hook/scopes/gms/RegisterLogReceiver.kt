package com.kooritea.fcmfix.hook.scopes.gms

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.java.AnyArrayClass
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.kooritea.fcmfix.BuildConfig

object RegisterLogReceiver : YukiBaseHooker() {

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onHook() {
        //Source GcmChimeraService
        val gcmService = "com.google.android.gms.gcm.GcmChimeraService".toClass()
        val gcmSendLogMethod = gcmService.method { param(StringClass, AnyArrayClass) }

        val logBroadcastReceive: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "${BuildConfig.APPLICATION_ID}.log") {
                    val text = intent.getStringExtra("text")
                    try {
                        gcmSendLogMethod.get().call("[FCMFix] $text", null)
                    } catch (e: Throwable) {
                        YLog.debug("FCM LOG Print Error -> $text", e)
                    }
                }
            }
        }

        gcmService.apply {
            method { name = "onCreate" }.hook {
                after {
                    val context = instance<ContextWrapper>()
                    val intentFilter = IntentFilter()
                    intentFilter.addAction("${BuildConfig.APPLICATION_ID}.log")
                    if (Build.VERSION.SDK_INT >= 34) {
                        context.registerReceiver(
                            logBroadcastReceive, intentFilter, Context.RECEIVER_EXPORTED
                        )
                    } else {
                        context.registerReceiver(logBroadcastReceive, intentFilter)
                    }
                }
            }
            method { name = "onDestroy" }.hook {
                before {
                    val context = instance<ContextWrapper>()
                    context.unregisterReceiver(logBroadcastReceive)
                }
            }
        }
    }
}