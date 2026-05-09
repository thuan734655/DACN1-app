package com.dacn1.feature.selfie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dacn1.core.designsystem.components.BadgeType
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.components.SecondaryButton
import com.dacn1.core.designsystem.components.StatusBadge
import com.dacn1.core.designsystem.components.StepProgress
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.UploadFileType
import com.dacn1.core.repository.EkycRepository
import kotlinx.coroutines.launch

@Composable
fun SelfieFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var uploaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ScreenContainer {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            StepProgress(currentStep = 1, totalSteps = 2)
            Text("Xác thực khuôn mặt selfie", style = MaterialTheme.typography.headlineLarge)
            Text("Đặt khuôn mặt vào khung, giữ máy ổn định và đủ ánh sáng.")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Xem trước camera", fontWeight = FontWeight.SemiBold)
                    Text("Selfie")
                }
            }

            if (uploaded) {
                StatusBadge(text = "FACE_FRAME_OK", type = BadgeType.Success)
            }
            if (errorMessage != null) {
                StatusBadge(text = errorMessage ?: "", type = BadgeType.Error)
            }

            Spacer(modifier = Modifier.weight(1f))
            SecondaryButton(text = "Chụp lại", enabled = !uploading, onClick = {
                uploaded = false
                errorMessage = null
            })
            PrimaryButton(
                text = if (uploaded) "Tiếp tục liveness" else if (uploading) "Đang tải lên..." else "Xác nhận selfie",
                enabled = !uploading,
                onClick = {
                    if (uploaded) {
                        onCompleted()
                    } else {
                        uploading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val response = repository.uploadFile(
                                    sessionId = sessionId,
                                    fileType = UploadFileType.SELFIE,
                                    localPath = "mock/selfie.jpg"
                                )
                                if (response.uploaded) {
                                    uploaded = true
                                } else {
                                    errorMessage = response.error?.message ?: "Tải lên selfie thất bại"
                                }
                            } catch (ex: Exception) {
                                errorMessage = ex.message ?: "Không thể tải lên selfie"
                            } finally {
                                uploading = false
                            }
                        }
                    }
                }
            )
        }
    }
}
