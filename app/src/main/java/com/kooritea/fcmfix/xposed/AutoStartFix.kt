package com.kooritea.fcmfix.xposed

import android.content.Intent
import com.kooritea.fcmfix.util.XposedUtils.findAndHookMethodAnyParam
import com.kooritea.fcmfix.util.XposedUtils.findMethod
import com.kooritea.fcmfix.util.XposedUtils.getObjectFieldByPath
import com.kooritea.fcmfix.util.XposedUtils.tryFindAndHookMethod
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class AutoStartFix(loadPackageParam: LoadPackageParam) : XposedModule(loadPackageParam) {
    private val FCM_RECEIVE = ".android.c2dm.intent.RECEIVE"

    init {
        try {
            this.startHook()
            this.startHookRemovePowerPolicy()
        } catch (e: Exception) {
            printLog("hook error AutoStartFix:" + e.message)
        }
    }

    protected fun startHook() {
        try {
            // miui12
            val BroadcastQueueInjector = XposedHelpers.findClass(
                "com.android.server.am.BroadcastQueueInjector",
                loadPackageParam.classLoader
            )
            findAndHookMethodAnyParam(
                BroadcastQueueInjector,
                "checkApplicationAutoStart",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                        val intent = XposedHelpers.getObjectField(
                            methodHookParam.args[2],
                            "intent"
                        ) as Intent
                        if (isFCMIntent(intent)) {
                            val target =
                                if (intent.component == null) intent.getPackage() else intent.component!!
                                    .packageName
                            if (targetIsAllow(target!!)) {
                                XposedHelpers.callStaticMethod(
                                    BroadcastQueueInjector, "checkAbnormalBroadcastInQueueLocked",
                                    methodHookParam.args[1], methodHookParam.args[0]
                                )
                                printLog("Allow Auto Start: $target", true)
                                methodHookParam.result = true
                            }
                        }
                    }
                })
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.android.server.am.BroadcastQueueInjector.checkApplicationAutoStart")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.android.server.am.BroadcastQueueInjector.checkApplicationAutoStart")
        }
        try {
            // miui13
            val BroadcastQueueImpl = XposedHelpers.findClass(
                "com.android.server.am.BroadcastQueueImpl",
                loadPackageParam.classLoader
            )
            findAndHookMethodAnyParam(
                BroadcastQueueImpl,
                "checkApplicationAutoStart",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                        val intent = XposedHelpers.getObjectField(
                            methodHookParam.args[1],
                            "intent"
                        ) as Intent
                        if (isFCMIntent(intent)) {
                            val target =
                                if (intent.component == null) intent.getPackage() else intent.component!!
                                    .packageName
                            if (targetIsAllow(target!!)) {
                                XposedHelpers.callMethod(
                                    methodHookParam.thisObject,
                                    "checkAbnormalBroadcastInQueueLocked",
                                    methodHookParam.args[0]
                                )
                                printLog("Allow Auto Start: $target", true)
                                methodHookParam.result = true
                            }
                        }
                    }
                })
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.android.server.am.BroadcastQueueImpl.checkApplicationAutoStart")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.android.server.am.BroadcastQueueImpl.checkApplicationAutoStart")
        }

        try {
            // hyperos
            val BroadcastQueueImpl = XposedHelpers.findClass(
                "com.android.server.am.BroadcastQueueModernStubImpl",
                loadPackageParam.classLoader
            )
            printLog("[fcmfix] start hook com.android.server.am.BroadcastQueueModernStubImpl.checkApplicationAutoStart")
            findAndHookMethodAnyParam(
                BroadcastQueueImpl,
                "checkApplicationAutoStart",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                        val intent = XposedHelpers.getObjectField(
                            methodHookParam.args[1],
                            "intent"
                        ) as Intent
                        val target =
                            if (intent.component == null) intent.getPackage() else intent.component!!
                                .packageName
                        if (targetIsAllow(target!!)) {
                            // 无日志，先放了
                            printLog(
                                "[" + intent.action + "]checkApplicationAutoStart package_name: " + target,
                                true
                            )
                            methodHookParam.result = true

                            //                        if(isFCMIntent(intent)){
//                            printLog("checkApplicationAutoStart package_name: " + target, true);
//                            methodHookParam.setResult(true);
//                        }else{
//                            printLog("[skip][" + intent.getAction() + "]checkApplicationAutoStart package_name: " + target, true);
//                        }
                        }
                    }
                })

            printLog("[fcmfix] start hook com.android.server.am.BroadcastQueueModernStubImpl.checkReceiverIfRestricted")
            findAndHookMethodAnyParam(
                BroadcastQueueImpl,
                "checkReceiverIfRestricted",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                        val intent = XposedHelpers.getObjectField(
                            methodHookParam.args[1],
                            "intent"
                        ) as Intent
                        val target =
                            if (intent.component == null) intent.getPackage() else intent.component!!
                                .packageName
                        if (targetIsAllow(target!!)) {
                            if (isFCMIntent(intent)) {
                                printLog(
                                    "BroadcastQueueModernStubImpl.checkReceiverIfRestricted package_name: $target",
                                    true
                                )
                                methodHookParam.result = false
                            }
                        }
                    }
                })
        } catch (e: ClassNotFoundError) {
            printLog("No Such class com.android.server.am.BroadcastQueueModernStubImpl")
        } catch (e: NoSuchMethodError) {
            printLog("No Such class com.android.server.am.BroadcastQueueModernStubImpl")
        }

        try {
            val AutoStartManagerServiceStubImpl = XposedHelpers.findClass(
                "com.android.server.am.AutoStartManagerServiceStubImpl",
                loadPackageParam.classLoader
            )
            val methodHook: XC_MethodHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                    val intent = methodHookParam.args[1] as Intent
                    val target = intent.component!!.packageName
                    if (targetIsAllow(target)) {
                        // 拿不到action，先放了
                        printLog(
                            "[" + intent.action + "]AutoStartManagerServiceStubImpl.isAllowStartService package_name: " + target,
                            true
                        )
                        methodHookParam.result = true
                        //                        if(isFCMIntent(intent)){
//                            printLog("AutoStartManagerServiceStubImpl.isAllowStartService package_name: " + target, true);
//                            methodHookParam.setResult(true);
//                        }else{
//                            printLog("[skip][" + intent.getAction() + "]AutoStartManagerServiceStubImpl.isAllowStartService package_name: " + target, true);
//                        }
                    }
                }
            }

            printLog("[fcmfix] start hook com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService")
            val unhook1 = tryFindAndHookMethod(
                AutoStartManagerServiceStubImpl,
                "isAllowStartService",
                3,
                methodHook
            )
            val unhook2 = tryFindAndHookMethod(
                AutoStartManagerServiceStubImpl,
                "isAllowStartService",
                4,
                methodHook
            )
            if (unhook1 == null && unhook2 == null) {
                throw NoSuchMethodError()
            }
        } catch (e: ClassNotFoundError) {
            printLog("No Such Class com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Class com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService")
        }

        try {
            val SmartPowerService = XposedHelpers.findClass(
                "com.android.server.am.SmartPowerService",
                loadPackageParam.classLoader
            )

            printLog("[fcmfix] start hook com.android.server.am.SmartPowerService.shouldInterceptBroadcast")
            findAndHookMethodAnyParam(
                SmartPowerService,
                "shouldInterceptBroadcast",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(methodHookParam: MethodHookParam) {
                        val intent = XposedHelpers.getObjectField(
                            methodHookParam.args[1],
                            "intent"
                        ) as Intent
                        val target =
                            if (intent.component == null) intent.getPackage() else intent.component!!
                                .packageName
                        if (targetIsAllow(target!!)) {
                            if (isFCMIntent(intent)) {
                                printLog(
                                    "SmartPowerService.shouldInterceptBroadcast package_name: $target",
                                    true
                                )
                                methodHookParam.result = false
                            }
                        }
                    }
                })
        } catch (e: ClassNotFoundError) {
            printLog("No Such Class com.android.server.am.SmartPowerService")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Class com.android.server.am.SmartPowerService")
        }

        try {
            // oos15/cos15
            val method = findMethod(
                XposedHelpers.findClass(
                    "com.android.server.am.OplusAppStartupManager",
                    loadPackageParam.classLoader
                ), "isAllowStartFromBroadCast", 4
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                    val intent = methodHookParam.args[2] as Intent
                    val packageName = getObjectFieldByPath(
                        methodHookParam.args[3],
                        "activityInfo.applicationInfo.packageName"
                    ) as String
                    if (isFCMIntent(intent) && targetIsAllow(packageName)) {
                        printLog(
                            "com.android.server.am.OplusAppStartupManager.isAllowStartFromBroadCast(4) package_name: $packageName",
                            true
                        )
                        methodHookParam.result = true
                    }
                }
            })
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.android.server.am.OplusAppStartupManager.isAllowStartFromBroadCast(4)")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.android.server.am.OplusAppStartupManager.isAllowStartFromBroadCast(4)")
        }
        try {
            // oos15/cos15
            val method = findMethod(
                XposedHelpers.findClass(
                    "com.android.server.am.OplusAppStartupManager",
                    loadPackageParam.classLoader
                ), "isAllowStartFromBroadCast", 5
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                    val intent = methodHookParam.args[3] as Intent
                    val packageName = getObjectFieldByPath(
                        methodHookParam.args[4],
                        "activityInfo.applicationInfo.packageName"
                    ) as String
                    if (isFCMIntent(intent) && targetIsAllow(packageName)) {
                        printLog(
                            "com.android.server.am.OplusAppStartupManager.isAllowStartFromBroadCast(5) package_name: $packageName",
                            true
                        )
                        methodHookParam.result = true
                    }
                }
            })
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.android.server.am.OplusAppStartupManager.isAllowStartFromBroadCast(5)")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.android.server.am.OplusAppStartupManager.isAllowStartFromBroadCast(5)")
        }
    }

    protected fun startHookRemovePowerPolicy() {
        try {
            // MIUI13
            val AutoStartManagerService = XposedHelpers.findClass(
                "com.miui.server.smartpower.SmartPowerPolicyManager",
                loadPackageParam.classLoader
            )
            findAndHookMethodAnyParam(
                AutoStartManagerService,
                "shouldInterceptService",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val intent = param.args[0] as Intent
                        if ("com.google.firebase.MESSAGING_EVENT" == intent.action) {
                            val target =
                                if (intent.component == null) intent.getPackage() else intent.component!!
                                    .packageName
                            if (targetIsAllow(target!!)) {
                                printLog("Disable MIUI Intercept: $target", true)
                                param.result = false
                            }
                        }
                    }
                })
        } catch (e: ClassNotFoundError) {
            printLog("No Such Method com.miui.server.smartpower.SmartPowerPolicyManager.shouldInterceptService")
        } catch (e: NoSuchMethodError) {
            printLog("No Such Method com.miui.server.smartpower.SmartPowerPolicyManager.shouldInterceptService")
        }
    }
}
