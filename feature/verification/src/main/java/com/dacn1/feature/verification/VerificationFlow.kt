package com.dacn1.feature.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacn1.core.designsystem.components.BadgeType
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.components.StatusBadge
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.EkycSessionStatus
import com.dacn1.core.model.FinalDecision
import com.dacn1.core.model.ResultResponse
import com.dacn1.core.repository.EkycRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VerificationFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Sẵn sàng gửi phiên xác minh") }
    var result by remember { mutableStateOf<ResultResponse?>(null) }

    fun badgeTypeFor(decision: FinalDecision): BadgeType = when (decision) {
        FinalDecision.PASSED -> BadgeType.Success
        FinalDecision.REVIEW -> BadgeType.Warning
        FinalDecision.FAILED -> BadgeType.Error
    }

    ScreenContainer {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Xử lý xác minh", style = MaterialTheme.typography.headlineLarge)
            Text("Phiên: $sessionId", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Md),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Trạng thái xử lý", fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8))
                    Text(statusText, color = Color(0xFF334155))
                }
            }

            val localResult = result
            if (localResult != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                    ) {
                        StatusBadge(
                            text = "KẾT QUẢ: ${localResult.finalDecision}",
                            type = badgeTypeFor(localResult.finalDecision)
                        )

                        localResult.riskScore?.let { InfoRow("Điểm rủi ro", it.toString()) }
                        localResult.ocr?.let {
                            InfoRow("Họ tên OCR", it.fullName ?: "--")
                            InfoRow("Số giấy tờ OCR", it.idNumber ?: "--")
                        }
                        localResult.faceMatch?.let { InfoRow("Độ tương đồng khuôn mặt", it.similarity.toString()) }
                        localResult.liveness?.let { InfoRow("Điểm liveness", it.score.toString()) }

                        if (localResult.errors.isNotEmpty()) {
                            Text("Lỗi phát hiện:", fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                            localResult.errors.forEach { item ->
                                StatusBadge(text = "${item.code}: ${item.message}", type = BadgeType.Warning)
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                StatusBadge(text = errorMessage ?: "", type = BadgeType.Error)
            }

            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = when {
                    loading -> "Đang xử lý..."
                    result != null -> "Hoàn tất"
                    else -> "Gửi xác minh"
                },
                enabled = !loading,
                onClick = {
                    if (result != null) {
                        onDone()
                        return@PrimaryButton
                    }
                    loading = true
                    errorMessage = null
                    statusText = "Đang gửi phiên..."
                    scope.launch {
                        try {
                            val submit = repository.submitSession(sessionId)
                            if (submit.status == EkycSessionStatus.COLLECTING) {
                                errorMessage = "Chưa đủ dữ liệu. Vui lòng kiểm tra lại các bước tải lên."
                                return@launch
                            }

                            var attempts = 0
                            var done = false
                            while (!done && attempts < 10) {
                                attempts += 1
                                val status = repository.getStatus(sessionId)
                                statusText = "Đang xử lý ${status.currentStep} - ${status.progress}%"
                                if (status.status == EkycSessionStatus.PASSED ||
                                    status.status == EkycSessionStatus.FAILED ||
                                    status.status == EkycSessionStatus.REVIEW
                                ) {
                                    done = true
                                } else {
                                    delay(600)
                                }
                            }

                            result = repository.getResult(sessionId)
                            statusText = "Hoàn tất xử lý phiên."
                        } catch (ex: Exception) {
                            errorMessage = ex.message ?: "Không thể xử lý xác minh"
                        } finally {
                            loading = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.weight(1f)) {
            Text(value, fontWeight = FontWeight.Medium)
        }
    }
}
