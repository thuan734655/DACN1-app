package com.dacn1.feature.verification

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacn1.core.designsystem.components.BadgeType
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.components.StatusBadge
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.FinalizeResponse
import com.dacn1.core.repository.EkycRepository
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
    var finalizeResult by remember { mutableStateOf<FinalizeResponse?>(null) }

    ScreenContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Xử lý xác minh", style = MaterialTheme.typography.headlineLarge)
            Text("Phiên: $sessionId", color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Trạng thái xử lý
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Md),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Trạng thái xử lý",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1D4ED8)
                    )
                    Text(statusText, color = Color(0xFF334155))
                }
            }

            // Kết quả finalize
            val localResult = finalizeResult
            if (localResult != null) {
                FinalizeResultCard(result = localResult)
            }

            if (errorMessage != null) {
                StatusBadge(text = errorMessage ?: "", type = BadgeType.Error)
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = when {
                    loading -> "Đang xử lý..."
                    finalizeResult != null -> "Hoàn tất"
                    else -> "Gửi xác minh"
                },
                enabled = !loading,
                onClick = {
                    if (finalizeResult != null) {
                        onDone()
                        return@PrimaryButton
                    }
                    loading = true
                    errorMessage = null
                    statusText = "Đang tổng hợp kết quả cuối cùng..."
                    scope.launch {
                        try {
                            // Gọi trực tiếp API finalize để lấy kết quả tổng hợp
                            finalizeResult = repository.finalizeSession(
                                sessionId = sessionId,
                                consent = true
                            )
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

// ─── Card hiển thị kết quả finalize ──────────────────────────────────────────

@Composable
private fun FinalizeResultCard(result: FinalizeResponse) {
    val (decisionColor, decisionBg, decisionEmoji) = when (result.decision.uppercase()) {
        "APPROVED" -> Triple(Color(0xFF166534), Color(0xFFDCFCE7), "✅")
        "REJECTED", "FAILED" -> Triple(Color(0xFF991B1B), Color(0xFFFFE4E6), "❌")
        else -> Triple(Color(0xFF92400E), Color(0xFFFEF9C3), "⚠️")
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {

        // ── Banner quyết định ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(decisionBg, decisionBg.copy(alpha = 0.6f))
                    )
                )
                .padding(vertical = 24.dp, horizontal = Spacing.Md),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(decisionEmoji, fontSize = 40.sp)
                Text(
                    text = result.decision,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = decisionColor
                )
                Text(
                    text = "Trạng thái: ${result.status}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = decisionColor.copy(alpha = 0.8f)
                )
            }
        }

        // ── Điểm rủi ro ───────────────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Điểm rủi ro",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                val riskColor = when {
                    result.risk_score < 0.3 -> Color(0xFF166534)
                    result.risk_score < 0.6 -> Color(0xFF92400E)
                    else -> Color(0xFF991B1B)
                }
                Text(
                    text = "%.2f".format(result.risk_score),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = riskColor
                )
            }
        }

        // ── Tóm tắt các bước xác minh ─────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                Text(
                    "Tóm tắt xác minh",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D4ED8)
                )
                HorizontalDivider(color = Color(0xFFE2E8F0))
                SummaryRow(label = "OCR Giấy tờ", passed = result.summary.ocr_pass)
                SummaryRow(label = "Khớp khuôn mặt", passed = result.summary.face_match_pass)
                SummaryRow(label = "Liveness (Chống giả mạo)", passed = result.summary.liveness_pass)
            }
        }

        // ── Session ID ────────────────────────────────────────────────────────
        Text(
            text = "Session: ${result.session_id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryRow(label: String, passed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color(0xFF334155),
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (passed) Color(0xFF22C55E) else Color(0xFFEF4444))
            )
            Text(
                text = if (passed) "Đạt" else "Không đạt",
                fontWeight = FontWeight.Medium,
                color = if (passed) Color(0xFF166534) else Color(0xFF991B1B)
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
