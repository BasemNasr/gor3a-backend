package com.bn.gor3a.core.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS native back gesture can be wired or handled via custom UI controls
}
