package com.quant.terminal.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quant_prefs", Context.MODE_PRIVATE)

    var isFloatingEnabled: Boolean
        get() = prefs.getBoolean("floating_enabled", false)
        set(value) = prefs.edit().putBoolean("floating_enabled", value).apply()

    var manualServerUrl: String
        get() = prefs.getString("manual_server_url", "") ?: ""
        set(value) = prefs.edit().putString("manual_server_url", value).apply()
}
