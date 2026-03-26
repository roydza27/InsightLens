package com.royal.insightlens.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.royal.insightlens.R

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val darkModeToggle = view.findViewById<SwitchMaterial>(R.id.dark_mode_toggle)
        val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        // Initial state
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        darkModeToggle.isChecked = isDarkMode

        darkModeToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        view.findViewById<View>(R.id.settings_back_btn)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
}