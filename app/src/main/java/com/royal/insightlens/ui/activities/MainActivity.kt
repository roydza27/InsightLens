package com.royal.insightlens.ui.activities

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)

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