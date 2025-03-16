package com.kooritea.fcmfix.xposed

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kooritea.fcmfix.util.ContentProviderHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

abstract class XposedModule protected constructor(@JvmField protected var loadPackageParam: LoadPackageParam) {
    @Throws(Exception::class)
    protected open fun onCanReadConfig() {
    }

    /**
     * 尝试读取允许的应用列表但列表未初始化时调用
     */
    private fun checkUserDeviceUnlockAndUpdateConfig() {
        if (context != null && context!!.getSystemService(
                UserManager::class.java
            ).isUserUnlocked
        ) {
            try {
                onUpdateConfig()
            } catch (e: Exception) {
                printLog("更新配置文件失败: " + e.message)
            }
        }
    }

    init {
        instances.add(this)
        if (instances.size == 1) {
            initContext(loadPackageParam)
        } else {
            if (context != null && context!!.getSystemService(
                    UserManager::class.java
                ).isUserUnlocked
            ) {
                try {
                    onCanReadConfig()
                } catch (e: Exception) {
                    printLog(e.message!!)
                }
            }
        }
    }

    protected fun targetIsAllow(packageName: String): Boolean {
        if (disableAutoCleanNotification == null) {
            this.checkUserDeviceUnlockAndUpdateConfig()
        }
        if ("com.kooritea.fcmfix" == packageName) {
            return true
        }
        if (allowList != null) {
            return allowList!!.contains(packageName)
        }
        return false
    }

    protected val isDisableAutoCleanNotification: Boolean
        get() {
            if (disableAutoCleanNotification == null) {
                this.checkUserDeviceUnlockAndUpdateConfig()
            }
            return disableAutoCleanNotification != null && disableAutoCleanNotification!!
        }

    protected val isIncludeIceBoxDisableApp: Boolean
        get() {
            if (includeIceBoxDisableApp == null) {
                this.checkUserDeviceUnlockAndUpdateConfig()
            }
            return includeIceBoxDisableApp != null && includeIceBoxDisableApp!!
        }

    protected fun sendNotification(title: String) {
        sendNotification(title, null, null)
    }

    protected fun sendNotification(title: String, content: String?) {
        sendNotification(title, content, null)
    }

    @SuppressLint("MissingPermission")
    protected fun sendNotification(title: String, content: String?, pendingIntent: PendingIntent?) {
        var mtitle = title
        printLog(mtitle, false)
        mtitle = "[fcmfix]$mtitle"
        val notificationManager = NotificationManagerCompat.from(
            context!!
        )
        this.createFcmfixChannel(notificationManager)
        val notification = NotificationCompat.Builder(context!!, "fcmfix")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(mtitle)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (pendingIntent != null) {
            notification.setContentIntent(pendingIntent).setAutoCancel(true)
        }
        notificationManager.notify(System.currentTimeMillis().toInt(), notification.build())
    }

    private fun createFcmfixChannel(notificationManager: NotificationManagerCompat) {
        if (notificationManager.getNotificationChannel("fcmfix") == null) {
            val channel =
                NotificationChannel("fcmfix", "fcmfix", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "[xposed] fcmfix"
            notificationManager.createNotificationChannel(channel)
        }
    }

    protected fun isFCMIntent(intent: Intent): Boolean {
        val action = intent.action
        return if (action != null && (action.endsWith(".android.c2dm.intent.RECEIVE") ||
                    "com.google.firebase.MESSAGING_EVENT" == action ||
                    "com.google.firebase.INSTANCE_ID_EVENT" == action)
        ) {
            true
        } else {
            false
        }
    }

    companion object {
        var staticLoadPackageParam: LoadPackageParam? = null

        var allowList: Set<String>? = null

        const val TAG: String = "FcmFix"
        private var disableAutoCleanNotification: Boolean? = null
        private var includeIceBoxDisableApp: Boolean? = null

        @JvmStatic
        @SuppressLint("StaticFieldLeak")
        protected var context: Context? = null
        private val instances = ArrayList<XposedModule>()
        private var isInitReceiver = false
        private var loadConfigThread: Thread? = null

        private fun initContext(loadPackageParam: LoadPackageParam) {
            XposedHelpers.findAndHookMethod(
                "android.content.ContextWrapper", loadPackageParam.classLoader, "attachBaseContext",
                Context::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(methodHookParam: MethodHookParam) {
                        if (context == null) {
                            context = methodHookParam.thisObject as Context
                            if (context!!.getSystemService(UserManager::class.java).isUserUnlocked) {
                                callAllOnCanReadConfig()
                            } else {
                                val userUnlockIntentFilter = IntentFilter()
                                userUnlockIntentFilter.addAction(Intent.ACTION_USER_UNLOCKED)
                                context!!.registerReceiver(
                                    unlockBroadcastReceive,
                                    userUnlockIntentFilter
                                )
                            }
                        }
                    }
                })
        }

        /**
         * 每个被hook的APP第一次获取到context时调用
         */
        private fun callAllOnCanReadConfig() {
            initReceiver()
            for (instance in instances) {
                try {
                    instance.onCanReadConfig()
                } catch (e: Exception) {
                    printLog(e.message!!)
                }
            }
        }

        @JvmStatic
        protected fun printLog(text: String, isDiagnosticsLog: Boolean = false) {
            Log.d(TAG, text)
            if (isDiagnosticsLog) {
                val log = Intent("com.kooritea.fcmfix.log")
                log.putExtra("text", "[$selfPackageName]$text")

                try {
                    context!!.sendBroadcast(log)
                } catch (e: Exception) {
                    XposedBridge.log("[fcmfix] [$selfPackageName]$text")
                }
            } else {
                XposedBridge.log("[fcmfix] [$selfPackageName]$text")
            }
        }

        private val unlockBroadcastReceive: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(_context: Context, intent: Intent) {
                val action = intent.action
                if (Intent.ACTION_USER_UNLOCKED == action) {
                    try {
                        context!!.unregisterReceiver(this)
                    } catch (ignored: Exception) {
                    }
                    callAllOnCanReadConfig()
                }
            }
        }

