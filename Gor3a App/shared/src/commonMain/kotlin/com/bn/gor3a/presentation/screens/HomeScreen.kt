package com.bn.gor3a.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bn.gor3a.data.remote.dto.BannerDto
import com.bn.gor3a.data.remote.dto.UserDto
import com.bn.gor3a.presentation.components.AuthBottomSheet
import com.bn.gor3a.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.delay
import com.bn.gor3a.core.localization.AppLanguage
import com.bn.gor3a.core.localization.LocalAppLanguage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.localization.localize
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.domain.model.SavedPrescription
import com.bn.gor3a.presentation.components.BottomNavBar
import com.bn.gor3a.presentation.components.BottomTab
import com.bn.gor3a.presentation.components.Gor3aSearchBar
import com.bn.gor3a.presentation.components.ScanPrescriptionCtaCard
import com.bn.gor3a.presentation.components.TopHeaderBar
import com.bn.gor3a.presentation.viewmodel.HomeViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    authViewModel: AuthViewModel? = null,
    onNavigateToScanner: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onSearchBarClick: () -> Unit = onNavigateToSearch,
    onNavigateToHistory: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onSelectPrescription: (SavedPrescription) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    val isArabic = currentLang.value == AppLanguage.ARABIC
    var showAuthSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentUser?.governorate) {
        viewModel.loadBanners(state.currentUser?.governorate)
    }

    if (showAuthSheet && authViewModel != null) {
        AuthBottomSheet(
            viewModel = authViewModel,
            onDismissRequest = { showAuthSheet = false }
        )
    }

    Scaffold(
        topBar = {
            TopHeaderBar(
                user = state.currentUser,
                onProfileClick = { showAuthSheet = true }
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = BottomTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.HOME -> { /* Already on Home */ }
                        BottomTab.SEARCH -> onNavigateToSearch()
                        BottomTab.HISTORY -> onNavigateToHistory()
                        BottomTab.MORE -> onNavigateToReminders()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Promotional Banner Slider (Admin-Controlled & Auto-Scrolling) or Default Welcome Card
            item {
                if (state.banners.isNotEmpty()) {
                    HomeBannerSlider(
                        banners = state.banners,
                        onBannerClick = { banner ->
                            if (banner.actionType.equals("drug", ignoreCase = true)) {
                                val query = banner.actionTarget ?: ""
                                if (query.isNotBlank()) {
                                    viewModel.onSearchQueryChanged(query)
                                    onNavigateToSearch()
                                }
                            }
                        }
                    )
                } else {
                    HomeDefaultWelcomeCard(user = state.currentUser)
                }
            }

            // 2. Cloud Registration Banner (shown when user is not logged in)
            if (state.currentUser == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0F766E), Color(0xFF00897B))
                                )
                            )
                            .clickable { showAuthSheet = true }
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("☁️", fontSize = 16.sp)
                                    Text(
                                        text = if (isArabic) "احفظ روشتاتك في ملفك الطبي السحابي" else "Save Prescriptions in Cloud",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isArabic)
                                        "سجل حسابك مجاناً لتسترجع روشتاتك وتاريخك الدوائي عند تغيير هاتفك."
                                    else
                                        "Register for free to backup your prescriptions & medication history.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isArabic) "تسجيل الآن" else "Sign In",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Gor3aTealDark,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Search Bar
            item {
                Gor3aSearchBar(
                    query = state.searchQuery,
                    onQueryChange = {
                        viewModel.onSearchQueryChanged(it)
                        onSearchBarClick()
                    },
                    readOnly = true,
                    onSearchBarClick = onSearchBarClick,
                    onFilterClick = onSearchBarClick
                )
            }

            // 3. Scan Hero Action CTA Card ("صوّر الروشتة الآن واكتشف علاجك")
            item {
                ScanPrescriptionCtaCard(
                    onScanClick = onNavigateToScanner
                )
            }

            // 4. Section Title: "روشتاتك السابقة" (Your Previous Prescriptions)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.previousPrescriptions,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = if (isArabic) "عرض الكل ›" else "View All ›",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gor3aTealPrimary,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.clickable { onNavigateToHistory() }
                    )
                }
            }

            // 5. List of Previous Prescriptions (Matching App Design .png)
            if (state.recentPrescriptions.isEmpty()) {
                item {
                    HomeEmptyPrescriptionsCard(
                        onScanNow = onNavigateToScanner
                    )
                }
            } else {
                items(state.recentPrescriptions) { rawRx ->
                    val rx = rawRx.localize(isArabic)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFF00897B).copy(alpha = 0.08f))
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                            .clickable { onSelectPrescription(rx) }
                            .padding(16.dp)
                    ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Prescription Document Icon in Mint Square
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFE0F2F1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val strokeW = 1.8.dp.toPx()
                                    val tealColor = Color(0xFF00897B)

                                    // Document outline with folded corner
                                    val docW = size.width * 0.75f
                                    val docH = size.height * 0.9f
                                    val startX = (size.width - docW) / 2
                                    val startY = (size.height - docH) / 2

                                    drawRoundRect(
                                        color = tealColor,
                                        topLeft = Offset(startX, startY),
                                        size = Size(docW, docH),
                                        cornerRadius = CornerRadius(4.dp.toPx()),
                                        style = Stroke(width = strokeW)
                                    )

                                    // Document text lines
                                    val lineStartX = startX + docW * 0.22f
                                    val lineEndX = startX + docW * 0.78f
                                    val line1Y = startY + docH * 0.35f
                                    val line2Y = startY + docH * 0.55f
                                    val line3Y = startY + docH * 0.75f

                                    drawLine(tealColor, Offset(lineStartX, line1Y), Offset(lineEndX, line1Y), strokeW, StrokeCap.Round)
                                    drawLine(tealColor, Offset(lineStartX, line2Y), Offset(lineEndX, line2Y), strokeW, StrokeCap.Round)
                                    drawLine(tealColor, Offset(lineStartX, line3Y), Offset(lineStartX + docW * 0.38f, line3Y), strokeW, StrokeCap.Round)
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = rx.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = rx.date,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                )
                                if (!rx.diagnosis.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = rx.diagnosis,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Chevron arrow ›
                        Text(
                            text = "›",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Custom medical prescription sheet illustration matching the graphic in App Design .png:
 * White sheet with Rx symbol, horizontal lines, and camera scan icon in teal badge.
 */
@Composable
private fun RxPrescriptionIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .rotate(-4f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sheetW = size.width * 0.88f
            val sheetH = size.height * 0.94f
            val x = (size.width - sheetW) / 2
            val y = (size.height - sheetH) / 2

            // White paper sheet
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x, y),
                size = Size(sheetW, sheetH),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            // Border
            drawRoundRect(
                color = Color(0xFFB2DFDB),
                topLeft = Offset(x, y),
                size = Size(sheetW, sheetH),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            val tealPrimary = Color(0xFF00897B)
            val lineTeal = Color(0xFFB2DFDB)

            // Draw "Rx" in top corner
            val rxX = x + sheetW * 0.18f
            val rxY = y + sheetH * 0.26f
            val strokeW = 1.8.dp.toPx()

            // "R" vertical stem
            drawLine(tealPrimary, Offset(rxX, rxY - 8.dp.toPx()), Offset(rxX, rxY + 8.dp.toPx()), strokeW, StrokeCap.Round)
            // "R" loop
            val loopPath = Path().apply {
                moveTo(rxX, rxY - 8.dp.toPx())
                cubicTo(rxX + 7.dp.toPx(), rxY - 8.dp.toPx(), rxX + 7.dp.toPx(), rxY, rxX, rxY)
            }
            drawPath(loopPath, tealPrimary, style = Stroke(width = strokeW))
            // "R" leg
            drawLine(tealPrimary, Offset(rxX + 3.dp.toPx(), rxY), Offset(rxX + 7.dp.toPx(), rxY + 8.dp.toPx()), strokeW, StrokeCap.Round)

            // Horizontal lines
            val lineStartX = rxX + 14.dp.toPx()
            val lineEndX = x + sheetW * 0.84f
            drawLine(lineTeal, Offset(lineStartX, rxY - 4.dp.toPx()), Offset(lineEndX, rxY - 4.dp.toPx()), 1.4.dp.toPx(), StrokeCap.Round)
            drawLine(lineTeal, Offset(lineStartX, rxY + 4.dp.toPx()), Offset(lineEndX - 8.dp.toPx(), rxY + 4.dp.toPx()), 1.4.dp.toPx(), StrokeCap.Round)

            // Lower lines
            val low1Y = y + sheetH * 0.48f
            val low2Y = y + sheetH * 0.62f
            drawLine(lineTeal, Offset(x + sheetW * 0.16f, low1Y), Offset(x + sheetW * 0.84f, low1Y), 1.4.dp.toPx(), StrokeCap.Round)
            drawLine(lineTeal, Offset(x + sheetW * 0.16f, low2Y), Offset(x + sheetW * 0.6f, low2Y), 1.4.dp.toPx(), StrokeCap.Round)

            // Camera scan badge in bottom right corner
            val badgeRadius = 14.dp.toPx()
            val badgeCenter = Offset(x + sheetW * 0.76f, y + sheetH * 0.78f)

            drawCircle(
                color = Color(0xFFE0F2F1),
                radius = badgeRadius,
                center = badgeCenter
            )
            drawCircle(
                color = tealPrimary,
                radius = badgeRadius,
                center = badgeCenter,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Mini camera inside badge
            val camW = 12.dp.toPx()
            val camH = 8.5.dp.toPx()
            drawRoundRect(
                color = tealPrimary,
                topLeft = Offset(badgeCenter.x - camW / 2, badgeCenter.y - camH / 2),
                size = Size(camW, camH),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = tealPrimary,
                radius = 2.2.dp.toPx(),
                center = badgeCenter,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}

/**
 * Empty view displayed under "Previous Prescriptions" on the Home tab when no prescriptions are saved,
 * matching the visual aesthetic and CTA button from the History tab.
 */
@Composable
private fun HomeEmptyPrescriptionsCard(
    onScanNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Illustration badge
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0F2F1)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(34.dp)) {
                    val strokeW = 2.2.dp.toPx()
                    val color = Gor3aTealPrimary

                    val docW = size.width * 0.65f
                    val docH = size.height * 0.85f
                    val startX = (size.width - docW) / 2
                    val startY = (size.height - docH) / 2

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(startX, startY),
                        size = Size(docW, docH),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(strokeW)
                    )
                    drawLine(color, Offset(startX + docW * 0.2f, startY + docH * 0.3f), Offset(startX + docW * 0.8f, startY + docH * 0.3f), strokeW, StrokeCap.Round)
                    drawLine(color, Offset(startX + docW * 0.2f, startY + docH * 0.5f), Offset(startX + docW * 0.8f, startY + docH * 0.5f), strokeW, StrokeCap.Round)
                    drawLine(color, Offset(startX + docW * 0.2f, startY + docH * 0.7f), Offset(startX + docW * 0.55f, startY + docH * 0.7f), strokeW, StrokeCap.Round)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = strings.noSavedPrescriptions,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.noSavedPrescriptionsDesc,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onScanNow,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gor3aTealPrimary),
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text(
                    text = strings.scanPrescriptionNow,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun HomeDefaultWelcomeCard(
    user: UserDto? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val greeting = if (user != null) {
        if (strings.isArabic) "مرحباً يا ${user.name} 👋" else "Welcome ${user.name} 👋"
    } else {
        strings.welcomeUser
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFEDF8F6))
            .border(1.dp, Color(0xFFD0ECE7), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00897B),
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = strings.bannerTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        fontSize = 19.sp,
                        lineHeight = 24.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.bannerDesc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            RxPrescriptionIllustration(modifier = Modifier.size(76.dp, 84.dp))
        }
    }
}

