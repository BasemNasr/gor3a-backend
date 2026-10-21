package com.bn.gor3a.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.LocalAppLanguage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.Gor3aTealAccent
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.core.theme.LocalThemeIsDark
import gor3a.shared.generated.resources.Res
import gor3a.shared.generated.resources.ic_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun TopHeaderBar(
    modifier: Modifier = Modifier,
    onTitleClick: () -> Unit = {}
) {
    val currentLang = LocalAppLanguage.current
    val isDark = LocalThemeIsDark.current
    val strings = LocalStrings.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Logo & Brand Title (Matching App Design .png)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onTitleClick() }
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Gor3aTealPrimary)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_logo),
                    contentDescription = "Gor3a Logo",
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (strings.isArabic) "جرعة" else "Gor3a",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark.value) Gor3aTealAccent else Gor3aTealDark,
                        fontSize = 22.sp,
                        lineHeight = 24.sp
                    )
                )
                Text(
                    text = if (strings.isArabic) "Gor3a" else "جرعة",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Gor3aTealPrimary,
                        fontSize = 13.sp
                    )
                )
            }
        }

        // Action controls: Dark mode toggle & Language Switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Language Switcher Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable {
                        currentLang.value = currentLang.value.toggle()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.languageSwitchText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealPrimary,
                        fontSize = 12.sp
                    )
                )
            }

            // Dark/Light Mode circular button matching design
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                    .clickable {
                        isDark.value = !isDark.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isDark.value) "☀️" else "🌙",
                    fontSize = 17.sp
                )
            }
        }
    }
}
