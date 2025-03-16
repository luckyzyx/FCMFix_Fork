package com.kooritea.fcmfix

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.ArraySet
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Filter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drake.net.utils.scopeLife
import com.drake.net.utils.withDefault
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.kooritea.fcmfix.data.AppInfo
import com.kooritea.fcmfix.databinding.ActivityMainBinding
import com.kooritea.fcmfix.databinding.AppItemBinding
import com.kooritea.fcmfix.util.IceboxUtils
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

@SuppressLint("WorldReadableFiles")
class MainActivity : AppCompatActivity(), MenuProvider {
    private lateinit var binding: ActivityMainBinding
    private var appAdapter: AppInfoAdapter? = null

    private var allAppInfo = ArrayList<AppInfo>()
    private var enabledList = ArraySet<String>()

    private lateinit var configFile: File
    private var config = JSONObject()

    private var disableAutoCleanNotification = false
    private var includeIceBoxDisableApp = false

    @Suppress("DEPRECATION")
    private val prefs by lazy {
        getSharedPreferences("config", MODE_WORLD_READABLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addMenuProvider(this)

        if (DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.searchViewLayout.apply {
            hint = "Name / PackageName"
        }
        binding.searchView.apply {
            addTextChangedListener(onTextChanged = { text: CharSequence?, _: Int, _: Int, _: Int ->
                appAdapter?.getFilter?.filter(text)
            })
        }
        binding.swipeRefreshLayout.apply {
            setOnRefreshListener {
                loadData()
            }
        }

        if (prefs == null) {
            MaterialAlertDialogBuilder(this).apply {
                setCancelable(false)
                setMessage("模块未激活,请重试!")
                setPositiveButton(android.R.string.ok) { _, _ -> exitProcess(0) }
                setOnDismissListener { exitProcess(0) }
                show()
            }
            return
        }

        requestIceBox()

        if (allAppInfo.isEmpty()) loadData()
    }

    private fun requestIceBox() {
        try {
            if (ContextCompat.checkSelfPermission(this, IceboxUtils.SDK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(IceboxUtils.SDK_PERMISSION), IceboxUtils.REQUEST_CODE
                )
            }
        } catch (ignored: Exception) {
        }
    }

    private fun loadAllowData() {
        try {
            config = JSONObject()
            configFile = File(filesDir, "config.json")
            if (!configFile.exists()) {
                configFile.createNewFile()
                configFile.writeText(JSONObject().toString(2))
            }
            val config = JSONObject(configFile.readText())
            val jsonAllowList = config.optJSONArray("allowList") ?: JSONArray()
            for (i in 0 until jsonAllowList.length()) {
                val packName = jsonAllowList.optString(i)
                if (packName.isNotBlank()) enabledList.add(packName)
            }
            disableAutoCleanNotification = config.optBoolean(
                "disableAutoCleanNotification",
                prefs.getBoolean("disableAutoCleanNotification", false)
            )
            config.put("disableAutoCleanNotification", disableAutoCleanNotification)
            includeIceBoxDisableApp = config.optBoolean(
                "includeIceBoxDisableApp",
                prefs.getBoolean("includeIceBoxDisableApp", false)
            )
            config.put("includeIceBoxDisableApp", includeIceBoxDisableApp)
        } catch (e: IOException) {
            Log.e("onCreate", e.toString())
        } catch (e: JSONException) {
            Log.e("onCreate", e.toString())
        }
    }

    private fun loadData() {
        loadAllowData()

        scopeLife {
            allAppInfo.clear()

            binding.swipeRefreshLayout.isRefreshing = true
            binding.searchViewLayout.isEnabled = false
            binding.searchView.text = null

            withDefault {
                val allowAndFcm = ArrayList<AppInfo>()
                val onlyAllow = ArrayList<AppInfo>()
                val onlyFcm = ArrayList<AppInfo>()
                val noFcmNoAllow = ArrayList<AppInfo>()

                packageManager.getInstalledPackages(
                    PackageManager.GET_RECEIVERS or PackageManager.MATCH_DISABLED_COMPONENTS
                            or PackageManager.MATCH_UNINSTALLED_PACKAGES
                ).forEachIndexed { _, packageInfo ->
                    val appInfo = AppInfo(packageManager, packageInfo)
                    val isAllow = enabledList.contains(appInfo.packageName)
                    val hasFcm = packageInfo.receivers?.any {
                        it.name == "com.google.firebase.iid.FirebaseInstanceIdReceiver"
                                || it.name == "com.google.android.gms.measurement.AppMeasurementReceiver"
                    } ?: false
                    appInfo.isFcm = hasFcm

                    if (isAllow && hasFcm) {
                        allowAndFcm.add(appInfo)
                    } else if (isAllow) {
                        onlyAllow.add(appInfo)
                    } else if (hasFcm) {
                        onlyFcm.add(appInfo)
                    } else {
                        noFcmNoAllow.add(appInfo)
                    }
                }

                allowAndFcm.sortBy { it.name }
                onlyAllow.sortBy { it.name }
                onlyFcm.sortBy { it.name }
                noFcmNoAllow.sortBy { it.name }

                allAppInfo.addAll(allowAndFcm)
                allAppInfo.addAll(onlyAllow)
                allAppInfo.addAll(onlyFcm)
                allAppInfo.addAll(noFcmNoAllow)
            }

            binding.recyclerView.apply {
                appAdapter = AppInfoAdapter()
                adapter = appAdapter
                layoutManager = LinearLayoutManager(context)
                FastScrollerBuilder(this).useMd2Style().build()
            }

            binding.swipeRefreshLayout.isRefreshing = false
            binding.searchViewLayout.isEnabled = true
        }
    }

    private fun addAppInAllowList(packageName: String) {
        enabledList.add(packageName)
        updateConfig()
    }

    private fun deleteAppInAllowList(packageName: String) {
        enabledList.remove(packageName)
        updateConfig()
    }

    private fun updateConfig() {
        try {
            prefs.edit(commit = true) {
                putBoolean("init", true)
                putStringSet("allowList", enabledList.toSet())
                putBoolean("disableAutoCleanNotification", disableAutoCleanNotification)
                putBoolean("includeIceBoxDisableApp", includeIceBoxDisableApp)
            }
        } catch (e: Exception) {
            Log.e("updateConfig", e.toString())
        }
        try {
            config.put("allowList", JSONArray(enabledList))
            config.put("disableAutoCleanNotification", disableAutoCleanNotification)
            config.put("includeIceBoxDisableApp", includeIceBoxDisableApp)
            configFile.writeText(config.toString(2))
            sendBroadcast(Intent("com.kooritea.fcmfix.update.config"))
        } catch (e: IOException) {
            Log.e("updateConfig", e.toString())
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("更新配置文件失败")
                .setMessage(e.message)
                .show()
        } catch (e: JSONException) {
            Log.e("updateConfig", e.toString())
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("更新配置文件失败")
                .setMessage(e.message)
                .show()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu, menu)

        menu.findItem(R.id.hide_launcher_icon)?.isChecked =
            packageManager.getComponentEnabledSetting(
                ComponentName(packageName, "com.kooritea.fcmfix.Home")
            ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        menu.findItem(R.id.disable_auto_clean_notification)
            ?.setChecked(disableAutoCleanNotification)

        menu.findItem(R.id.include_ice_box_disable_app)
            .setChecked(includeIceBoxDisableApp)

        menu.findItem(R.id.select_all_fcm_apps)?.setOnMenuItemClickListener {
            allAppInfo.filter { it.isFcm }.forEach { i -> enabledList.add(i.packageName) }
            appAdapter?.refreshDatas()
            updateConfig()
            false
        }

        menu.findItem(R.id.open_fcm_diagnostics)?.setOnMenuItemClickListener {
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
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.hide_launcher_icon -> {
                val packageManager = packageManager
                packageManager.setComponentEnabledSetting(
                    ComponentName("com.kooritea.fcmfix", "com.kooritea.fcmfix.Home"),
                    if (menuItem.isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            R.id.disable_auto_clean_notification -> {
                try {
                    menuItem.isChecked = !menuItem.isChecked
                    disableAutoCleanNotification = menuItem.isChecked
                    config.put("disableAutoCleanNotification", disableAutoCleanNotification)
                    updateConfig()
                } catch (e: JSONException) {
                    Log.e("onOptionsItemSelected", e.toString())
                }
            }

            R.id.include_ice_box_disable_app -> {
                try {
                    menuItem.isChecked = !menuItem.isChecked
                    includeIceBoxDisableApp = menuItem.isChecked
                    config.put("includeIceBoxDisableApp", includeIceBoxDisableApp)
                    updateConfig()
                } catch (e: JSONException) {
                    Log.e("onOptionsItemSelected", e.toString())
                }
            }
        }
        return true
    }

    inner class AppInfoAdapter : RecyclerView.Adapter<ViewHolder>() {

        private var filterDatas = ArrayList<AppInfo>()

        init {
            filterDatas.clear()
            filterDatas = allAppInfo
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = AppItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return filterDatas.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val appInfo = filterDatas[position]
            val appIcon = appInfo.icon
            val appName = appInfo.name
            val packName = appInfo.packageName
            val isFcm = appInfo.isFcm

            holder.appIcon.setImageDrawable(appIcon)
            holder.appName.text = appName
            holder.fcm.isVisible = isFcm
            holder.packName.text = packName
            holder.appInfoView.setOnClickListener(null)
            holder.switch.setOnCheckedChangeListener(null)

            holder.switch.isChecked = enabledList.contains(packName)
            holder.appInfoView.setOnClickListener {
                holder.switch.performClick()
            }
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) addAppInAllowList(packName)
                else deleteAppInAllowList(packName)
            }
        }

        val getFilter = object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val filterStr = constraint.toString().lowercase()
                filterDatas = if (constraint.isBlank()) allAppInfo
                else {
                    val filterlist = ArrayList<AppInfo>()
                    allAppInfo.forEach {
                        if (it.name.lowercase().contains(filterStr)
                            || it.packageName.lowercase().contains(filterStr)
                        ) filterlist.add(it)
                    }
                    filterlist
                }
                val filterResults = FilterResults()
                filterResults.values = filterDatas
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                filterDatas = results.values as ArrayList<AppInfo>
                refreshDatas()
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun refreshDatas() {
            notifyDataSetChanged()
        }
    }

    class ViewHolder(binding: AppItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val appInfoView: LinearLayout = binding.root
        val appIcon: ImageView = binding.icon
        val appName: TextView = binding.name
        val fcm: TextView = binding.includeFcm
        val packName: TextView = binding.packageName
        val switch: MaterialSwitch = binding.switchItem
    }
}