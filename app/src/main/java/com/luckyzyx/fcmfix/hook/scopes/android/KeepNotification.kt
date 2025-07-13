package com.luckyzyx.fcmfix.hook.scopes.android

import android.util.ArraySet
import com.google.android.material.datepicker.DateValidatorPointBackward.before
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.luckyzyx.fcmfix.hook.HookUtils.isAllowPackage

object KeepNotification : YukiBaseHooker() {

    var callback: ((key: String, value: Any) -> Unit)? = null
    var isBootComplete = false

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
        "com.android.server.notification.OplusNotificationManagerServiceExtImpl".toClass().resolve().apply {
            firstMethod { name = "shouldKeepNotifcationWhenForceStop" }.hook {
                before {
                    if (!isBootComplete) return@before
                    val packName = args().first().string()
                    val reason = args().last().int()
                    if (disableACN && isAllowPackage(allowList, packName)) {
                        if (reason == 10020 || reason == 10021) resultTrue()
                    }
                }
            }
        }
    }
}