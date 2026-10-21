package com.bn.gor3a.core.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val LocalAppLanguage = compositionLocalOf<MutableState<AppLanguage>> {
    mutableStateOf(AppLanguage.ARABIC)
}

val LocalStrings = compositionLocalOf<Strings> {
    ArabicStrings
}

@Composable
fun Gor3aLocalizationProvider(
    currentLanguageState: MutableState<AppLanguage>,
    content: @Composable () -> Unit
) {
    val language = currentLanguageState.value
    val strings = getStrings(language)
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppLanguage provides currentLanguageState,
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}
