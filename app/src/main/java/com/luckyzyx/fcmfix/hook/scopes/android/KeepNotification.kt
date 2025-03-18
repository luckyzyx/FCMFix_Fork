package com.luckyzyx.fcmfix.hook.scopes.android

import android.service.notification.NotificationListenerService
import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage

object KeepNotification : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null

    override fun onHook() {
        var allowList = prefs("config").getStringSet("allowList", ArraySet())
        var disableACN = prefs("config").getBoolean("disableAutoCleanNotification", false)

        @Suppress("UNCHECKED_CAST")
        callback = { key: String, value: Any ->
            when (key) {
                "allowList" -> allowList = value as Set<String>
                "disableAutoCleanNotification" -> disableACN = value as Boolean
            }
        }

        //Source NotificationManagerService
        "com.android.server.notification.NotificationManagerService".toClass().apply {
            method { name = "cancelAllNotificationsInt" }.hook {
                before {
                    val packName = args(args.indexOfFirst { it == StringClass }).cast<String>()
                        ?: return@before
                    val reason = args(args.indexOfLast { it == IntType }).cast<Int>()
                        ?: return@before
                    if (disableACN && isAllowPackage(allowList, packName)) {
                        if (reason == NotificationListenerService.REASON_PACKAGE_CHANGED) {
                            resultNull()
                        }
                        if (reason == 10020) { // cos15/oos15
                            resultNull()
                        }
                    }
                }
            }
        }
    }
}