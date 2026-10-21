package com.bn.gor3a.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

val LocalThemeIsDark = compositionLocalOf { mutableStateOf(false) }

private val LightColorScheme = lightColorScheme(
    primary = Gor3aTealPrimary,
    onPrimary = Gor3aSurfaceLight,
    primaryContainer = Gor3aTealContainer,
    onPrimaryContainer = Gor3aTealDark,
    secondary = Gor3aTealAccent,
    onSecondary = Gor3aSurfaceLight,
    secondaryContainer = Gor3aCardTint,
    onSecondaryContainer = Gor3aTealDeepHeader,
    background = Gor3aBgLight,
    onBackground = Gor3aTextDark,
    surface = Gor3aSurfaceLight,
    onSurface = Gor3aTextDark,
    surfaceVariant = Gor3aCardTint,
    onSurfaceVariant = Gor3aTextMuted,
    outline = Gor3aBorderLight,
    error = Gor3aAlertRed,
    onError = Gor3aSurfaceLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Gor3aTealAccent,
    onPrimary = Gor3aBgDark,
    primaryContainer = Gor3aTealDark,
    onPrimaryContainer = Gor3aTealContainer,
    secondary = Gor3aTealAccent,
    onSecondary = Gor3aBgDark,
    secondaryContainer = Gor3aCardDark,
    onSecondaryContainer = Gor3aTealAccent,
    background = Gor3aBgDark,
    onBackground = Gor3aTextLight,
    surface = Gor3aSurfaceDark,
    onSurface = Gor3aTextLight,
    surfaceVariant = Gor3aCardDark,
    onSurfaceVariant = Gor3aTextMutedDark,
    outline = Gor3aBorderDark,
    error = Gor3aAlertRed,
    onError = Gor3aSurfaceLight,
)

val Gor3aShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun Gor3aTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val typography = getGor3aTypography()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = Gor3aShapes,
        content = content
    )
}
