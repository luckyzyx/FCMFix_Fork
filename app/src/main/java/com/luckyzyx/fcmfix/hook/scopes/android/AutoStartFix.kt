package com.luckyzyx.fcmfix.hook.scopes.android

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType
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
        "com.android.server.am.OplusAppStartupManager".toClassOrNull()?.apply {
            method { name = "shouldPreventSendReceiverReal";returnType = BooleanType }.hookAll {
                before {
                    if (!isBootComplete) return@before
                    val ams = field {
                        type = "com.android.server.am.ActivityManagerService"
                    }.get(instance).any() ?: return@before
                    val context = ams.current().field {
                        name = "mContext";superClass()
                    }.cast<Context>() ?: return@before
                    val broadcastRecord = args().first().any() ?: return@before
                    val intent = broadcastRecord.current().field { name = "intent" }
                        .cast<Intent>() ?: return@before
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