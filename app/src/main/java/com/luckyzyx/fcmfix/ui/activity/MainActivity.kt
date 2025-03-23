package com.luckyzyx.fcmfix.ui.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import com.luckyzyx.fcmfix.R
import com.luckyzyx.fcmfix.databinding.ActivityMainBinding
import com.luckyzyx.fcmfix.utils.SPUtils.putBoolean
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private var isModuleActive = YukiHookAPI.Status.isXposedModuleActive

    private lateinit var binding: ActivityMainBinding
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_container) as NavHostFragment
        navController = navHostFragment.navController
        setSupportActionBar(binding.toolbar)
        setupActionBarWithNavController(navController)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        if (!isModuleActive || !prefs("config").isPreferencesAvailable) {
            MaterialAlertDialogBuilder(this).apply {
                setCancelable(false)
                setMessage("模块未激活,请重试!")
                setPositiveButton(android.R.string.ok) { _, _ -> exitProcess(0) }
                setOnDismissListener { exitProcess(0) }
                show()
            }
        } else {
            putBoolean("config", "init", true)
        }
    }
}