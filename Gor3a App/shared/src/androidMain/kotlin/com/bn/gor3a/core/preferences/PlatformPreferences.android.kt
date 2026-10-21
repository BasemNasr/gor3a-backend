package com.bn.gor3a.core.preferences

import android.content.Context
import android.content.SharedPreferences

actual object PlatformPreferences {
    private const val PREFS_NAME = "gor3a_preferences"
    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences?.getBoolean(key, defaultValue) ?: defaultValue
    }

    actual fun setBoolean(key: String, value: Boolean) {
        sharedPreferences?.edit()?.putBoolean(key, value)?.apply()
    }

    actual fun getString(key: String, defaultValue: String): String {
        return sharedPreferences?.getString(key, defaultValue) ?: defaultValue
    }

    actual fun setString(key: String, value: String) {
        sharedPreferences?.edit()?.putString(key, value)?.apply()
    }
}
