package com.luckyzyx.fcmfix.hook.scopes.gms

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.os.WorkSource
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast
import org.luckypray.dexkit.DexKitBridge
import java.util.Timer
import java.util.TimerTask

object Heartbeat : YukiBaseHooker() {
    override fun onHook() {
        var timerClazz: String
        var alarmClazz: String
        var alarmTypeStr: String

        System.loadLibrary("dexkit")
        DexKitBridge.create(appInfo.sourceDir).use { dexkit ->
            dexkit.findClass {
                matcher {
                    addFieldForType(String::class.java)
                    addFieldForType(Context::class.java)
                    addFieldForType(WorkSource::class.java)
                    addFieldForType(PowerManager.WakeLock::class.java)
                    addMethod {
                        paramTypes(
                            PowerManager.WakeLock::class.java,
                            WorkSource::class.java
                        )
                    }
                    addMethod {
                        paramTypes(WorkSource::class.java)
                    }
                    addMethod {
                        paramTypes(String::class.java, Long::class.java)
                    }
                    addMethod {
                        paramTypes(String::class.java, String::class.java, Long::class.java)
                    }
                    usingStrings("com.google.android.gms")
                }
            }.apply {
                alarmClazz = single().name
                if (alarmClazz.isBlank()) YLog.debug("Heartbeat find alarm clazz error")

                findField {
                    matcher {
                        type(String::class.java)
                        addReadMethod {
                            paramTypes(String::class.java, Long::class.java)
                        }
                    }
                }.apply {
                    alarmTypeStr = single().fieldName
                    if (alarmTypeStr.isBlank()) YLog.debug("Heartbeat find alarm type error")
                }
            }

            dexkit.findClass {
                matcher {
                    addFieldForType(Int::class.java)
                    addFieldForType(Long::class.java)
                    addFieldForType(Boolean::class.java)
                    addFieldForType(List::class.java)
                    addMethod {
                        name("toString")
                        usingStrings("alarm")
                        usingNumbers(1000)
                    }
                    addMethod { paramTypes(Long::class.java) }
                }
            }.apply {
                timerClazz = single().name
                if (timerClazz.isBlank()) YLog.debug("Heartbeat find timer clazz error")
            }

            timerClazz.toClass().resolve().apply {
                firstMethod { parameters(Long::class) }.hook {
//                    before {
//                        val time = args().first().long()
//                        val alarmField = field { type = alarmClazz }.get(instance)
//                            .any() ?: return@before
//                        val alarmType = alarmField.current().field { name = alarmTypeStr }
//                            .string()
//                        if (alarmType == "GCM_HB_ALARM") {
//                            val interval = 0
//                            if (interval > 1000) {
//                                args().first().set(interval)
//                            }
//                        }
//                        if (alarmType == "GCM_CONN_ALARM") {
//                            val interval = 0
//                            if (interval > 1000) {
//                                args().first().set(interval)
//                            }
//                        }
//                    }
                    after {
                        val time = args().first().long()
                        val alarmField = firstField { type = alarmClazz }.of(instance)
                            .get() ?: return@after
                        val context = alarmField.asResolver().firstField { type = Context::class }
                            .get<Context>() ?: return@after
                        val alarmType = alarmField.asResolver().firstField { name = alarmTypeStr }
                            .get<String>() ?: return@after
                        if (alarmType == "GCM_HB_ALARM" || alarmType == "GCM_CONN_ALARM") {
                            val longFields = field { type = Long::class }
                            val maxLongField = longFields.maxByOrNull {
                                it.of(instance).get<Long>() ?: 0L
                            } ?: return@after
                            val timer = Timer("ReconnectManagerFix")
                            timer.schedule(object : TimerTask() {
                                override fun run() {
                                    val nextTime = maxLongField.copy().of(instance).get<Long>() ?: 0L
                                    if (nextTime != 0L && nextTime - SystemClock.elapsedRealtime() < -60000) {
                                        context.sendBroadcast(Intent("com.google.android.intent.action.GCM_RECONNECT"))
                                        context.sendGsmLogBroadcast("Send timer broadcast GCM_RECONNECT")
                                    }
                                    timer.cancel()
                                }
                            }, time + 5000)
                        }
                    }
                }
            }
        }
    }
}