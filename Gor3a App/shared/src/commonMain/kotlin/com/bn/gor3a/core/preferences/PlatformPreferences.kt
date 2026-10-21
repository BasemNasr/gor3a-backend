package com.bn.gor3a.core.preferences

expect object PlatformPreferences {
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun setBoolean(key: String, value: Boolean)
    fun getString(key: String, defaultValue: String = ""): String
    fun setString(key: String, value: String)
}
