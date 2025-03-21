package com.luckyzyx.fcmfix.hook.scopes.android

import android.util.ArraySet
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
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

        //Source NotificationManagerService -> cancelAllNotificationsInt
        //Source OplusNotificationManagerServiceExtImpl -> shouldKeepNotifcationWhenForceStop
        //Source OplusNotificationCommonPolicy -> shouldKeepNotifcationWhenForceStop
        "com.android.server.notification.OplusNotificationManagerServiceExtImpl".toClass().apply {
            method { name = "shouldKeepNotifcationWhenForceStop" }.hook {
                before {
                    val packName = args().first().string()
                    if (disableACN && isAllowPackage(allowList, packName)) {
                        resultTrue()
                    }
                }
            }
        }
    }
}