package com.bn.gor3a.presentation.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bn.gor3a.core.theme.Gor3aSafeGreen
import com.bn.gor3a.core.theme.Gor3aTealAccent
import com.bn.gor3a.core.theme.Gor3aTealDark
import com.bn.gor3a.core.theme.Gor3aTealPrimary
import com.bn.gor3a.data.remote.dto.UserDto
import com.bn.gor3a.presentation.viewmodel.AuthViewModel

enum class AuthMode {
    LOGIN,
    REGISTER,
    PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBottomSheet(
    viewModel: AuthViewModel,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var mode by remember(isLoggedIn) {
        mutableStateOf(if (isLoggedIn) AuthMode.PROFILE else AuthMode.REGISTER)
    }

    var phoneInput by remember { mutableStateOf(currentUser?.phone ?: "") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf(currentUser?.name ?: "") }
    var governorateInput by remember { mutableStateOf(currentUser?.governorate ?: "القاهرة") }
    var genderInput by remember { mutableStateOf(currentUser?.gender ?: "male") }
    var ageInput by remember { mutableStateOf(currentUser?.age?.toString() ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            when (mode) {
                AuthMode.PROFILE -> {
                    currentUser?.let { user ->
                        UserProfileView(
                            user = user,
                            onLogout = {
                                viewModel.logout()
                                passwordInput = ""
                                mode = AuthMode.REGISTER
                            },
                            onClose = onDismissRequest
                        )
                    } ?: run {
                        mode = AuthMode.REGISTER
                    }
                }

                AuthMode.REGISTER -> {
                    AuthRegisterView(
                        phone = phoneInput,
                        password = passwordInput,
                        name = nameInput,
                        governorate = governorateInput,
                        gender = genderInput,
                        age = ageInput,
                        governorates = uiState.governorates.ifEmpty {
                            listOf("القاهرة", "الجيزة", "الإسكندرية", "الدقهلية", "الشرقية", "المنوفية", "القليوبية", "الغربية", "البحيرة", "دمياط", "بورسعيد", "الإسماعيلية", "السويس", "كفر الشيخ", "الفيوم", "بني سويف", "المنيا", "أسيوط", "سوهاج", "قنا", "الأقصر", "أسوان")
                        },
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        onPhoneChange = { phoneInput = it; viewModel.clearMessages() },
                        onPasswordChange = { passwordInput = it; viewModel.clearMessages() },
                        onNameChange = { nameInput = it; viewModel.clearMessages() },
                        onGovChange = { governorateInput = it },
                        onGenderChange = { genderInput = it },
                        onAgeChange = { ageInput = it },
                        onSubmit = {
                            viewModel.register(
                                phone = phoneInput,
                                password = passwordInput,
                                name = nameInput,
                                governorate = governorateInput,
                                gender = genderInput,
                                age = ageInput.toIntOrNull(),
                                onSuccess = onDismissRequest
                            )
                        },
                        onSwitchToLogin = {
                            viewModel.clearMessages()
                            mode = AuthMode.LOGIN
                        }
                    )
                }

                AuthMode.LOGIN -> {
                    AuthLoginView(
                        phone = phoneInput,
                        password = passwordInput,
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        onPhoneChange = { phoneInput = it; viewModel.clearMessages() },
                        onPasswordChange = { passwordInput = it; viewModel.clearMessages() },
                        onSubmit = {
                            viewModel.login(
                                phone = phoneInput,
                                password = passwordInput,
                                onSuccess = onDismissRequest,
                                onRequiresRegistration = {
                                    mode = AuthMode.REGISTER
                                }
                            )
                        },
                        onSwitchToRegister = {
                            viewModel.clearMessages()
                            mode = AuthMode.REGISTER
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthRegisterView(
    phone: String,
    password: String,
    name: String,
    governorate: String,
    gender: String,
    age: String,
    governorates: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onGovChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchToLogin: () -> Unit
) {
    var showGovDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Title & Cloud Reassurance Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Gor3aTealPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("☁️", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "إنشاء حسابك الطبي السحابي",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealDark,
                        fontSize = 19.sp
                    )
                )
                Text(
                    text = "احفظ روشتاتك وتاريخك الدوائي في السحابة بأمان",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Info Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Gor3aSafeGreen.copy(alpha = 0.1f))
                .border(1.dp, Gor3aSafeGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "✨ بفضل الحساب السحابي، لن تفقد روشتاتك أبداً حتى لو قمت بتغيير هاتفك أو إعادة تثبيت التطبيق.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Gor3aTealDark,
                    lineHeight = 16.sp
                )
            )
        }

        // Phone Input
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("رقم الهاتف المحمول (01xxxxxxxxx)") },
            placeholder = { Text("مثال: 01012345678") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gor3aTealPrimary,
                focusedLabelColor = Gor3aTealPrimary
            )
        )

        // Password Input
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("كلمة المرور (6 أحرف على الأقل)") },
            placeholder = { Text("••••••••") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (passwordVisible) "👁️" else "🔒",
                        fontSize = 18.sp
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gor3aTealPrimary,
                focusedLabelColor = Gor3aTealPrimary
            )
        )

        // Name Input
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("الاسم بالكامل") },
            placeholder = { Text("مثال: أحمد محمد") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gor3aTealPrimary,
                focusedLabelColor = Gor3aTealPrimary
            )
        )

        // Gender Selection (ذكر / أنثى)
        Column {
            Text(
                text = "الجنس 👤",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isMale = gender != "female"
                val isFemale = gender == "female"

                // Male Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isMale) Gor3aTealPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = if (isMale) 2.dp else 1.dp,
                            color = if (isMale) Gor3aTealPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onGenderChange("male") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ذكر 👨",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isMale) FontWeight.Bold else FontWeight.Medium,
                                color = if (isMale) Gor3aTealPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (isMale) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓", color = Gor3aTealPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Female Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isFemale) Gor3aTealPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .border(
                            width = if (isFemale) 2.dp else 1.dp,
                            color = if (isFemale) Gor3aTealPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onGenderChange("female") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "أنثى 👩",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isFemale) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFemale) Gor3aTealPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (isFemale) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("✓", color = Gor3aTealPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Governorate Dropdown Trigger
        Column {
            Text(
                text = "المحافظة 📍",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { showGovDialog = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = governorate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text("▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Optional Age
        OutlinedTextField(
            value = age,
            onValueChange = onAgeChange,
            label = { Text("العمر (اختياري)") },
            placeholder = { Text("مثال: 32") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Error message
        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Submit Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isLoading) Gor3aTealPrimary.copy(alpha = 0.7f) else Gor3aTealPrimary)
                .clickable(enabled = !isLoading) { onSubmit() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "حفظ وإنشاء الحساب السحابي ☁️✓",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                )
            }
        }

        // Switch to login
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لديك حساب مسجل بالفعل؟",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "تسجيل الدخول",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Gor3aTealPrimary
                ),
                modifier = Modifier.clickable { onSwitchToLogin() }
            )
        }
    }

    // Governorates Dialog / Picker with Live Search
    if (showGovDialog) {
        var govSearchQuery by remember { mutableStateOf("") }
        val filteredGovs = remember(govSearchQuery, governorates) {
            if (govSearchQuery.isBlank()) governorates
            else {
                val q = normalizeArabicSearch(govSearchQuery)
                governorates.filter { gov ->
                    normalizeArabicSearch(gov).contains(q)
                }
            }
        }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showGovDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                Column {
                    // Header Row with Title & Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اختر المحافظة 📍",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Gor3aTealDark,
                                fontSize = 18.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { showGovDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search input field
                    OutlinedTextField(
                        value = govSearchQuery,
                        onValueChange = { govSearchQuery = it },
                        placeholder = { Text("ابحث عن محافظتك (مثال: أسيوط...)", fontSize = 13.sp) },
                        leadingIcon = { Text("🔍", fontSize = 15.sp) },
                        trailingIcon = {
                            if (govSearchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clickable { govSearchQuery = "" }
                                        .padding(8.dp)
                                ) {
                                    Text("✕", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gor3aTealPrimary,
                            focusedLabelColor = Gor3aTealPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Results or Empty State
                    if (filteredGovs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 26.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "لا توجد نتائج مطابقة لـ \"$govSearchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredGovs) { gov ->
                                val isSelected = gov == governorate
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Gor3aTealPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                        .clickable {
                                            onGovChange(gov)
                                            showGovDialog = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = gov,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Gor3aTealPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        if (isSelected) {
                                            Text("✓", color = Gor3aTealPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthLoginView(
    phone: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchToRegister: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Gor3aTealPrimary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("📱", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "تسجيل الدخول",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealDark,
                        fontSize = 19.sp
                    )
                )
                Text(
                    text = "أدخل رقم الهاتف وكلمة المرور للوصول لحسابك",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Phone Input
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("رقم الهاتف المحمول (01xxxxxxxxx)") },
            placeholder = { Text("مثال: 01012345678") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gor3aTealPrimary,
                focusedLabelColor = Gor3aTealPrimary
            )
        )

        // Password Input
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("كلمة المرور") },
            placeholder = { Text("••••••••") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Box(
                    modifier = Modifier
                        .clickable { passwordVisible = !passwordVisible }
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (passwordVisible) "👁️" else "🔒",
                        fontSize = 18.sp
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gor3aTealPrimary,
                focusedLabelColor = Gor3aTealPrimary
            )
        )

        // Error message
        if (!errorMessage.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Submit Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isLoading) Gor3aTealPrimary.copy(alpha = 0.7f) else Gor3aTealPrimary)
                .clickable(enabled = !isLoading) { onSubmit() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "تسجيل الدخول ومزامنة الروشتات 🔄",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                )
            }
        }

        // Switch to register
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "مستخدم جديد؟",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "إنشاء حساب سحابي جديد",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Gor3aTealPrimary
                ),
                modifier = Modifier.clickable { onSwitchToRegister() }
            )
        }
    }
}

@Composable
private fun UserProfileView(
    user: UserDto,
    onLogout: () -> Unit,
    onClose: () -> Unit
) {
    val genderLabel = if (user.gender == "female") "أنثى 👩" else "ذكر 👨"

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Avatar Circle
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Gor3aTealPrimary.copy(alpha = 0.15f))
                .border(2.dp, Gor3aTealPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = user.name.firstOrNull()?.toString() ?: "👤",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Gor3aTealPrimary
                )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Gor3aTealDark
                )
            )
            Text(
                text = "📍 محافظة ${user.governorate ?: "القاهرة"} • $genderLabel • ${user.phone}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Stats Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "☁️ الروشتات السحابية",
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = "${user.prescriptionsCount}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aTealPrimary
                    )
                )
            }
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔒 حالة الأمان",
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = "مشفر ومحمي",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Gor3aSafeGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Logout Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { onLogout() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "تسجيل الخروج من الحساب 🚪",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            )
        }

        // Close Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Gor3aTealPrimary)
                .clickable { onClose() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "إغلاق",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
    }
}

private fun normalizeArabicSearch(text: String): String {
    return text.trim().lowercase()
        .replace("أ", "ا")
        .replace("إ", "ا")
        .replace("آ", "ا")
        .replace("ة", "ه")
        .replace("ى", "ي")
        .replace("ء", "")
}


