package com.luckyzyx.fcmfix.hook.scopes.gms

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.ArrayClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.fcmfix.BuildConfig

object RegisterLogReceiver : YukiBaseHooker() {

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onHook() {
        //Source GcmChimeraService
        val gcmService = "com.google.android.gms.gcm.GcmChimeraService".toClass()
        val gcmSendLogMethod = gcmService.resolve().firstMethod {
            parameters(String::class, ArrayClass(Any::class))
        }

        val logBroadcastReceive: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "${BuildConfig.APPLICATION_ID}.log") {
                    val text = intent.getStringExtra("text")
                    try {
                        gcmSendLogMethod.invoke("[FCMFix] $text", null)
                    } catch (e: Throwable) {
                        YLog.debug("FCM LOG Print Error -> $text", e)
                    }
                }
            }
        }

        gcmService.resolve().apply {
            firstMethod { name = "onCreate" }.hook {
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
            firstMethod { name = "onDestroy" }.hook {
                before {
                    val context = instance<ContextWrapper>()
                    context.unregisterReceiver(logBroadcastReceive)
                }
            }
        }
    }
}