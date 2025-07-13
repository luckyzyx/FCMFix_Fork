package com.luckyzyx.fcmfix.hook.scopes.gms

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.fcmfix.BuildConfig
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast

object AddButton : YukiBaseHooker() {
    @SuppressLint("SetTextI18n")
    override fun onHook() {
        //Source GcmChimeraDiagnostics
        "com.google.android.gms.gcm.GcmChimeraDiagnostics".toClass().resolve().apply {
            firstMethod { name = "onCreate" }.hook {
                after {
                    val button = firstField { type = Button::class }.of(instance).get<Button>()
                        ?: return@after
                    val linear = button.parent as LinearLayout
                    val context = button.context

                    val reConnectButton = Button(context)
                    reConnectButton.text = "RECONNECT"
                    reConnectButton.setOnClickListener {
                        context.sendBroadcast(Intent("com.google.android.intent.action.GCM_RECONNECT"))
                        context.sendGsmLogBroadcast("Send broadcast GCM_RECONNECT")
                    }
                    linear.addView(reConnectButton)

                    val openFcmFixButton = Button(context)
                    openFcmFixButton.text = "打开FCMFIX"
                    openFcmFixButton.setOnClickListener {
                        try {
                            val intent =
                                context.packageManager.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
                            context.startActivity(intent)
                        } catch (t: Throwable) {
                            context.sendGsmLogBroadcast("Open FCMFix Error", t)
                        }
                    }
                    linear.addView(openFcmFixButton)
                }
            }
        }
    }
}