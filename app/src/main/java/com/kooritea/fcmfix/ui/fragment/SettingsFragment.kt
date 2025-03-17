package com.kooritea.fcmfix.ui.fragment

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.kooritea.fcmfix.BuildConfig

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "config"
        preferenceScreen = preferenceManager.createPreferenceScreen(requireActivity()).apply {
            addPreference(SwitchPreference(context).apply {
                title = "隐藏启动器图标"
                key = "hide_launcher_icon"
                isChecked = context.packageManager.getComponentEnabledSetting(
                    ComponentName(context.packageName, "${BuildConfig.APPLICATION_ID}.Home")
                ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                isPersistent = false
                isIconSpaceReserved = false
                setOnPreferenceChangeListener { _, isChecked ->
                    context.packageManager.setComponentEnabledSetting(
                        ComponentName(
                            BuildConfig.APPLICATION_ID,
                            "${BuildConfig.APPLICATION_ID}.Home"
                        ),
                        if (isChecked as Boolean) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    true
                }
            })
            addPreference(SwitchPreference(context).apply {
                title = "阻止应用停止时自动清除通知"
                key = "disableAutoCleanNotification"
                setDefaultValue(false)
                isIconSpaceReserved = false
            })
            addPreference(SwitchPreference(context).apply {
                title = "允许唤醒被冰箱冻结的应用"
                key = "includeIceBoxDisableApp"
                setDefaultValue(false)
                isIconSpaceReserved = false
            })
            addPreference(Preference(context).apply {
                title = "打开FCM Diagnostics"
                key = "open_fcm_diagnostics"
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.setPackage("com.google.android.gms")
                    intent.setComponent(
                        ComponentName(
                            "com.google.android.gms",
                            "com.google.android.gms.gcm.GcmDiagnostics"
                        )
                    )
                    startActivity(intent)
                    true
                }
            })
        }
    }
}
