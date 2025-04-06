package com.luckyzyx.fcmfix.hook

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.ArraySet
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.luckyzyx.fcmfix.BuildConfig
import com.luckyzyx.fcmfix.hook.HookUtils.fileIsExists
import com.luckyzyx.fcmfix.hook.scopes.android.AutoStartFix
import com.luckyzyx.fcmfix.hook.scopes.android.BroadcastFix
import com.luckyzyx.fcmfix.hook.scopes.android.BroadcastNotification
import com.luckyzyx.fcmfix.hook.scopes.android.KeepNotification
import com.luckyzyx.fcmfix.hook.scopes.gms.AddButton
import com.luckyzyx.fcmfix.hook.scopes.gms.Heartbeat
import com.luckyzyx.fcmfix.hook.scopes.gms.RegisterLogReceiver

@InjectYukiHookWithXposed
object MainHook : IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.configs {
            debugLog {
                tag = "FCMFix"
                isEnable = true
                elements(TAG, PRIORITY, PACKAGE_NAME, USER_ID)
            }
            isDebug = false
        }
    }

    var bootCompleteCallback: (() -> Unit)? = null

    var sysyemCallback: ((key: String?) -> Unit)? = null
    var gsmCallback: ((key: String?) -> Unit)? = null

    @SuppressLint("SdCardPath")
    override fun onHook() = encase {

        if (fileIsExists("/sdcard/disable_fcmfix")) {
            YLog.debug("/sdcard/disable_fcmfix is exists, exit")
            return@encase
        }

        loadSystem {
            loadHooker(RegisterBootComplete)
            loadHooker(RegisterConfigUpdate)

            loadHooker(BroadcastFix)
            loadHooker(AutoStartFix)
            loadHooker(KeepNotification)
            loadHooker(BroadcastNotification)

            bootCompleteCallback = {
                BroadcastFix.isBootComplete = true
                AutoStartFix.isBootComplete = true
                KeepNotification.isBootComplete = true
                BroadcastNotification.isBootComplete = true

                YLog.debug("System is BootComplete")
            }

            sysyemCallback = {
                when (it) {
                    "allowList" -> {
                        val allowList = prefs("config").getStringSet("allowList", ArraySet())
                        BroadcastFix.callback?.invoke("allowList", allowList)
                        AutoStartFix.callback?.invoke("allowList", allowList)
                        KeepNotification.callback?.invoke("allowList", allowList)
                        BroadcastNotification.callback?.invoke("allowList", allowList)
                    }

                    "disableAutoCleanNotification" -> {
                        val disableACN =
                            prefs("config").getBoolean("disableAutoCleanNotification", false)
                        KeepNotification.callback?.invoke(
                            "disableAutoCleanNotification",
                            disableACN
                        )
                    }

                    "includeIceBoxDisableApp" -> {
                        val includeIBDA =
                            prefs("config").getBoolean("includeIceBoxDisableApp", false)
                        BroadcastFix.callback?.invoke("includeIceBoxDisableApp", includeIBDA)
                    }

                    "noResponseNotification" -> {
                        val noRN = prefs("config").getBoolean("noResponseNotification", false)
                        BroadcastNotification.callback?.invoke("noResponseNotification", noRN)
                    }
                }
                YLog.debug("Update system config success")
            }
        }

        loadApp("com.google.android.gms") {
            loadHooker(RegisterConfigUpdate)

            loadHooker(AddButton)
            loadHooker(RegisterLogReceiver)
//            loadHooker(BroadcastNotify)
            loadHooker(Heartbeat)

            gsmCallback = {
//                when (it) {
//                    "allowList" -> {
//                        val allowList = prefs("config").getStringSet("allowList", ArraySet())
//                        BroadcastNotify.callback?.invoke("allowList", allowList)
//                    }
//
//                    "noResponseNotification" -> {
//                        val noRN = prefs("config").getBoolean("noResponseNotification", false)
//                        BroadcastNotify.callback?.invoke("noResponseNotification", noRN)
//                    }
//                }
                YLog.debug("Update gms config success")
            }
        }
    }

    object RegisterConfigUpdate : YukiBaseHooker() {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        override fun onHook() {
            onAppLifecycle {
                attachBaseContext { baseContext, _ ->
                    val intentFilter = IntentFilter("${BuildConfig.APPLICATION_ID}.update.config")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        baseContext.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    val action = intent?.action ?: return
                                    if (action != "${BuildConfig.APPLICATION_ID}.update.config") return
                                    val key = intent.getStringExtra("key")
                                    sysyemCallback?.invoke(key)
                                    gsmCallback?.invoke(key)
                                }
                            }, intentFilter, Context.RECEIVER_EXPORTED
                        )
                    } else {
                        baseContext.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    val action = intent?.action ?: return
                                    if (action != "${BuildConfig.APPLICATION_ID}.update.config") return
                                    val key = intent.getStringExtra("key")
                                    sysyemCallback?.invoke(key)
                                    gsmCallback?.invoke(key)
                                }
                            }, intentFilter
                        )
                    }
                }
            }
        }
    }

    object RegisterBootComplete : YukiBaseHooker() {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        override fun onHook() {
            onAppLifecycle {
                attachBaseContext { baseContext, _ ->
                    val intentFilter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        baseContext.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    val action = intent?.action ?: return
                                    if (action != Intent.ACTION_BOOT_COMPLETED) return
                                    bootCompleteCallback?.invoke()
                                }
                            }, intentFilter, Context.RECEIVER_EXPORTED
                        )
                    } else {
                        baseContext.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    val action = intent?.action ?: return
                                    if (action != Intent.ACTION_BOOT_COMPLETED) return
                                    bootCompleteCallback?.invoke()
                                }
                            }, intentFilter
                        )
                    }
                }
            }
        }
    }
}