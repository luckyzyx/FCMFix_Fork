package com.kooritea.fcmfix.xposed

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

class MiuiLocalNotificationFix(loadPackageParam: LoadPackageParam) :
    XposedModule(loadPackageParam) {
    init {
        this.startHook()
    }

    private fun startHook() {
        try {
            val clazz = try {
                XposedHelpers.findClass(
                    "com.android.server.notification.NotificationManagerServiceInjector",
                    loadPackageParam.classLoader
                )
            } catch (e: ClassNotFoundError) {
                XposedHelpers.findClass(
                    "com.android.server.notification.NotificationManagerServiceImpl",
                    loadPackageParam.classLoader
                )
            }
            val declareMethods = clazz.declaredMethods
            var targetMethod: Method? = null
            for (method in declareMethods) {
                if ("isAllowLocalNotification" == method.name || "isDeniedLocalNotification" == method.name) {
                    targetMethod = method
                    break
                }
            }
            if (targetMethod != null) {
                val finalTargetMethod: Method = targetMethod
                XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                        if (targetIsAllow((methodHookParam.args[3] as String))) {
                            methodHookParam.result =
                                "isAllowLocalNotification" == finalTargetMethod.name
                        }
                    }
                })
            } else {
                printLog("Not found [isAllowLocalNotification/isDeniedLocalNotification] in com.android.server.notification.[NotificationManagerServiceInjector/NotificationManagerServiceImpl]")
            }
        } catch (e: ClassNotFoundError) {
            printLog("Not found [isAllowLocalNotification/isDeniedLocalNotification] in com.android.server.notification.[NotificationManagerServiceInjector/NotificationManagerServiceImpl]")
        }
    }
}
