package com.luckyzyx.fcmfix.hook.scopes.android

import android.util.ArraySet
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.hook.HookUtils.isFCMAction

object OplusProxyFix : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null
    var isBootComplete = false

    override fun onHook() {
        var allowList = prefs("config").getStringSet("allowList", ArraySet())

        @Suppress("UNCHECKED_CAST")
        BroadcastFix.callback = { key: String, value: Any ->
            when (key) {
                "allowList" -> allowList = value as Set<String>
            }
        }

        val oplusProxyResultClazz = "com.android.server.am.OplusProxyBroadcast\$RESULT"
            .toClassOrNull() ?: return
        val notInclude =
            oplusProxyResultClazz.resolve().firstField { name = "NOT_INCLUDE" }.get()
        val notProxy =
            oplusProxyResultClazz.resolve().firstField { name = "NOT_PROXY" }.get()
        val proxy = oplusProxyResultClazz.resolve().firstField { name = "PROXY" }.get()

        //Source OplusProxyBroadcast
        "com.android.server.am.OplusProxyBroadcast".toClass().resolve().apply {
            firstMethod {
                name = "shouldProxy"
                returnType = oplusProxyResultClazz
            }.hook {
                before {
                    if (!isBootComplete) return@before
                    val callingPkg = args(3).string()
                    val pkgName = args(5).string()
                    val action = args(6).string()

                    if (isFCMAction(action) && isAllowPackage(allowList, pkgName)) {
                        YLog.debug("shouldProxy -> bypass callingPkg: $callingPkg | pkgName: $pkgName | action: $action")
                        result = notInclude
                    }
                }
            }
        }
    }
}