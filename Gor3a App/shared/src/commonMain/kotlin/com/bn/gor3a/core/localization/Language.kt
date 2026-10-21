package com.bn.gor3a.core.localization

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    ARABIC("ar", "العربية", true),
    ENGLISH("en", "English", false);

    fun toggle(): AppLanguage = if (this == ARABIC) ENGLISH else ARABIC
}
