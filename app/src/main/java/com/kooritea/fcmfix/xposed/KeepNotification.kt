package com.kooritea.fcmfix.xposed

import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Method

class KeepNotification(loadPackageParam: LoadPackageParam) : XposedModule(loadPackageParam) {
    override fun onCanReadConfig() {
        try {
            this.startHook()
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.android.server.notification.NotificationManagerService.cancelAllNotificationsInt")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.android.server.notification.NotificationManagerService.cancelAllNotificationsInt")
        }
    }

    @Throws(NoSuchMethodError::class, ClassNotFoundError::class)
    private fun startHook() {
        val clazz = XposedHelpers.findClass(
            "com.android.server.notification.NotificationManagerService",
            loadPackageParam.classLoader
        )
        val declareMethods = clazz.declaredMethods
        var targetMethod: Method? = null
        for (method in declareMethods) {
            if ("cancelAllNotificationsInt" == method.name) {
                if (targetMethod == null || targetMethod.parameterTypes.size < method.parameterTypes.size) {
                    targetMethod = method
                }
            }
        }
        if (targetMethod != null) {
            var pkg_args_index = 0
            var reason_args_index = 0
            if (Build.VERSION.SDK_INT == 30) {
                pkg_args_index = 2
                reason_args_index = 8
            }
            if (Build.VERSION.SDK_INT == 31) {
                pkg_args_index = 2
                reason_args_index = 8
            }
            if (Build.VERSION.SDK_INT == 32) {
                pkg_args_index = 2
                reason_args_index = 8
            }
            if (Build.VERSION.SDK_INT == 33) {
                pkg_args_index = 2
                reason_args_index = 8
            }
            if (Build.VERSION.SDK_INT == 34) {
                if (targetMethod.parameterTypes.size == 10) {
                    pkg_args_index = 2
                    reason_args_index = 8
                } else if (targetMethod.parameterTypes.size == 8) {
                    pkg_args_index = 2
                    reason_args_index = 7
                }
            }
            if (Build.VERSION.SDK_INT > 34) {
                pkg_args_index = 2
                reason_args_index = 7
            }
            if (pkg_args_index == 0 || reason_args_index == 0) {
                throw NoSuchMethodError()
            }
            val finalPkg_args_index = pkg_args_index
            val finalReason_args_index = reason_args_index
            XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isDisableAutoCleanNotification && targetIsAllow((param.args[finalPkg_args_index] as String)) && param.args[finalReason_args_index] as Int == 5) {
                        param.result = null
                    }
                }
            })
        } else {
            throw NoSuchMethodError()
        }
    }
}
