package com.luckyzyx.fcmfix.ui.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.ArraySet
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.drake.net.utils.scopeLife
import com.google.android.material.materialswitch.MaterialSwitch
import com.luckyzyx.fcmfix.BuildConfig
import com.luckyzyx.fcmfix.R
import com.luckyzyx.fcmfix.data.AppInfo
import com.luckyzyx.fcmfix.databinding.AppItemBinding
import com.luckyzyx.fcmfix.databinding.FragmentListBinding
import com.luckyzyx.fcmfix.hook.IceboxUtils
import com.luckyzyx.fcmfix.utils.SPUtils.getBoolean
import com.luckyzyx.fcmfix.utils.SPUtils.getStringSet
import com.luckyzyx.fcmfix.utils.SPUtils.putStringSet
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class AppListFragment : Fragment(), MenuProvider {

    private lateinit var binding: FragmentListBinding
    private var appAdapter: AppInfoAdapter? = null

    private var allAppInfo = ArrayList<AppInfo>()
    private var enabledList = ArraySet<String>()

    private var disableAutoCleanNotification = false
    private var includeIceBoxDisableApp = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        binding = FragmentListBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

        requestIceBox()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.select_all_fcm_apps -> {
                allAppInfo.filter { it.isFcm }.forEach { i -> enabledList.add(i.packageName) }
                appAdapter?.refreshDatas()
                Toast.makeText(context, "已勾选全部FCM应用", Toast.LENGTH_SHORT).show()
                updateConfig()
            }

            R.id.menu_settings -> {
                findNavController().navigate(R.id.settingsFragment)
            }
        }
        return true
    }

    private fun requestIceBox() {
        try {
            if (ContextCompat.checkSelfPermission(requireActivity(), IceboxUtils.SDK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(), arrayOf(IceboxUtils.SDK_PERMISSION), IceboxUtils.REQUEST_CODE
                )
            }
        } catch (ignored: Exception) {
        }
    }

    private fun loadAllowData() {
        try {
            enabledList.clear()
            enabledList.addAll(requireActivity().getStringSet("config", "allowList", ArraySet()))
            disableAutoCleanNotification =
                requireActivity().getBoolean("config", "disableAutoCleanNotification", false)
            includeIceBoxDisableApp =
                requireActivity().getBoolean("config", "includeIceBoxDisableApp", false)
        } catch (e: Throwable) {
            Log.e("loadAllowData", e.toString())
        }
    }

    private fun loadData() {
        loadAllowData()

        scopeLife {
            allAppInfo.clear()

            binding.swipeRefreshLayout.isRefreshing = true
            binding.searchViewLayout.isEnabled = false
            binding.searchView.text = null

            com.drake.net.utils.withDefault {
                val allowAndFcm = ArrayList<AppInfo>()
                val onlyAllow = ArrayList<AppInfo>()
                val onlyFcm = ArrayList<AppInfo>()
                val noFcmNoAllow = ArrayList<AppInfo>()

                requireActivity().packageManager.getInstalledPackages(
                    PackageManager.GET_RECEIVERS or PackageManager.MATCH_DISABLED_COMPONENTS
                            or PackageManager.MATCH_UNINSTALLED_PACKAGES
                ).forEachIndexed { _, packageInfo ->
                    val appInfo = AppInfo(requireActivity().packageManager, packageInfo)
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
            requireActivity().putStringSet("config", "allowList", enabledList.toSet())
            requireActivity().sendBroadcast(Intent("${BuildConfig.APPLICATION_ID}.update.config"))
        } catch (e: Exception) {
            Log.e("updateConfig", e.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
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