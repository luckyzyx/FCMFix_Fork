package com.luckyzyx.fcmfix.hook.scopes.android

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.ArraySet
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.hook.HookUtils.isFCMIntent
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast

object AutoStartFix : YukiBaseHooker() {

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

        //Source OplusAppStartupManager
        "com.android.server.am.OplusAppStartupManager".toClassOrNull()?.resolve()?.apply {
            method {
                name = "shouldPreventSendReceiverReal"
                returnType = Boolean::class
            }.hookAll {
                before {
                    if (!isBootComplete) return@before
                    val ams = firstField {
                        type = "com.android.server.am.ActivityManagerService"
                    }.of(instance).get() ?: return@before
                    val context = ams.asResolver().firstField {
                        name = "mContext";superclass()
                    }.get<Context>() ?: return@before
                    val broadcastRecord = args().first().any() ?: return@before
                    val intent = broadcastRecord.asResolver().firstField { name = "intent" }
                        .get<Intent>() ?: return@before
                    val resolveIndex = args.indexOfFirst { it is ResolveInfo }.takeIf { it != -1 }
                        ?: return@before
                    val resolveInfo = args(resolveIndex).cast<ResolveInfo>() ?: return@before
                    val packName = resolveInfo.activityInfo.applicationInfo.packageName
                    if (isFCMIntent(intent) && isAllowPackage(allowList, packName)) {
                        context.sendGsmLogBroadcast("[$packName] Allow Start From BroadCast")
                        resultFalse()
                    }
                }
            }
        }
    }
}