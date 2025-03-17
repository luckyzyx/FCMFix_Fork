package com.kooritea.fcmfix.hook

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

@Suppress("MemberVisibilityCanBePrivate")
object IceboxUtils {
    const val REQUEST_CODE: Int = 0x2333
    const val PACKAGE_NAME: String = "com.catchingnow.icebox"
    const val SDK_PERMISSION: String = "$PACKAGE_NAME.SDK"
    private val PERMISSION_URI: Uri = "content://$PACKAGE_NAME.SDK".toUri()
    private val NO_PERMISSION_URI: Uri = "content://$PACKAGE_NAME.STATE".toUri()
    private const val TAG = "IceboxUtils"
    private var isIceBoxWorking = false
    private var authorizedPendingIntent: PendingIntent? = null

    fun queryPermission(context: Context?): PendingIntent? {
        if (authorizedPendingIntent == null) {
            authorizedPendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, Intent(context, IceboxUtils::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return authorizedPendingIntent
    }

    private fun queryWorkMode(context: Context): Boolean {
        try {
            val extra = Bundle()
            extra.putParcelable("authorize", queryPermission(context))
            val bundle = checkNotNull(
                context.contentResolver.call(
                    NO_PERMISSION_URI, "query_mode", null, extra
                )
            )
            return bundle.getString("work_mode", null) != "MODE_NOT_AVAILABLE"
        } catch (e: Exception) {
            Log.e(TAG, "[icebox] queryWorkMode: " + e.message)
            return false
        }
    }

    @JvmStatic
    fun isAppEnabled(context: Context, packageName: String): Boolean {
        try {
            val applicationInfo = context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
            )
            return applicationInfo.enabled
        } catch (e: Exception) {
            Log.e(TAG, "[icebox] " + packageName + " " + e.message)
        }
        return true
    }

    @RequiresPermission(SDK_PERMISSION)
    fun enableApp(context: Context, enable: Boolean, vararg packageNames: String?) {
        val userHandle = Process.myUserHandle().hashCode()
        val extra = Bundle()
        extra.putParcelable("authorize", queryPermission(context))
        extra.putStringArray("package_names", packageNames)
        extra.putInt("user_handle", userHandle)
        extra.putBoolean("enable", enable)
        context.contentResolver.call(PERMISSION_URI, "set_enable", null, extra)
    }

    @JvmStatic
    fun activeApp(context: Context, pkg: String) {
        try {
            if (!isIceBoxWorking) {
                if (ContextCompat.checkSelfPermission(
                        context, SDK_PERMISSION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "[icebox] need permission $pkg")
                    return
                }
                if (!queryWorkMode(context)) {
                    Log.e(TAG, "[icebox] is not working...")
                    return
                }
                isIceBoxWorking = true
            }
            if (!isAppEnabled(context, pkg)) {
                enableApp(context, true, pkg)
                Log.i(TAG, "[icebox] successfully enable $pkg")
            } else {
                Log.e(TAG, "[icebox] has been enabled $pkg")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[icebox] " + pkg + " " + e.message)
        }
    }
}
