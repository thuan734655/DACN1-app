package com.dacn1.feature.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.CreateSessionRequest
import com.dacn1.core.repository.EkycRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class OnboardingStep {
    SPLASH,
    WELCOME
}

@Composable
fun OnboardingFlowRoute(
    repository: EkycRepository,
    onSessionCreated: (String) -> Unit,
    onCompleted: () -> Unit
) {
    var step by remember { mutableStateOf(OnboardingStep.SPLASH) }
    var serviceChecking by remember { mutableStateOf(true) }
    var serviceError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        serviceChecking = true
        serviceError = null
        try {
            repository.getErrorCatalog()
            delay(500)
            step = OnboardingStep.WELCOME
        } catch (ex: Exception) {
            serviceError = ex.message ?: "Không kết nối được hệ thống"
        } finally {
            serviceChecking = false
        }
    }

    when (step) {
        OnboardingStep.SPLASH -> SplashScreen(serviceChecking = serviceChecking, serviceError = serviceError)
        OnboardingStep.WELCOME -> WelcomeScreen(
            repository = repository,
            onContinue = { sessionId ->
                onSessionCreated(sessionId)
                onCompleted()
            }
        )
    }
}

@Composable
private fun SplashScreen(serviceChecking: Boolean, serviceError: String?) {
    ScreenContainer {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "DACN1 Định danh điện tử", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(Spacing.Md))
            if (serviceChecking) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(Spacing.Sm))
                Text("Đang kết nối hệ thống xác thực...")
            } else if (serviceError != null) {
                Text(text = serviceError, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    repository: EkycRepository,
    onContinue: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ScreenContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Xác minh danh tính\ntrực tuyến", style = MaterialTheme.typography.headlineLarge)
            Text("Quá trình xác minh nhanh chóng, an toàn và bảo mật với các bước đơn giản")

            IntroStepCard(number = "1", title = "Giấy tờ tùy thân", subtitle = "Chụp rõ hai mặt CCCD/CMND/Hộ chiếu của bạn")
            IntroStepCard(number = "2", title = "NFC (Tùy chọn)", subtitle = "Quét chip CCCD để đối soát thêm với dữ liệu giấy tờ")
            IntroStepCard(number = "3", title = "Xác minh bằng khuôn mặt", subtitle = "Thực hiện động tác khuôn mặt để xác nhận đúng là bạn")

            Spacer(modifier = Modifier.height(Spacing.Xl))
            HintCard(
                title = "Hoàn tất trong 2-3 phút",
                subtitle = "Không cần tài liệu thêm hoặc tham khảo",
                borderColor = Color(0xFF9AE6B4),
                bgColor = Color(0xFFEFFCF3),
                dotColor = Color(0xFF16A34A)
            )
            if (errorMessage != null) {
                Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = if (submitting) "Đang tạo phiên..." else "Bắt đầu",
                enabled = !submitting,
                onClick = {
                    submitting = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val response = repository.createSession(
                                CreateSessionRequest(
                                    userRef = "USR_DEMO_001",
                                    flowVersion = "1.0",
                                    appVersion = "1.0.0"
                                )
                            )
                            val sId = response.sessionId
                            if (sId.isNullOrBlank()) {
                                errorMessage = "Lỗi: Server không trả về session_id"
                            } else {
                                onContinue(sId)
                            }
                        } catch (ex: Exception) {
                            errorMessage = ex.message ?: "Không thể tạo phiên eKYC"
                        } finally {
                            submitting = false
                        }
                    }
                }
            )
            Text(
                "Thông tin của bạn được bảo vệ bằng mã hóa end-to-end",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntroStepCard(number: String, title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFFDE7CF), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(number, fontWeight = FontWeight.Bold, color = Color(0xFFEA580C))
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HintCard(
    title: String,
    subtitle: String,
    borderColor: Color,
    bgColor: Color,
    dotColor: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(dotColor, RoundedCornerShape(999.dp))
            )
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
