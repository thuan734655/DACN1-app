package com.dacn1.feature.verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var statusText by remember { mutableStateOf("Sẵn sàng gửi phiên eKYC") }
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
            Text("Phiên: $sessionId")
            Text(statusText)

            val localResult = result
            if (localResult != null) {
                StatusBadge(
                    text = "KẾT QUẢ: ${localResult.finalDecision}",
                    type = badgeTypeFor(localResult.finalDecision)
                )
                localResult.riskScore?.let { Text("Điểm rủi ro: $it") }
                localResult.ocr?.let { Text("OCR: ${it.fullName ?: "--"} | ${it.idNumber ?: "--"}") }
                localResult.faceMatch?.let { Text("Độ tương đồng khuôn mặt: ${it.similarity}") }
                localResult.liveness?.let { Text("Điểm liveness: ${it.score}") }
                if (localResult.errors.isNotEmpty()) {
                    Text("Lỗi:")
                    localResult.errors.forEach { item ->
                        StatusBadge(text = "${item.code}: ${item.message}", type = BadgeType.Warning)
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
