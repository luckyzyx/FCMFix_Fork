package com.luckyzyx.fcmfix.data

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

class AppInfo(packageManager: PackageManager, packageInfo: PackageInfo) {
    var name: String = packageInfo.applicationInfo!!.loadLabel(packageManager).toString()
    var packageName: String = packageInfo.packageName
    var icon: Drawable = packageInfo.applicationInfo!!.loadIcon(packageManager)
    var isFcm: Boolean = false
}