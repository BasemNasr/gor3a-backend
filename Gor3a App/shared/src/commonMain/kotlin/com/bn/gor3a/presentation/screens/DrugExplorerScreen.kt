package com.bn.gor3a.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bn.gor3a.core.localization.LocalStrings
import com.bn.gor3a.core.navigation.PlatformBackHandler
import com.bn.gor3a.core.theme.Gor3aTealContainer
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.data.remote.Gor3aApiClient
import com.bn.gor3a.domain.model.DrugCardItem
import com.bn.gor3a.domain.model.ExtractedMedication
import com.bn.gor3a.presentation.components.BottomNavBar
import com.bn.gor3a.presentation.components.BottomTab
import com.bn.gor3a.presentation.components.DosageTagPill
import com.bn.gor3a.presentation.components.Gor3aSearchBar
import com.bn.gor3a.presentation.components.TopHeaderBar
import com.bn.gor3a.presentation.viewmodel.DrugExplorerViewModel
import kotlinx.coroutines.delay

@Composable
fun DrugExplorerScreen(
    viewModel: DrugExplorerViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToReminders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var previewImageItem by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        if (!state.shouldFocusSearch) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(state.shouldFocusSearch) {
        if (state.shouldFocusSearch) {
            delay(150)
            focusRequester.requestFocus()
            keyboardController?.show()
            viewModel.onSearchFocused()
        }
    }

    val formFilters = listOf(
        strings.filterAll,
        strings.filterTablets,
        strings.filterCapsules,
        strings.filterSyrup,
        strings.filterDrops,
        strings.filterTopical,
        strings.filterInjections,
        strings.filterSuppositories
    )

    Scaffold(
        topBar = {
            TopHeaderBar(onTitleClick = onNavigateToHome)
        },
        bottomBar = {
            BottomNavBar(
                selectedTab = BottomTab.SEARCH,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.HOME -> onNavigateToHome()
                        BottomTab.SEARCH -> { /* Current */ }
                        BottomTab.HISTORY -> onNavigateToHistory()
                        BottomTab.MORE -> onNavigateToReminders()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Header Title
            Column {
                Text(
                    text = strings.explorerTitle,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = strings.explorerSubtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            Gor3aSearchBar(
                query = state.query,
                onQueryChange = { viewModel.onQueryChanged(it) },
                focusRequester = focusRequester
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dosage Form Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                formFilters.forEach { form ->
                    val isSelected = Gor3aApiClient.mapFormToCanonical(state.selectedForm) == Gor3aApiClient.mapFormToCanonical(form)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Gor3aTealPrimary
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                1.dp,
                                if (isSelected) Gor3aTealPrimary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { viewModel.selectFormFilter(form) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = form,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Results Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.showingDrugs(state.searchResult.results.size, state.searchResult.total),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )

                // Quick Sort Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val nextSort = if (state.selectedSort == "relevance") "price_asc"
                            else if (state.selectedSort == "price_asc") "price_desc"
                            else "relevance"
                            viewModel.selectSort(nextSort)
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${strings.sortByLabel}: ${if (state.selectedSort == "price_asc") strings.sortByPriceLow else if (state.selectedSort == "price_desc") strings.sortByPriceHigh else strings.sortByRelevance}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Gor3aTealPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Gor3aTealPrimary)
                }
            } else if (state.searchResult.results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noDrugsFound,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.searchResult.results) { drugCard ->
                        DrugExplorerRow(
                            drugCard = drugCard,
                            onClick = { viewModel.selectDrug(drugCard) },
                            onImageClick = { url, name ->
                                previewImageItem = Pair(url, name)
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        // Details Bottom Sheet if a drug is clicked
        val details = state.selectedDrugDetails
        if (details != null) {
            PlatformBackHandler(enabled = true) {
                viewModel.dismissDetails()
            }
            val med = ExtractedMedication(
                rawText = details.drug.commercialNameEn,
                name = details.drug.commercialNameEn,
                commercialNameAr = details.drug.commercialNameAr,
                activeIngredient = details.drug.genericName,
                strength = details.drug.strength,
                dosageForm = details.drug.dosageForm,
                edaId = details.drug.id,
                priceEgp = details.pricing?.expectedPriceCurrentEgp ?: details.drug.priceEgp,
                clinicalIndication = details.drug.clinicalIndication,
                likelyDiagnosis = details.drug.likelyDiagnosis,
                imageUrl = details.drug.imageUrl
            )
            DrugDetailBottomSheet(
                medication = med,
                details = details,
                alternatives = state.selectedDrugAlternatives,
                isLoading = state.isDetailsLoading,
                onDismiss = { viewModel.dismissDetails() }
            )
        }

        // Full Screen Drug Packaging Image Dialog
        val preview = previewImageItem
        if (preview != null) {
            PlatformBackHandler(enabled = true) {
                previewImageItem = null
            }
            FullScreenDrugImageDialog(
                imageUrl = preview.first,
                drugName = preview.second,
                onDismiss = { previewImageItem = null }
            )
        }
    }
}

@Composable
private fun DrugExplorerRow(
    drugCard: DrugCardItem,
    onClick: () -> Unit,
    onImageClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val drug = drugCard.drug
    val pricing = drugCard.pricing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drug Packaging Thumbnail
            val img = drug.imageUrl
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable {
                        if (!img.isNullOrBlank()) {
                            onImageClick(img, drug.commercialNameEn)
                        } else {
                            onClick()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!img.isNullOrBlank()) {
                    AsyncImage(
                        model = img,
                        contentDescription = drug.commercialNameEn,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Text(text = "💊", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // English Commercial Name
                Text(
                    text = drug.commercialNameEn,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Arabic Commercial Name
                if (!drug.commercialNameAr.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = drug.commercialNameAr,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Gor3aTealDark,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dosage Tag Pill & Strength (Clean dedicated line - NEVER squished!)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DosageTagPill(form = drug.dosageForm)

                    val strength = drug.strength?.trim()
                    if (!strength.isNullOrBlank() && !strength.equals("unspecified", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = strength,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Active Generic / Ingredient (Omitted if missing or "unspecified", capped at 1 line with ellipsis)
                val generic = drug.genericName.trim()
                val isValidGeneric = generic.isNotBlank() &&
                    !generic.equals("unspecified", ignoreCase = true) &&
                    !generic.equals("none", ignoreCase = true)
                if (isValidGeneric) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = generic,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Price Column (Formatted without trailing .0, clean pill container)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                val price = pricing?.expectedPriceCurrentEgp ?: drug.priceEgp
                if (price != null && price > 0) {
                    val priceFormatted = if (price % 1.0 == 0.0) price.toLong().toString() else {
                        val s = price.toString()
                        if (s.contains('.')) {
                            val parts = s.split('.')
                            "${parts[0]}.${parts[1].take(2)}"
                        } else s
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Gor3aTealContainer.copy(alpha = 0.5f))
                            .border(0.5.dp, Gor3aTealPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = priceFormatted,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Gor3aTealDark,
                                    fontSize = 14.sp
                                )
                            )
                            Text(
                                text = strings.currencyEgp,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Gor3aTealPrimary,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                }

                val stripPrice = pricing?.expectedStripPriceEgp
                if (stripPrice != null && stripPrice > 0) {
                    val stripFormatted = if (stripPrice % 1.0 == 0.0) stripPrice.toLong().toString() else {
                        val s = stripPrice.toString()
                        if (s.contains('.')) {
                            val parts = s.split('.')
                            "${parts[0]}.${parts[1].take(2)}"
                        } else s
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${strings.stripPriceLabel}: $stripFormatted ${strings.currencyEgp}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenDrugImageDialog(
    imageUrl: String,
    drugName: String,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = drugName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = strings.edaAuthorityName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = drugName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = strings.officialPackagingImage,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}
