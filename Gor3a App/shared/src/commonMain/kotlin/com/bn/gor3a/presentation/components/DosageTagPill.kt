package com.bn.gor3a.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.theme.FormCapsuleBg
import com.bn.gor3a.core.theme.FormCapsuleText
import com.bn.gor3a.core.theme.FormDropsBg
import com.bn.gor3a.core.theme.FormDropsText
import com.bn.gor3a.core.theme.FormInjectionBg
import com.bn.gor3a.core.theme.FormInjectionText
import com.bn.gor3a.core.theme.FormSuppositoryBg
import com.bn.gor3a.core.theme.FormSuppositoryText
import com.bn.gor3a.core.theme.FormSyrupBg
import com.bn.gor3a.core.theme.FormSyrupText
import com.bn.gor3a.core.theme.FormTabletBg
import com.bn.gor3a.core.theme.FormTabletText
import com.bn.gor3a.core.theme.FormTopicalBg
import com.bn.gor3a.core.theme.FormTopicalText

@Composable
fun DosageTagPill(
    form: String?,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val cleanForm = (form ?: "").trim().lowercase()

    val (bg, textCol, label, iconEmoji) = when {
        cleanForm.contains("drop") || cleanForm.contains("قطرة") || cleanForm.contains("قطره") ||
            cleanForm.contains("نقط") || cleanForm.contains("eye") || cleanForm.contains("ophth") ||
            cleanForm.contains("عين") -> {
            Quad(FormDropsBg, FormDropsText, strings.formDrops, "💧")
        }
        cleanForm.contains("syr") || cleanForm.contains("susp") || cleanForm.contains("شراب") || cleanForm.contains("معلق") -> {
            Quad(FormSyrupBg, FormSyrupText, strings.formSyrup, "🥄")
        }
        cleanForm.contains("tab") || cleanForm.contains("قرص") || cleanForm.contains("أقراص") || cleanForm.contains("اقراص") -> {
            Quad(FormTabletBg, FormTabletText, strings.formTablets, "💊")
        }
        cleanForm.contains("cap") || cleanForm.contains("كبسول") -> {
            Quad(FormCapsuleBg, FormCapsuleText, strings.formCapsules, "💊")
        }
        cleanForm.contains("topical") || cleanForm.contains("cream") || cleanForm.contains("ointment") ||
            cleanForm.contains("gel") || cleanForm.contains("lotion") || cleanForm.contains("دهان") ||
            cleanForm.contains("مرهم") || cleanForm.contains("كريم") || cleanForm.contains("جل") -> {
            Quad(FormTopicalBg, FormTopicalText, strings.formTopical, "🧴")
        }
        cleanForm.contains("supp") || cleanForm.contains("لبوس") -> {
            Quad(FormSuppositoryBg, FormSuppositoryText, strings.formSuppositories, "🛡️")
        }
        cleanForm.contains("inj") || cleanForm.contains("amp") || cleanForm.contains("vial") || cleanForm.contains("حقن") -> {
            Quad(FormInjectionBg, FormInjectionText, strings.formInjections, "💉")
        }
        cleanForm.contains("spray") || cleanForm.contains("inhal") || cleanForm.contains("بخاخ") -> {
            Quad(FormDropsBg, FormDropsText, strings.formSpray, "💨")
        }
        cleanForm.isNotBlank() -> {
            Quad(FormTabletBg, FormTabletText, form ?: strings.formPharmaceutical, "💊")
        }
        else -> {
            Quad(FormTabletBg, FormTabletText, strings.formMedicine, "💊")
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = 22.dp, max = 28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(0.5.dp, textCol.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = iconEmoji,
                fontSize = 11.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = textCol,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
