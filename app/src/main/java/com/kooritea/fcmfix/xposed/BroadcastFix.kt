package com.kooritea.fcmfix.xposed

import android.content.Intent
import android.os.Build
import com.kooritea.fcmfix.util.IceboxUtils.Companion.activeApp
import com.kooritea.fcmfix.util.IceboxUtils.Companion.isAppEnabled
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method
import kotlin.math.abs

class BroadcastFix(loadPackageParam: LoadPackageParam) : XposedModule(loadPackageParam) {
    override fun onCanReadConfig() {
        try {
            this.startHook()
        } catch (e: Exception) {
            printLog("hook error com.android.server.am.ActivityManagerService.broadcastIntentLocked:" + e.message)
        }
    }

    protected fun startHook() {
        val clazz = XposedHelpers.findClass(
            "com.android.server.am.ActivityManagerService",
            loadPackageParam.classLoader
        )
        val declareMethods = clazz.declaredMethods
        var targetMethod: Method? = null
        for (method in declareMethods) {
            if ("broadcastIntentLocked" == method.name) {
                if (targetMethod == null || targetMethod.parameterTypes.size < method.parameterTypes.size) {
                    targetMethod = method
                }
            }
        }
        if (targetMethod != null) {
            var intent_args_index = 0
            var appOp_args_index = 0
            val parameters = targetMethod.parameters
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                intent_args_index = 2
                appOp_args_index = 9
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                intent_args_index = 3
                appOp_args_index = 10
            } else if (Build.VERSION.SDK_INT == 31) {
                intent_args_index = 3
                if (parameters[11].type == Int::class.javaPrimitiveType) {
                    appOp_args_index = 11
                }
                if (parameters[12].type == Int::class.javaPrimitiveType) {
                    appOp_args_index = 12
                }
            } else if (Build.VERSION.SDK_INT == 32) {
                intent_args_index = 3
                if (parameters[11].type == Int::class.javaPrimitiveType) {
                    appOp_args_index = 11
                }
                if (parameters[12].type == Int::class.javaPrimitiveType) {
                    appOp_args_index = 12
                }
            } else if (Build.VERSION.SDK_INT == 33) {
                intent_args_index = 3
                appOp_args_index = 12
            } else if (Build.VERSION.SDK_INT == 34) {
                intent_args_index = 3
                if (parameters[12].type == Int::class.javaPrimitiveType) {
                    appOp_args_index = 12
                }
                if (parameters[13].type == Int::class.javaPrimitiveType) {
                    appOp_args_index = 13
                }
            } else if (Build.VERSION.SDK_INT == 35) {
                intent_args_index = 3
                appOp_args_index = 13
            }
            if (intent_args_index == 0 || appOp_args_index == 0) {
                intent_args_index = 0
                appOp_args_index = 0
                // 根据参数名称查找，部分经过混淆的系统无效
                for (i in parameters.indices) {
                    if ("appOp" == parameters[i].name && parameters[i].type == Int::class.javaPrimitiveType) {
                        appOp_args_index = i
                    }
                    if ("intent" == parameters[i].name && parameters[i].type == Intent::class.java) {
                        intent_args_index = i
                    }
                }
            }
            if (intent_args_index == 0 || appOp_args_index == 0) {
                intent_args_index = 0
                appOp_args_index = 0
                // 尝试用最后一个版本
                if (parameters[3].type == Intent::class.java && parameters[12].type == Int::class.javaPrimitiveType) {
                    intent_args_index = 3
                    appOp_args_index = 12
                    printLog("未适配的安卓版本，正在使用最后一个适配的安卓版本的配置，可能会出现工作异常。")
                }
            }
            if (intent_args_index == 0 || appOp_args_index == 0) {
                intent_args_index = 0
                appOp_args_index = 0
                for (i in parameters.indices) {
                    // 从最后一个适配的版本的位置左右查找appOp的位置
                    if (abs((12 - i).toDouble()) < 2 && parameters[i].type == Int::class.javaPrimitiveType) {
                        appOp_args_index = i
                    }
                    // 唯一一个Intent参数的位置
                    if (parameters[i].type == Intent::class.java) {
                        if (intent_args_index != 0) {
                            printLog("查找到多个Intent，停止查找hook位置。")
                            intent_args_index = 0
                            break
                        }
                        intent_args_index = i
                    }
                }
                if (intent_args_index != 0 && appOp_args_index != 0) {
                    printLog("当前hook位置通过模糊查找得出，fcmfix可能不会正常工作。")
                }
            }
            printLog("Android API: " + Build.VERSION.SDK_INT)
            printLog("appOp_args_index: $appOp_args_index")
            printLog("intent_args_index: $intent_args_index")
            if (intent_args_index == 0 || appOp_args_index == 0) {
                printLog("broadcastIntentLocked hook 位置查找失败，fcmfix将不会工作。")
                return
            }
            val finalIntent_args_index = intent_args_index
            val finalAppOp_args_index = appOp_args_index

            XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                    val intent = methodHookParam.args[finalIntent_args_index] as Intent
                    if (intent.getPackage() != null && intent.flags != Intent.FLAG_INCLUDE_STOPPED_PACKAGES && isFCMIntent(
                            intent
                        )
                    ) {
                        val target = if (intent.component != null) {
                            intent.component!!.packageName
                        } else {
                            intent.getPackage()
                        }
                        if (targetIsAllow(target!!)) {
                            val i = methodHookParam.args[finalAppOp_args_index] as Int
                            if (i == -1) {
                                methodHookParam.args[finalAppOp_args_index] = 11
                            }
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            if (isIncludeIceBoxDisableApp && !isAppEnabled(
                                    context!!,
                                    target
                                )
                            ) {
                                printLog("Waiting for IceBox to activate the app: $target", true)
                                methodHookParam.result = false
                                Thread {
                                    activeApp(
                                        context!!,
                                        target
                                    )
                                    for (i1 in 0..299) {
                                        if (!isAppEnabled(
                                                context!!,
                                                target
                                            )
                                        ) {
                                            try {
                                                Thread.sleep(100)
                                            } catch (e: Exception) {
                                                printLog(
                                                    "Send Forced Start Broadcast Error: " + target + " " + e.message,
                                                    true
                                                )
                                            }
                                        } else {
                                            break
                                        }
                                    }
                                    try {
                                        if (isAppEnabled(
                                                context!!,
                                                target
                                            )
                                        ) {
                                            printLog(
                                                "Send Forced Start Broadcast: $target",
                                                true
                                            )
                                        } else {
                                            printLog(
                                                "Waiting for IceBox to activate the app timed out: $target",
                                                true
                                            )
                                        }
                                        XposedBridge.invokeOriginalMethod(
                                            methodHookParam.method,
                                            methodHookParam.thisObject,
                                            methodHookParam.args
                                        )
                                    } catch (e: Exception) {
                                        printLog(
                                            "Send Forced Start Broadcast Error: " + target + " " + e.message,
                                            true
                                        )
                                    }
                                }.start()
                            } else {
                                printLog("Send Forced Start Broadcast: $target", true)
                            }
                        }
                    }
                }
            })
        } else {
            printLog("No Such Method com.android.server.am.ActivityManagerService.broadcastIntentLocked")
        }
    }
}
