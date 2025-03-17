package com.kooritea.fcmfix.hook.scopes.gms

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.os.WorkSource
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.ListClass
import com.highcapable.yukihookapi.hook.type.java.LongType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.kooritea.fcmfix.hook.HookUtils.sendGsmLogBroadcast
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
                    addFieldForType(StringClass)
                    addFieldForType(ContextClass)
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
                        paramTypes(StringClass, LongType)
                    }
                    addMethod {
                        paramTypes(StringClass, StringClass, LongType)
                    }
                    usingStrings("com.google.android.gms")
                }
            }.apply {
                alarmClazz = single().name
                if (alarmClazz.isBlank()) YLog.debug("Heartbeat find alarm clazz error")

                findField {
                    matcher {
                        type(StringClass)
                        addReadMethod {
                            paramTypes(StringClass, LongType)
                        }
                    }
                }.apply {
                    alarmTypeStr = single().fieldName
                    if (alarmTypeStr.isBlank()) YLog.debug("Heartbeat find alarm type error")
                }
            }

            dexkit.findClass {
                matcher {
                    addFieldForType(IntType)
                    addFieldForType(LongType)
                    addFieldForType(BooleanType)
                    addFieldForType(ListClass)
                    addMethod {
                        name("toString")
                        usingStrings("alarm")
                        usingNumbers(1000)
                    }
                    addMethod { paramTypes(LongType) }
                }
            }.apply {
                timerClazz = single().name
                if (timerClazz.isBlank()) YLog.debug("Heartbeat find timer clazz error")
            }

            timerClazz.toClass().apply {
                method { param(LongType) }.hook {
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
                        val alarmField = field { type = alarmClazz }.get(instance)
                            .any() ?: return@after
                        val context = alarmField.current().field { type = ContextClass }
                            .cast<Context>() ?: return@after
                        val alarmType = alarmField.current().field { name = alarmTypeStr }
                            .string()
                        if (alarmType == "GCM_HB_ALARM" || alarmType == "GCM_CONN_ALARM") {
                            val longFields = field { type = LongType }.giveAll()
                            val maxLongField = longFields.maxByOrNull {
                                field { name = it.name }.get(instance).long()
                            } ?: return@after
                            val timer = Timer("ReconnectManagerFix")
                            timer.schedule(object : TimerTask() {
                                override fun run() {
                                    val nextTime = field { name = maxLongField.name }
                                        .get(instance).long()
                                    if (nextTime != 0L && nextTime - SystemClock.elapsedRealtime() > 0) {
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