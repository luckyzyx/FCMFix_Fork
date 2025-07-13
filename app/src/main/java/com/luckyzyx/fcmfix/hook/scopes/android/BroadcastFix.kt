package com.luckyzyx.fcmfix.hook.scopes.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.WorkSource
import android.util.ArraySet
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.ArrayClass
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.fcmfix.hook.HookUtils.getPackageUid
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.hook.HookUtils.isFCMIntent
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast
import com.luckyzyx.fcmfix.hook.IceboxUtils.activeApp
import com.luckyzyx.fcmfix.hook.IceboxUtils.isAppEnabled

object BroadcastFix : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null
    var isBootComplete = false
    var oplusWakeLock: Any? = null

    override fun onHook() {
        var allowList = prefs("config").getStringSet("allowList", ArraySet())
        var includeIBDA = prefs("config").getBoolean("includeIceBoxDisableApp", false)

        @Suppress("UNCHECKED_CAST")
        callback = { key: String, value: Any ->
            when (key) {
                "allowList" -> allowList = value as Set<String>
                "includeIceBoxDisableApp" -> includeIBDA = value as Boolean
            }
        }

        //Source ActivityManagerService
        val clazz = "com.android.server.am.ActivityManagerService".toClassOrNull() ?: return
        val findMethods = clazz.resolve().method {
            name = "broadcastIntentLocked";returnType = Int::class
        }
        val maxParamCount = findMethods.maxByOrNull { it.self.parameterCount }?.self?.parameterCount
        val finalMethod = findMethods.find { it.self.parameterCount == maxParamCount } ?: return
        val paramTypes = finalMethod.self.parameterTypes
        val intentArgsIndex = paramTypes.indexOfFirst { it == Intent::class.java }
        val appOpArgsIndex = paramTypes.withIndex().find { (index, paramType) ->
            paramType == Int::class.java
                    && (index > 0 && paramTypes[index - 1] == ArrayClass(String::class.java))
                    && (index < paramTypes.size - 1 && paramTypes[index + 1] == Bundle::class.java)
        }?.index ?: paramTypes.indexOfFirst { it == Int::class.java }
        YLog.debug("Android API: " + Build.VERSION.SDK_INT)
        YLog.debug("intentArgsIndex: $intentArgsIndex")
        YLog.debug("appOpArgsIndex: $appOpArgsIndex")

        if (intentArgsIndex == -1 || appOpArgsIndex == -1) {
            YLog.debug("BroadcastFix broadcastIntentLocked not found")
            return
        }

        //Source OplusProxyWakeLock
        "com.android.server.power.OplusProxyWakeLock".toClass().resolve().apply {
            firstConstructor().hook {
                after {
                    if (oplusWakeLock == null) oplusWakeLock = instance
                }
            }
        }

        clazz.resolve().apply {
            finalMethod.hook {
                before {
                    if (!isBootComplete) return@before
                    val context = firstField { name = "mContext" }.of(instance).get<Context>()
                        ?: return@before
                    val intent = args(intentArgsIndex).cast<Intent>() ?: return@before
                    val appop = args(appOpArgsIndex).cast<Int>() ?: return@before
                    val packName = (intent.component?.packageName ?: intent.getPackage())
                        ?: return@before
                    // 介入条件：Intent未包含唤醒停止的pkg 且 Intent是FCM
                    if ((intent.flags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES) == 0
                        && isFCMIntent(intent)
                    ) {
                        if (isAllowPackage(allowList, packName)) {
                            if (appop == -1) args(appOpArgsIndex).set(11)
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            if (includeIBDA && !isAppEnabled(context, packName)) {
                                context.sendGsmLogBroadcast("[$packName] Wait IceBox Active")
                                Thread {
                                    activeApp(context, packName)
                                    for (i in 0 until 300) {
                                        if (!isAppEnabled(context, packName)) {
                                            try {
                                                Thread.sleep(100)
                                            } catch (e: Throwable) {
                                                context.sendGsmLogBroadcast(
                                                    "[$packName] Send Forced Start Broadcast Error",
                                                    e
                                                )
                                            }
                                        } else break
                                    }
                                    try {
                                        if (isAppEnabled(context, packName)) {
                                            context.sendGsmLogBroadcast(
                                                "[$packName] Send Forced Start Broadcast"
                                            )
                                        } else {
                                            context.sendGsmLogBroadcast(
                                                "[$packName] Wait IceBox active time out"
                                            )
                                        }
                                        result = invokeOriginal(*args)
                                    } catch (e: Exception) {
                                        context.sendGsmLogBroadcast(
                                            "[$packName] Send Forced Start Broadcast Error",
                                            e
                                        )
                                    }
                                }.start()
                            } else {
                                context.sendGsmLogBroadcast(
                                    "[$packName] Send Forced Start Broadcast"
                                )
                            }

                            if (oplusWakeLock != null) {
                                val uid = getPackageUid(context, packName)
                                if (uid >= 0) {
                                    val ws = WorkSource()
                                    oplusWakeLock!!.asResolver().firstMethod {
                                        name = "unfreezeIfNeed"
                                        parameters(Int::class, WorkSource::class, String::class)
                                    }.invoke(uid, ws, "FCMFX")
                                    YLog.debug("unfreezeIfNeed: $packName")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}