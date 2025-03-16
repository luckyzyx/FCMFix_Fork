package com.kooritea.fcmfix.xposed

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.edit
import com.kooritea.fcmfix.util.XposedUtils.getObjectFieldByPath
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Timer
import java.util.TimerTask

class ReconnectManagerFix(loadPackageParam: LoadPackageParam) : XposedModule(loadPackageParam) {
    private var GcmChimeraService: Class<*>? = null
    private var GcmChimeraServiceLogMethodName: String? = null
    private var startHookFlag = false


    @Throws(Exception::class)
    override fun onCanReadConfig() {
        if (startHookFlag) {
            this.checkVersion()
            onUpdateConfig()
        } else {
            startHookFlag = true
        }
    }

    private fun startHookGcmServiceStart() {
        this.GcmChimeraService = XposedHelpers.findClass(
            "com.google.android.gms.gcm.GcmChimeraService",
            loadPackageParam.classLoader
        )
        try {
            for (method in GcmChimeraService!!.getMethods()) {
                if (method.parameterTypes.size == 2) {
                    if (method.parameterTypes[0] == String::class.java && method.parameterTypes[1] == Array<Any>::class.java) {
                        this.GcmChimeraServiceLogMethodName = method.name
                        break
                    }
                }
            }

            XposedHelpers.findAndHookMethod(
                GcmChimeraService,
                "onCreate",
                object : XC_MethodHook() {
                    @SuppressLint("UnspecifiedRegisterReceiverFlag")
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val intentFilter = IntentFilter()
                        intentFilter.addAction("com.kooritea.fcmfix.log")
                        if (Build.VERSION.SDK_INT >= 34) {
                            context!!.registerReceiver(
                                logBroadcastReceive,
                                intentFilter,
                                Context.RECEIVER_EXPORTED
                            )
                        } else {
                            context!!.registerReceiver(logBroadcastReceive, intentFilter)
                        }
                        if (startHookFlag) {
                            checkVersion()
                        } else {
                            startHookFlag = true
                        }
                    }
                })
            XposedHelpers.findAndHookMethod(
                this.GcmChimeraService,
                "onDestroy",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        context!!.unregisterReceiver(logBroadcastReceive)
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("GcmChimeraService hook 失败")
        }
        try {
            val clazz = XposedHelpers.findClass(
                "com.google.android.gms.gcm.DataMessageManager\$BroadcastDoneReceiver",
                loadPackageParam.classLoader
            )
            val declareMethods = clazz.declaredMethods
            var targetMethod: Method? = null
            for (method in declareMethods) {
                val parameters = method.parameters
                if (parameters.size == 2 && parameters[0].type == Context::class.java && parameters[1].type == Intent::class.java) {
                    targetMethod = method
                    break
                }
            }
            if (targetMethod != null) {
                XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                        val resultCode = XposedHelpers.callMethod(
                            methodHookParam.thisObject,
                            "getResultCode"
                        ) as Int
                        val intent = methodHookParam.args[1] as Intent
                        val packageName = intent.getPackage()
                        if (resultCode != -1 && targetIsAllow(packageName!!)) {
                            try {
                                val notifyIntent =
                                    context!!.packageManager.getLaunchIntentForPackage(
                                        packageName
                                    )
                                if (notifyIntent != null) {
                                    notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    val pendingIntent = PendingIntent.getActivity(
                                        context,
                                        0,
                                        notifyIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                    sendNotification("FCM Message $packageName", "", pendingIntent)
                                } else {
                                    printLog("无法获取目标应用active: $packageName", false)
                                }
                            } catch (e: Exception) {
                                printLog(e.message!!, false)
                            }
                        }
                    }
                })
            } else {
                printLog("No Such Method com.google.android.gms.gcm.DataMessageManager\$BroadcastDoneReceiver.handler")
            }
        } catch (e: Exception) {
            XposedBridge.log("DataMessageManager\$BroadcastDoneReceiver hook 失败")
        }
    }

    @Throws(Exception::class)
    private fun checkVersion() {
        val sharedPreferences =
            context!!.getSharedPreferences("fcmfix_config", Context.MODE_PRIVATE)
        val versionName = context!!.packageManager.getPackageInfo(
            context!!.packageName, 0
        ).versionName
        val versionCode = context!!.packageManager.getPackageInfo(
            context!!.packageName, 0
        ).longVersionCode
        if (versionCode < 213916046) {
            printLog("当前为旧版GMS，请使用0.4.1版本FCMFIX，禁用重连修复功能")
            return
        }
        if (!sharedPreferences.getBoolean(
                "isInit",
                false
            ) || sharedPreferences.getString("config_version", "") != configVersion
        ) {
            printLog("fcmfix_config init", true)
            sharedPreferences.edit {
                putBoolean("isInit", true)
                putBoolean("enable", false)
                putLong("heartbeatInterval", 0L)
                putLong("reconnInterval", 0L)
                putString("gms_version", versionName)
                putLong("gms_version_code", versionCode)
                putString("config_version", configVersion)
                putString("timer_class", "")
                putString("timer_settimeout_method", "")
                putString("timer_alarm_type_property", "")
                apply()
            }
            printLog("正在更新hook位置", true)
            findAndUpdateHookTarget(sharedPreferences)
            return
        }
        if (sharedPreferences.getString("gms_version", "") != versionName) {
            printLog(
                "gms已更新: " + sharedPreferences.getString(
                    "gms_version",
                    ""
                ) + "(" + sharedPreferences.getLong(
                    "gms_version_code",
                    0
                ) + ")" + "->" + versionName + "(" + versionCode + ")", true
            )
            sharedPreferences.edit {
                putString("gms_version", versionName)
                putLong("gms_version_code", versionCode)
                putBoolean("enable", false)
                apply()
            }
            printLog("正在更新hook位置", true)
            findAndUpdateHookTarget(sharedPreferences)
            return
        }
        if (!sharedPreferences.getBoolean("enable", false)) {
            printLog("当前配置文件enable标识为false，FCMFIX退出", true)
            return
        }
        startHook()
    }

    protected fun startHook() {
        val sharedPreferences =
            context!!.getSharedPreferences("fcmfix_config", Context.MODE_PRIVATE)
        printLog("timer_class: " + sharedPreferences.getString("timer_class", ""), true)
        printLog(
            "timer_alarm_type_property: " + sharedPreferences.getString(
                "timer_alarm_type_property",
                ""
            ), true
        )
        printLog(
            "timer_settimeout_method: " + sharedPreferences.getString(
                "timer_settimeout_method",
                ""
            ), true
        )
        val timerClazz = XposedHelpers.findClass(
            sharedPreferences.getString("timer_class", ""),
            loadPackageParam.classLoader
        )
        XposedHelpers.findAndHookMethod(timerClazz, "toString", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val alarmType = getObjectFieldByPath(
                    param.thisObject,
                    sharedPreferences.getString("timer_alarm_type_property", "")!!
                ) as String
                if ("GCM_HB_ALARM" == alarmType || "GCM_CONN_ALARM" == alarmType) {
                    val hinterval = sharedPreferences.getLong("heartbeatInterval", 0L)
                    val cinterval = sharedPreferences.getLong("reconnInterval", 0L)
                    if ((hinterval > 1000) || (cinterval > 1000)) {
                        param.result = param.result.toString() + "[fcmfix locked]"
                    }
                }
            }
        })
        XposedHelpers.findAndHookMethod(
            timerClazz, sharedPreferences.getString("timer_settimeout_method", ""),
            Long::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 修改心跳间隔
                    val alarmType = getObjectFieldByPath(
                        param.thisObject,
                        sharedPreferences.getString("timer_alarm_type_property", "")!!
                    ) as String
                    if ("GCM_HB_ALARM" == alarmType) {
                        val interval = sharedPreferences.getLong("heartbeatInterval", 0L)
                        if (interval > 1000) {
                            param.args[0] = interval
                        }
                    }
                    if ("GCM_CONN_ALARM" == alarmType) {
                        val interval = sharedPreferences.getLong("reconnInterval", 0L)
                        if (interval > 1000) {
                            param.args[0] = interval
                        }
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    // 防止计时器出现负数计时,分别是心跳计时和重连计时
                    val alarmType = getObjectFieldByPath(
                        param.thisObject,
                        sharedPreferences.getString("timer_alarm_type_property", "")!!
                    ) as String
                    if ("GCM_HB_ALARM" == alarmType || "GCM_CONN_ALARM" == alarmType) {
                        var maxField: Field? = null
                        var maxFieldValue = 0L
                        for (field in timerClazz.declaredFields) {
                            if (field.type == Long::class.javaPrimitiveType) {
                                val fieldValue = XposedHelpers.getObjectField(
                                    param.thisObject,
                                    field.name
                                ) as Long
                                if (maxField == null || fieldValue > maxFieldValue) {
                                    maxField = field
                                    maxFieldValue = fieldValue
                                }
                            }
                        }
                        val timer = Timer("ReconnectManagerFix")
                        val finalMaxField = maxField
                        timer.schedule(object : TimerTask() {
                            override fun run() {
                                val nextConnectionTime = XposedHelpers.getLongField(
                                    param.thisObject,
                                    finalMaxField!!.name
                                )
                                if (nextConnectionTime != 0L && nextConnectionTime - SystemClock.elapsedRealtime() < 0) {
                                    context!!.sendBroadcast(Intent("com.google.android.intent.action.GCM_RECONNECT"))
                                    printLog("Send broadcast GCM_RECONNECT", true)
                                }
                                timer.cancel()
                            }
                        }, param.args[0] as Long + 5000)
                    }
                }
            })
    }

    private val logBroadcastReceive: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if ("com.kooritea.fcmfix.log" == action) {
                try {
                    XposedHelpers.callStaticMethod(
                        GcmChimeraService, GcmChimeraServiceLogMethodName, arrayOf<Class<*>>(
                            String::class.java,
                            Array<Any>::class.java
                        ), "[fcmfix] " + intent.getStringExtra("text"), null
                    )
                } catch (e: Throwable) {
                    XposedBridge.log("输出日志到fcm失败： " + "[fcmfix] " + intent.getStringExtra("text"))
                }
            }
        }
    }

    init {
        this.addButton()
        this.startHookGcmServiceStart()
    }

    private fun findAndUpdateHookTarget(sharedPreferences: SharedPreferences) {
        sharedPreferences.edit {
            try {
                val heartbeatChimeraAlarm = XposedHelpers.findClass(
                    "com.google.android.gms.gcm.connection.HeartbeatChimeraAlarm",
                    loadPackageParam.classLoader
                )
                var timerClass = heartbeatChimeraAlarm.constructors[0].parameterTypes[3]
                if (timerClass!!.declaredMethods.isEmpty()) {
                    timerClass = timerClass.superclass
                }
                putString("timer_class", timerClass!!.name)
                for (method in timerClass.declaredMethods) {
                    if (method.parameterTypes.size == 1 && method.parameterTypes[0] == Long::class.javaPrimitiveType && Modifier.isFinal(
                            method.modifiers
                        ) && Modifier.isPublic(method.modifiers)
                    ) {
                        putString("timer_settimeout_method", method.name)
                        break
                    }
                }
                for (timerClassField in timerClass.declaredFields) {
                    if (Modifier.isFinal(timerClassField.modifiers) && Modifier.isPublic(
                            timerClassField.modifiers
                        )
                    ) {
                        val alarmClass = timerClassField.type
                        val isFinish = arrayOf(false)
                        var alarmClassConstructor: Constructor<*>? = null
                        for (constructor in alarmClass.constructors) {
                            val pts = constructor.parameterTypes
                            if (alarmClassConstructor == null || pts.size > alarmClassConstructor.parameterCount) {
                                if (pts[0] == Context::class.java && pts[1] == Int::class.javaPrimitiveType && pts[2] == String::class.java) alarmClassConstructor =
                                    constructor
                            }
                        }
                        if (alarmClassConstructor == null) throw Throwable("未找到构造函数")
                        XposedBridge.hookMethod(alarmClassConstructor, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (!isFinish[0]) {
                                    for (field in alarmClass.declaredFields) {
                                        if (field.type == String::class.java && Modifier.isFinal(
                                                field.modifiers
                                            ) && Modifier.isPrivate(
                                                field.modifiers
                                            )
                                        ) {
                                            if (param.args[2] != null && XposedHelpers.getObjectField(
                                                    param.thisObject,
                                                    field.name
                                                ) === param.args[2]
                                            ) {
                                                sharedPreferences.edit() {
                                                    putString(
                                                        "timer_alarm_type_property",
                                                        timerClassField.name + "." + field.name
                                                    )
                                                    putBoolean("enable", true)
                                                    apply()
                                                }
                                                isFinish[0] = true
                                                printLog("更新hook位置成功", true)
                                                sendNotification("自动更新配置文件成功")
                                                startHook()
                                                return
                                            }
                                        }
                                    }
                                    printLog("自动寻找hook点失败: 未找到目标方法", true)
                                }
                            }
                        })
                        break
                    }
                }
            } catch (e: Throwable) {
                putBoolean("enable", false)
                printLog("自动寻找hook点失败" + e.message, true)
                sendNotification(
                    "自动更新配置文件失败",
                    "未能找到hook点，已禁用重连修复和固定心跳功能。"
                )
                e.printStackTrace()
            }
            apply()
        }
    }

    private fun addButton() {
        XposedHelpers.findAndHookMethod(
            "com.google.android.gms.gcm.GcmChimeraDiagnostics",
            loadPackageParam.classLoader,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                @SuppressLint("SetTextI18n")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val viewGroup = (XposedHelpers.callMethod(
                        param.thisObject,
                        "getWindow"
                    ) as Window).decorView.findViewById<ViewGroup>(
                        android.R.id.content
                    )
                    val linearLayout = viewGroup.getChildAt(0) as LinearLayout
                    val linearLayout2 = linearLayout.getChildAt(0) as LinearLayout

                    val reConnectButton = Button(param.thisObject as ContextWrapper)
                    reConnectButton.text = "RECONNECT"
                    reConnectButton.setOnClickListener { view: View? ->
                        context!!.sendBroadcast(Intent("com.google.android.intent.action.GCM_RECONNECT"))
                        printLog("Send broadcast GCM_RECONNECT", true)
                    }
                    linearLayout2.addView(reConnectButton)

                    val openFcmFixButton = Button(param.thisObject as ContextWrapper)
                    openFcmFixButton.text = "打开FCMFIX"
                    openFcmFixButton.setOnClickListener { view: View? ->
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.setPackage("com.kooritea.fcmfix")
                        intent.setComponent(
                            ComponentName(
                                "com.kooritea.fcmfix",
                                "com.kooritea.fcmfix.MainActivity"
                            )
                        )
                        context!!.startActivity(intent)
                    }
                    linearLayout2.addView(openFcmFixButton)
                }
            })
    }

    companion object {
        const val configVersion: String = "v3"
    }
}
