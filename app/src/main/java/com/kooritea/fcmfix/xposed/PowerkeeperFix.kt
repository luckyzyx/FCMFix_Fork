package com.kooritea.fcmfix.xposed

import android.content.Context
import com.kooritea.fcmfix.util.XposedUtils.findAndHookMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.lang.reflect.Field
import java.util.ArrayList

class PowerkeeperFix(loadPackageParam: LoadPackageParam) : XposedModule(loadPackageParam) {
    init {
        this.startHook()
    }

    private fun startHook() {
        try {
            val MilletConfig = XposedHelpers.findClassIfExists(
                "com.miui.powerkeeper.millet.MilletConfig",
                loadPackageParam.classLoader
            )
            XposedHelpers.setStaticBooleanField(MilletConfig, "isGlobal", true)
            printLog("Set com.miui.powerkeeper.millet.MilletConfig.isGlobal to true")

            val Misc = XposedHelpers.findClassIfExists(
                "com.miui.powerkeeper.provider.SimpleSettings.Misc",
                loadPackageParam.classLoader
            )
            printLog("[fcmfix] start hook com.miui.powerkeeper.provider.SimpleSettings.Misc.getBoolean")
            findAndHookMethod(Misc, "getBoolean", 3, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                    if ("gms_control" == methodHookParam.args[1] as String) {
                        printLog("Success: Success: PowerKeeper GMS Limitation. ", true)
                        methodHookParam.result = false
                    }
                }
            })

            val MilletPolicy = XposedHelpers.findClassIfExists(
                "com.miui.powerkeeper.millet.MilletPolicy",
                loadPackageParam.classLoader
            )

            val methodHook: XC_MethodHook = object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                    super.afterHookedMethod(methodHookParam)
                    var mSystemBlackList = false
                    var whiteApps = false
                    var mDataWhiteList = false

                    for (field in MilletPolicy.declaredFields) {
                        if (field.name == "mSystemBlackList") {
                            mSystemBlackList = true
                        } else if (field.name == "whiteApps") {
                            whiteApps = true
                        } else if (field.name == "mDataWhiteList") {
                            mDataWhiteList = true
                        }
                    }

                    if (mSystemBlackList) {
                        val blackList = XposedHelpers.getObjectField(
                            methodHookParam.thisObject,
                            "mSystemBlackList"
                        ) as ArrayList<*>
                        blackList.remove("com.google.android.gms")
                        XposedHelpers.setObjectField(
                            methodHookParam.thisObject,
                            "mSystemBlackList",
                            blackList
                        )
                        printLog("Success: MilletPolicy mSystemBlackList.")
                    } else {
                        printLog("Error: MilletPolicy. Field not found: com.miui.powerkeeper.millet.MilletPolicy.mSystemBlackList")
                    }
                    if (whiteApps) {
                        val whiteAppList = XposedHelpers.getObjectField(
                            methodHookParam.thisObject,
                            "whiteApps"
                        ) as ArrayList<*>
                        whiteAppList.remove("com.google.android.gms")
                        whiteAppList.remove("com.google.android.ext.services")
                        XposedHelpers.setObjectField(
                            methodHookParam.thisObject,
                            "whiteApps",
                            whiteAppList
                        )
                        printLog("Success: MilletPolicy whiteApps.")
                    } else {
                        printLog("Error: MilletPolicy. Field not found: com.miui.powerkeeper.millet.MilletPolicy.whiteApps")
                    }
                    if (mDataWhiteList) {
                        val dataWhiteList = XposedHelpers.getObjectField(
                            methodHookParam.thisObject,
                            "mDataWhiteList"
                        ) as ArrayList<String>
                        dataWhiteList.add("com.google.android.gms")

                        XposedHelpers.setObjectField(
                            methodHookParam.thisObject,
                            "mDataWhiteList",
                            dataWhiteList
                        )
                        printLog("Success: MilletPolicy mDataWhiteList.")
                    }
                }
            }
            printLog("[fcmfix] start hook com.miui.powerkeeper.millet.MilletPolicy constructor")
            XposedHelpers.findAndHookConstructor(
                MilletPolicy,
                *arrayOf(Context::class.java, methodHook)
            )
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.android.server.am.ProcessMemoryCleaner.checkBackgroundAppException")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.android.server.am.ProcessMemoryCleaner.checkBackgroundAppException")
        }
    }
}
