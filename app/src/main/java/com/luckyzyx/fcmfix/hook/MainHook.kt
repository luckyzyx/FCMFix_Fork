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
import com.luckyzyx.fcmfix.hook.scopes.android.KeepNotification
import com.luckyzyx.fcmfix.hook.scopes.gms.AddButton
import com.luckyzyx.fcmfix.hook.scopes.gms.BroadcastNotify
import com.luckyzyx.fcmfix.hook.scopes.gms.Heartbeat
import com.luckyzyx.fcmfix.hook.scopes.gms.RegisterLogReceiver
import de.robv.android.xposed.XSharedPreferences

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

    var sysyemCallback: (() -> Unit)? = null
    var gsmCallback: (() -> Unit)? = null

    @SuppressLint("SdCardPath")
    override fun onHook() = encase {

        val prefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "config")

        if (fileIsExists("/sdcard/disable_fcmfix")) {
            YLog.debug("/sdcard/disable_fcmfix is exists, exit")
            return@encase
        }

        loadSystem {
            loadHooker(RegisterConfigUpdate)

            loadHooker(BroadcastFix)
            loadHooker(AutoStartFix)
            loadHooker(KeepNotification)

            sysyemCallback = {
                prefs.reload()
                val allowList = prefs.getStringSet("allowList", ArraySet()) ?: ArraySet()
                val disableACN = prefs.getBoolean("disableAutoCleanNotification", false)
                val includeIBDA = prefs.getBoolean("includeIceBoxDisableApp", false)
                BroadcastFix.callback?.invoke("allowList", allowList)
                AutoStartFix.callback?.invoke("allowList", allowList)
                KeepNotification.callback?.invoke("allowList", allowList)
                KeepNotification.callback?.invoke("disableAutoCleanNotification", disableACN)
                BroadcastFix.callback?.invoke("includeIceBoxDisableApp", includeIBDA)
            }
        }

        loadApp("com.google.android.gms") {
            loadHooker(RegisterConfigUpdate)

            loadHooker(AddButton)
            loadHooker(RegisterLogReceiver)
            loadHooker(BroadcastNotify)
            loadHooker(Heartbeat)

            gsmCallback = {
                prefs.reload()
                val allowList = prefs.getStringSet("allowList", ArraySet()) ?: ArraySet()
                BroadcastNotify.callback?.invoke("allowList", allowList)
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
                                    if (action == "${BuildConfig.APPLICATION_ID}.update.config") {
                                        sysyemCallback?.invoke()
                                        gsmCallback?.invoke()
                                    }
                                }
                            }, intentFilter, Context.RECEIVER_EXPORTED
                        )
                    } else {
                        baseContext.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    val action = intent?.action ?: return
                                    if (action == "${BuildConfig.APPLICATION_ID}.update.config") {
                                        sysyemCallback?.invoke()
                                        gsmCallback?.invoke()
                                    }
                                }
                            }, intentFilter
                        )
                    }
                }
            }
        }
    }
}