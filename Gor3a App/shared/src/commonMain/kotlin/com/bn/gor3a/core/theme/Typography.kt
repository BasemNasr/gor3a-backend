package com.bn.gor3a.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import gor3a.shared.generated.resources.Res
import gor3a.shared.generated.resources.baloo_bhaijaan
import org.jetbrains.compose.resources.Font

@Composable
fun getBalooFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.baloo_bhaijaan, FontWeight.Normal),
        Font(Res.font.baloo_bhaijaan, FontWeight.Medium),
        Font(Res.font.baloo_bhaijaan, FontWeight.SemiBold),
        Font(Res.font.baloo_bhaijaan, FontWeight.Bold)
    )
}

@Composable
fun getGor3aTypography(): Typography {
    val baloo = getBalooFontFamily()
    return Typography(
        displayLarge = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
        displayMedium = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
        displaySmall = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineLarge = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        headlineMedium = TextStyle(fontFamily = baloo, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        headlineSmall = TextStyle(fontFamily = baloo, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleLarge = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = baloo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = baloo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = baloo, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp)
    )
}
