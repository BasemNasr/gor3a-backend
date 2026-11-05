package com.bn.gor3a.core.quota

import com.bn.gor3a.core.preferences.PlatformPreferences

object DeviceQuotaManager {
    private const val KEY_DEVICE_ID = "gor3a_persistent_device_uuid"
    private const val KEY_LOCAL_SCANS_COUNT = "gor3a_daily_scans_count"
    private const val KEY_LOCAL_SCANS_DATE = "gor3a_daily_scans_date"
    private const val KEY_MAX_DAILY_SCANS = "gor3a_max_daily_scans"

    const val DEFAULT_MAX_DAILY_SCANS = 5
    const val MAX_DAILY_SCANS = 5
    const val WHATSAPP_NUMBER = "+201064290486"
    const val WHATSAPP_LINK = (
        "https://wa.me/201064290486?text=" +
        "%D9%85%D8%B1%D8%AD%D8%A8%D8%A7%D9%8B%D8%8C%20%D8%A3%D8%B1%D9%8A%D8%AF%20%D8%A7%D9%84%D8%A7%D8%B4%D8%AA%D8%B1%D8%A7%D9%83%20%D9%81%D9%8A%20%D8%A7%D9%84%D9%86%D8%B3%D8%AE%D8%A9%20%D8%A7%D9%84%D9%83%D8%A7%D9%85%D9%84%D8%A9%20%D8%BA%D9%8A%D8%B1%20%D8%A7%D9%84%D9%85%D8%AD%D8%AF%D9%88%D8%AF%D8%A9%20%D9%84%D8%AA%D8%B7%D8%A8%D9%8A%D9%82%20%D8%AC%D8%B1%D8%B9%D8%A9"
    )

    /**
     * Retrieves or generates a permanent device identifier that is never cleared on user logout.
     * Prevents bypassing the daily limit by logging out and creating another account.
     */
    fun getOrCreateDeviceId(): String {
        val existing = PlatformPreferences.getString(KEY_DEVICE_ID, "")
        if (existing.isNotBlank()) {
            return existing
        }
        val part1 = (10000000..99999999).random().toString(16)
        val part2 = (10000000..99999999).random().toString(16)
        val part3 = (10000000..99999999).random().toString(16)
        val generated = "dev-$part1-$part2-$part3"
        PlatformPreferences.setString(KEY_DEVICE_ID, generated)
        return generated
    }

    fun getMaxDailyScans(): Int {
        val saved = PlatformPreferences.getString(KEY_MAX_DAILY_SCANS, "").toIntOrNull()
        return if (saved != null && saved > 0) saved else DEFAULT_MAX_DAILY_SCANS
    }

    fun getLocalScansUsed(): Int {
        return PlatformPreferences.getString(KEY_LOCAL_SCANS_COUNT, "0").toIntOrNull() ?: 0
    }

    fun getLocalScansRemaining(): Int {
        val used = getLocalScansUsed()
        val maxLimit = getMaxDailyScans()
        return (maxLimit - used).coerceAtLeast(0)
    }

    fun isQuotaExceeded(): Boolean {
        return getLocalScansRemaining() <= 0
    }

    fun recordSuccessfulScan() {
        val currentUsed = getLocalScansUsed()
        val maxLimit = getMaxDailyScans()
        val newUsed = (currentUsed + 1).coerceAtMost(maxLimit)
        PlatformPreferences.setString(KEY_LOCAL_SCANS_COUNT, newUsed.toString())
    }

    fun updateQuotaState(scansUsed: Int, date: String?, maxScans: Int? = null) {
        val storedDate = PlatformPreferences.getString(KEY_LOCAL_SCANS_DATE, "")
        if (date != null && date != storedDate) {
            // New day in Egypt: reset date and sync
            PlatformPreferences.setString(KEY_LOCAL_SCANS_DATE, date)
        }
        PlatformPreferences.setString(KEY_LOCAL_SCANS_COUNT, scansUsed.toString())
        if (maxScans != null && maxScans > 0) {
            PlatformPreferences.setString(KEY_MAX_DAILY_SCANS, maxScans.toString())
        }
    }

    fun resetToDefaultLimit() {
        PlatformPreferences.setString(KEY_MAX_DAILY_SCANS, DEFAULT_MAX_DAILY_SCANS.toString())
    }
}
