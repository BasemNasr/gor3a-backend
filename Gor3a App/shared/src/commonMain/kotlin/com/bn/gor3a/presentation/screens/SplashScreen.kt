package com.bn.gor3a.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.LocalAppLanguage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.preferences.PlatformPreferences
import com.bn.gor3a.core.theme.Gor3aTealAccent
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealDeepHeader
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import gor3a.shared.generated.resources.Res
import gor3a.shared.generated.resources.ic_logo
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private const val PREF_HAS_ONBOARDED = "has_completed_onboarding"

@Composable
fun SplashScreen(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    val scale = remember { Animatable(0.85f) }

    // Check if this is the user's first launch
    val isFirstTime = remember {
        !PlatformPreferences.getBoolean(PREF_HAS_ONBOARDED, false)
    }

    var showContent by remember { mutableStateOf(!isFirstTime) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 650,
                easing = FastOutSlowInEasing
            )
        )
        showContent = true

        // If NOT first time, auto-navigate to Home after splash animation
        if (!isFirstTime) {
            delay(1200L)
            onGetStartedClick()
        }
    }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(Gor3aTealDeepHeader, Gor3aTealDark, Gor3aTealPrimary)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBg)
            .clickable(enabled = !isFirstTime) {
                // Tap anywhere to skip splash if already onboarded
                onGetStartedClick()
            }
            .padding(horizontal = 24.dp)
    ) {
        // Top Language Toggle with status bar padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { currentLang.value = currentLang.value.toggle() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = strings.languageSwitchText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Center Brand & Tagline
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .scale(scale.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(24.dp, RoundedCornerShape(32.dp), spotColor = Color.Black.copy(alpha = 0.45f))
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_logo),
                    contentDescription = "Gor3a Logo",
                    modifier = Modifier.size(122.dp).clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (strings.isArabic) "جرعة | Gor3a" else "Gor3a | جرعة",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 28.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.tagline,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Gor3aTealAccent,
                    fontSize = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = strings.subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Bottom CTA Button: SHOWN ON FIRST TIME ONLY!
        AnimatedVisibility(
            visible = isFirstTime && showContent,
            enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { it / 2 }),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Button(
                onClick = {
                    PlatformPreferences.setBoolean(PREF_HAS_ONBOARDED, true)
                    onGetStartedClick()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Gor3aTealPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = strings.scanCtaButton,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealPrimary,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}