        @JvmStatic
        protected fun onUpdateConfig() {
            if (loadConfigThread == null) {
                loadConfigThread = object : Thread() {
                    override fun run() {
                        super.run()
                        try {
                            val pref = XSharedPreferences("com.kooritea.fcmfix", "config")
                            if (pref.file.canRead() && pref.getBoolean("init", false)) {
                                allowList = pref.getStringSet("allowList", null)
                                if (allowList != null && "android" == selfPackageName) {
                                    printLog("[XSharedPreferences Mode]onUpdateConfig allowList size: " + allowList!!.size)
                                }
                                disableAutoCleanNotification =
                                    pref.getBoolean("disableAutoCleanNotification", false)
                                includeIceBoxDisableApp =
                                    pref.getBoolean("includeIceBoxDisableApp", false)
                                loadConfigThread = null
                                return
                            }
                        } catch (e: Exception) {
                            printLog("直接读取应用配置失败，将唤醒fcmfix本体进行读取: " + e.message)
                        }
                        try {
                            val contentProviderHelper = ContentProviderHelper(
                                context!!, "content://com.kooritea.fcmfix.provider/config"
                            )
                            allowList = contentProviderHelper.getStringSet("allowList")
                            if (allowList != null && "android" == selfPackageName) {
                                printLog("[ContentProvider Mode]onUpdateConfig allowList size: " + allowList!!.size)
                            }
                            disableAutoCleanNotification = contentProviderHelper.getBoolean(
                                "disableAutoCleanNotification",
                                false
                            )
                            includeIceBoxDisableApp =
                                contentProviderHelper.getBoolean("includeIceBoxDisableApp", false)
                            contentProviderHelper.close()
                        } catch (e: Exception) {
                            printLog("唤醒fcmfix应用读取配置失败: " + e.message)
                        }
                        loadConfigThread = null
                    }
                }
                loadConfigThread?.start()
            }
        }

        private fun onUninstallFcmfix() {
            val notificationManager =
                context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel("fcmfix")
            if (channel != null) {
                notificationManager.deleteNotificationChannel(channel.id)
            }
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @Synchronized
        private fun initReceiver() {
            if (!isInitReceiver && context != null) {
                isInitReceiver = true

                val updateConfigIntentFilter = IntentFilter()
                updateConfigIntentFilter.addAction("com.kooritea.fcmfix.update.config")
                if (Build.VERSION.SDK_INT >= 34) {
                    context!!.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val action = intent.action
                            if ("com.kooritea.fcmfix.update.config" == action) {
                                onUpdateConfig()
                            }
                        }
                    }, updateConfigIntentFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context!!.registerReceiver(object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val action = intent.action
                            if ("com.kooritea.fcmfix.update.config" == action) {
                                onUpdateConfig()
                            }
                        }
                    }, updateConfigIntentFilter)
                }

                val unInstallIntentFilter = IntentFilter()
                unInstallIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
                unInstallIntentFilter.addDataScheme("package")
                context!!.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val action = intent.action
                        if (Intent.ACTION_PACKAGE_REMOVED == action && "com.kooritea.fcmfix" == intent.data!!
                                .schemeSpecificPart
                        ) {
                            val extras = intent.extras
                            if (extras!!.containsKey(Intent.EXTRA_REPLACING) && extras.getBoolean(
                                    Intent.EXTRA_REPLACING
                                )
                            ) {
                                return
                            }
                            onUninstallFcmfix()
                            if ("android" == selfPackageName) {
                                printLog("Fcmfix已卸载，重启后停止生效。")
                            }
                        }
                    }
                }, unInstallIntentFilter)
            }
        }

        protected val selfPackageName: String
            get() = if (staticLoadPackageParam == null) "UNKNOWN" else staticLoadPackageParam!!.packageName
    }
}
