package com.example.mousetouch

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsController(context: Context) {
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun isAirModeEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_FEATURE_ENABLED, false)
    }

    fun setAirModeEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_FEATURE_ENABLED, enabled) }
    }

    companion object {
        private const val KEY_FEATURE_ENABLED = "air_mode_enabled"
    }
}
