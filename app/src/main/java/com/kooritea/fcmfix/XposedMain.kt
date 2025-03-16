package com.kooritea.fcmfix

import android.annotation.SuppressLint
import com.kooritea.fcmfix.xposed.AutoStartFix
import com.kooritea.fcmfix.xposed.BroadcastFix
import com.kooritea.fcmfix.xposed.KeepNotification
import com.kooritea.fcmfix.xposed.MiuiLocalNotificationFix
import com.kooritea.fcmfix.xposed.PowerkeeperFix
import com.kooritea.fcmfix.xposed.ReconnectManagerFix
import com.kooritea.fcmfix.xposed.XposedModule
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.File

class XposedMain : IXposedHookLoadPackage {
    @SuppressLint("SdCardPath")
    override fun handleLoadPackage(loadPackageParam: LoadPackageParam) {
        if (fileIsExists("/sdcard/disable_fcmfix")) {
            XposedBridge.log("[fcmfix] /sdcard/disable_fcmfix is exists, exit")
            return
        }
        if (loadPackageParam.packageName == "android") {
            XposedModule.staticLoadPackageParam = loadPackageParam
            XposedBridge.log("[fcmfix] start hook com.android.server.am.ActivityManagerService")
            BroadcastFix(loadPackageParam)

            XposedBridge.log("[fcmfix] start hook com.android.server.notification.NotificationManagerServiceInjector")
            MiuiLocalNotificationFix(loadPackageParam)

            XposedBridge.log("[fcmfix] com.android.server.am.BroadcastQueueInjector.checkApplicationAutoStart")
            AutoStartFix(loadPackageParam)

            XposedBridge.log("[fcmfix] com.android.server.notification.NotificationManagerService")
            KeepNotification(loadPackageParam)
        }

        if (loadPackageParam.packageName == "com.google.android.gms" && loadPackageParam.isFirstApplication) {
            XposedModule.staticLoadPackageParam = loadPackageParam
            XposedBridge.log("[fcmfix] start hook com.google.android.gms")
            ReconnectManagerFix(loadPackageParam)
        }

        if (loadPackageParam.packageName == "com.miui.powerkeeper" && loadPackageParam.isFirstApplication) {
            XposedModule.staticLoadPackageParam = loadPackageParam
            XposedBridge.log("[fcmfix] start hook com.miui.powerkeeper")
            PowerkeeperFix(loadPackageParam)
        }
    }

    private fun fileIsExists(strFile: String): Boolean {
        try {
            val f = File(strFile)
            if (!f.exists()) {
                return false
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }
}
