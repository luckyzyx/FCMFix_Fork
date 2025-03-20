package com.luckyzyx.fcmfix.hook.scopes.android

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.highcapable.yukihookapi.hook.type.android.ResolveInfoClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage
import com.luckyzyx.fcmfix.hook.HookUtils.isFCMIntent
import com.luckyzyx.fcmfix.hook.HookUtils.sendGsmLogBroadcast

object AutoStartFix : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null

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
            method { name = "isAllowStartFromBroadCast";returnType = BooleanType }.hookAll {
                after {
                    val ams = field {
                        type = "com.android.server.am.ActivityManagerService"
                    }.get(instance).any() ?: return@after
                    val context = ams.current().field {
                        name = "mContext";superClass()
                    }.cast<Context>() ?: return@after
                    val intent = args(args.indexOf(IntentClass)).cast<Intent>()
                        ?: return@after
                    val resolveInfo = args(args.indexOf(ResolveInfoClass)).cast<ResolveInfo>()
                        ?: return@after
                    val packName = resolveInfo.activityInfo.applicationInfo.packageName
                    if (isFCMIntent(intent) && isAllowPackage(allowList, packName)) {
                        context.sendGsmLogBroadcast("[$packName] Allow Start From BroadCast")
                        resultTrue()
                    }
                }
            }
        }
    }
}