package com.dacn1.feature.document

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.SecondaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.VerifyNfcRequest
import com.dacn1.core.repository.EkycRepository
import kotlinx.coroutines.launch

private enum class NfcStage {
    READY, FETCHING_KEY, SCANNING, READING, VERIFYING, PASSED, FAILED
}

@Composable
fun NfcFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onCompleted: () -> Unit,
    onExitToHome: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var nfcInfo by remember { mutableStateOf<String?>(null) }
    var nfcStage by remember { mutableStateOf(NfcStage.READY) }
    var nfcScanning by remember { mutableStateOf(false) }
    var nfcVerifying by remember { mutableStateOf(false) }
    var nfcKey by remember { mutableStateOf<String?>(null) }
    fun startNfcVerification() {
        nfcInfo = null
        nfcStage = NfcStage.FETCHING_KEY
        scope.launch {
            try {
                val response = repository.getNfcKey(sessionId)
                nfcKey = response.key
                nfcStage = NfcStage.SCANNING
                nfcScanning = true
            } catch (ex: Exception) {
                nfcStage = NfcStage.FAILED
                nfcInfo = "Không lấy được chìa khóa bảo mật NFC"
            }
        }
    }

    fun verifyNfcToken(token: String) {
        nfcScanning = false
        nfcStage = NfcStage.VERIFYING
        nfcVerifying = true
        scope.launch {
            try {
                val response = repository.verifyNfc(
                    sessionId = sessionId,
                    request = VerifyNfcRequest(
                        nfcToken = token,
                        idNumber = null
                    )
                )
                nfcVerifying = false
                nfcInfo = response.message
                nfcStage = if (response.passed) NfcStage.PASSED else NfcStage.FAILED
            } catch (ex: Exception) {
                nfcVerifying = false
                nfcStage = NfcStage.FAILED
                nfcInfo = ex.message ?: "Không thể xác thực NFC"
            }
        }
    }

    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val hostActivity = remember(context) { context.findActivity() }
    val nfcUnsupported = nfcAdapter == null
    val nfcDisabled = nfcAdapter?.isEnabled == false

    if (nfcScanning && nfcAdapter != null && hostActivity != null && nfcKey != null) {
        NfcReaderEffect(
            activity = hostActivity,
            nfcAdapter = nfcAdapter,
            nfcKey = nfcKey!!,
            coroutineScope = scope,
            onTagDiscovered = {
                nfcStage = NfcStage.READING
            },
            onTagRead = { token ->
                verifyNfcToken(token)
            },
            onError = { msg ->
                nfcScanning = false
                nfcStage = NfcStage.FAILED
                nfcInfo = msg
            }
        )
    }

    ScreenContainer {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                Text("Xác thực NFC", style = MaterialTheme.typography.headlineLarge)
                Text("Đặt CCCD có chip sát mặt lưng điện thoại để quét NFC.")
                if (nfcUnsupported) {
                    Text("Thiết bị không hỗ trợ NFC.", color = MaterialTheme.colorScheme.error)
                } else if (nfcDisabled) {
                    Text("NFC đang tắt. Vui lòng bật NFC trong cài đặt.", color = MaterialTheme.colorScheme.error)
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val statusText = when (nfcStage) {
                            NfcStage.READY -> "Sẵn sàng quét NFC"
                            NfcStage.FETCHING_KEY -> "Đang khởi tạo bảo mật và tải chìa khóa..."
                            NfcStage.SCANNING -> "Vui lòng đặt thẻ CCCD vào lưng điện thoại..."
                            NfcStage.READING -> "Đang đọc chip. VUI LÒNG GIỮ NGUYÊN THẺ..."
                            NfcStage.VERIFYING -> "Đang gửi server giải mã và đối soát..."
                            NfcStage.PASSED -> "Thông tin NFC khớp dữ liệu giấy tờ"
                            NfcStage.FAILED -> "Thông tin NFC không khớp"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (nfcStage == NfcStage.FETCHING_KEY || nfcStage == NfcStage.READING || nfcStage == NfcStage.VERIFYING) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            Text(statusText, fontWeight = FontWeight.SemiBold)
                        }
                        nfcInfo?.let { Text(it, color = Color(0xFF334155)) }
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Md),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Lưu ý", fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E))
                        Text("- Bật NFC trên điện thoại", color = Color(0xFF92400E))
                        Text("- Giữ thẻ cố định 2-3 giây khi quét", color = Color(0xFF92400E))
                        Text("- Không rút thẻ trong lúc đang xác minh", color = Color(0xFF92400E))
                    }
                }
            }

            SecondaryButton(
                text = "Quay lại",
                enabled = !nfcScanning && !nfcVerifying,
                onClick = onBack
            )

            if (nfcStage == NfcStage.PASSED) {
                PrimaryButton(
                    text = "Tiếp tục quay video",
                    onClick = onCompleted
                )
            } else if (nfcStage == NfcStage.FAILED) {
                PrimaryButton(
                    text = "Thử lại (Quét lại NFC)",
                    onClick = { startNfcVerification() }
                )
            } else {
                PrimaryButton(
                    text = if (nfcStage == NfcStage.FETCHING_KEY) "Đang khởi tạo..." else if (nfcScanning || nfcVerifying) "Đang xử lý..." else "Bắt đầu quét NFC",
                    enabled = nfcStage == NfcStage.READY && !nfcUnsupported && !nfcDisabled,
                    onClick = { startNfcVerification() }
                )
            }
        }
    }
}

@Composable
private fun NfcReaderEffect(
    activity: Activity,
    nfcAdapter: NfcAdapter,
    nfcKey: String,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onTagDiscovered: () -> Unit,
    onTagRead: (String) -> Unit,
    onError: (String) -> Unit
) {
    DisposableEffect(activity, nfcAdapter) {
        val callback = NfcAdapter.ReaderCallback { tag ->
            activity.runOnUiThread { onTagDiscovered() }
            coroutineScope.launch {
                try {
                    val token = com.dacn1.feature.document.nfc.JmrtdNfcReader.readTag(tag, nfcKey)
                    activity.runOnUiThread { onTagRead(token) }
                } catch (ex: Exception) {
                    activity.runOnUiThread { onError(ex.message ?: "Không đọc được dữ liệu NFC") }
                }
            }
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 100)
        }

        try {
            nfcAdapter.enableReaderMode(activity, callback, flags, options)
        } catch (_: Exception) {
            onError("Không thể bật chế độ đọc NFC")
        }

        onDispose {
            try {
                nfcAdapter.disableReaderMode(activity)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }
}

// Removed buildNfcToken

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