@Composable
fun HomeBannerSlider(
    banners: List<BannerDto>,
    onBannerClick: (BannerDto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })
    val uriHandler = LocalUriHandler.current

    // Auto-scroll every 4 seconds continuously (pauses when user touches or swipes)
    LaunchedEffect(pagerState, banners.size) {
        if (banners.size > 1) {
            while (true) {
                delay(5000)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % banners.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val banner = banners[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFEDF8F6))
                    .border(1.dp, Color(0xFFD0ECE7), RoundedCornerShape(22.dp))
                    .clickable {
                        onBannerClick(banner)
                        val target = banner.actionTarget
                        if (!target.isNullOrBlank()) {
                            try {
                                when (banner.actionType.lowercase()) {
                                    "whatsapp", "url" -> uriHandler.openUri(target)
                                    "phone" -> uriHandler.openUri("tel:$target")
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        val badgeText = when (banner.actionType.lowercase()) {
                            "whatsapp" -> "عرض واتساب 🟢"
                            "phone" -> "خط ساخن 📞"
                            "url" -> "عرض حصري 🌐"
                            else -> "إعلان مميز ✨"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00897B).copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00897B),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = banner.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!banner.subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = banner.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFD0ECE7), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (banner.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = banner.imageUrl,
                                contentDescription = banner.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            RxPrescriptionIllustration(modifier = Modifier.size(56.dp, 62.dp))
                        }
                    }
                }
            }
        }

        // Indicator Dots
        if (banners.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(banners.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(5.dp)
                            .width(if (isSelected) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) Gor3aTealPrimary else Color(0xFFCBD5E1))
                    )
                }
            }
        }
    }
}

