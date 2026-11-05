package com.bn.gor3a.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.quota.DeviceQuotaManager
import com.bn.gor3a.core.theme.Gor3aTealPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotaExceededBottomSheet(
    onDismissRequest: () -> Unit,
    message: String? = null,
    whatsappNumber: String = DeviceQuotaManager.WHATSAPP_NUMBER,
    whatsappLink: String = DeviceQuotaManager.WHATSAPP_LINK,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE2E8F0))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Icon Badge (Shield / Alert)
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF2F2))
                    .border(1.5.dp, Color(0xFFFCA5A5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Title
            val maxLimit = DeviceQuotaManager.getMaxDailyScans()
            Text(
                text = "وصلت للحد الأقصى اليومي ($maxLimit روشتات)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Explanation Description
            Text(
                text = message ?: "لقد استنفدت الفحوصات المتاحة لهذا اليوم على هذا الجهاز وحسابك ($maxLimit من $maxLimit). يتجدد الرصيد المجاني تلقائياً في بداية اليوم التالي.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Unlimited Pro Version Card (Promo)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF0FDF4), Color(0xFFE6F4EA))
                        )
                    )
                    .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⭐", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تريد فحص عدد لا نهائي من الروشتات؟",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "احصل على النسخة الكاملة غير المحدودة مع سرعة معالجة فائقة ودعم فني مخصص للأطباء والصيادلة والمرضى.",
                        fontSize = 12.sp,
                        color = Color(0xFF15803D),
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // WhatsApp Direct Contact Button
                    Button(
                        onClick = {
                            try {
                                uriHandler.openUri(whatsappLink)
                            } catch (e: Exception) {
                                println("Failed to open WhatsApp: $e")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "💬", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تواصل عبر واتساب ($whatsappNumber)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Secondary Close Button
            OutlinedButton(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = "حسناً، فهمت",
                    fontSize = 14.sp,
                    color = Color(0xFF475569)
                )
            }
        }
    }
}
