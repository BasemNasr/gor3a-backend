package com.bn.gor3a.core.preferences

import platform.Foundation.NSUserDefaults

actual object PlatformPreferences {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        if (userDefaults.objectForKey(key) == null) {
            return defaultValue
        }
        return userDefaults.boolForKey(key)
    }

    actual fun setBoolean(key: String, value: Boolean) {
        userDefaults.setBool(value, forKey = key)
    }

    actual fun getString(key: String, defaultValue: String): String {
        return userDefaults.stringForKey(key) ?: defaultValue
    }

    actual fun setString(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
    }
}
