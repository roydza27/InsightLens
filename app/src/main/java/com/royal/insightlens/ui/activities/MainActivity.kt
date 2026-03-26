package com.royal.insightlens.ui.activities

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.ExperimentalGetImage
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.royal.insightlens.R
import com.royal.insightlens.ui.fragments.HistoryFragment
import com.royal.insightlens.ui.fragments.HomeFragment
import com.royal.insightlens.ui.fragments.ScanFragment
import com.royal.insightlens.ui.fragments.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    @OptIn(ExperimentalGetImage::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        val sharedPrefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        
        // 1️⃣ Enable edge-to-edge handling
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.main)
        bottomNav = findViewById(R.id.bottom_nav)

        // 2️⃣ Apply window inset padding to the root view
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Set padding for status bar at the top and navigation bar at the bottom
            view.setPadding(0, statusBarInsets.top, 0, navigationBarInsets.bottom)
            
            insets
        }

        // Load HomeFragment on first launch
        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), "HOME")
            bottomNav.selectedItemId = R.id.menu_home
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    loadFragment(HomeFragment(), "HOME")
                    true
                }
                R.id.menu_scan -> {
                    loadFragment(fragment = ScanFragment(), tag = "SCAN")
                    true
                }
                R.id.menu_history -> {
                    loadFragment(fragment = HistoryFragment(), tag = "HISTORY")
                    true
                }
                R.id.menu_settings -> {
                    loadFragment(SettingsFragment(), "SETTINGS")
                    true
                }
                else -> {
                    false
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null && existing.isVisible) return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }
}