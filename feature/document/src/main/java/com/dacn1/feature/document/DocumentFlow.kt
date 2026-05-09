package com.dacn1.feature.document

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.dacn1.core.designsystem.components.BadgeType
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.components.SecondaryButton
import com.dacn1.core.designsystem.components.StatusBadge
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.UploadFileType
import com.dacn1.core.model.VerifyNfcRequest
import com.dacn1.core.repository.EkycRepository
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.launch

private enum class DocumentStep {
    GUIDE,
    FRONT_CAPTURE,
    BACK_CAPTURE,
    REVIEW,
    NFC_VERIFY
}

private enum class NfcStage {
    READY,
    SCANNING,
    VERIFYING,
    PASSED,
    FAILED
}

@Composable
fun DocumentFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onCompleted: () -> Unit,
    onNfcPassed: () -> Unit,
    onExitToHome: () -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(DocumentStep.GUIDE) }
    var frontUploaded by remember { mutableStateOf(false) }
    var backUploaded by remember { mutableStateOf(false) }
    var frontImagePath by remember { mutableStateOf<String?>(null) }
    var backImagePath by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var nfcInfo by remember { mutableStateOf<String?>(null) }
    var nfcStage by remember { mutableStateOf(NfcStage.READY) }
    var nfcScanning by remember { mutableStateOf(false) }
    var nfcVerifying by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val warnings = remember { mutableStateListOf<String>() }

    fun upload(
        type: UploadFileType,
        path: String,
        onSuccess: () -> Unit,
        onQualityFailed: () -> Unit = {}
    ) {
        uploading = true
        lastError = null
        scope.launch {
            try {
                val response = repository.uploadFile(
                    sessionId = sessionId,
                    fileType = type,
                    localPath = path
                )
                val qualityPassed = isDocumentQualityPassed(response.qualityCheck)
                if (response.uploaded && qualityPassed) {
                    response.qualityCheck?.let {
                        warnings.clear()
                        warnings.add(it)
                    }
                    onSuccess()
                } else if (response.uploaded) {
                    lastError = "Ảnh chưa đạt tiêu chuẩn, vui lòng chụp lại."
                    onQualityFailed()
                } else {
                    lastError = response.error?.message ?: "Tải lên thất bại"
                }
            } catch (ex: Exception) {
                lastError = ex.message ?: "Không thể tải lên"
            } finally {
                uploading = false
            }
        }
    }

    fun startNfcVerification() {
        nfcInfo = null
        nfcStage = NfcStage.SCANNING
        nfcScanning = true
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

    ScreenContainer {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            when (step) {
                DocumentStep.GUIDE -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Md)
                    ) {
                        Text("Hướng dẫn chụp ảnh", style = MaterialTheme.typography.headlineLarge)
                        Text("Để xác minh danh tính, vui lòng chụp rõ CCCD/CMND.")
                        Text("Yêu cầu chụp ảnh:", fontWeight = FontWeight.SemiBold)
                        GuideRuleCard("Đủ ánh sáng", "Tìm nơi có ánh sáng tự nhiên hoặc đèn sáng")
                        GuideRuleCard("Không chói sáng", "Tránh chụp với ánh sáng trực tiếp hoặc phản chiếu")
                        GuideRuleCard("Không cắt góc", "Toàn bộ giấy tờ phải hiển thị trong khung hình")
                    }
                    PrimaryButton(text = "Mở camera", onClick = { step = DocumentStep.FRONT_CAPTURE })
                }

                DocumentStep.FRONT_CAPTURE -> {
                    Text("Chụp mặt trước", style = MaterialTheme.typography.headlineLarge)
                    if (frontImagePath.isNullOrBlank()) {
                        IdCardCameraPreview(onImageCaptureReady = { imageCapture = it })
                    } else {
                        CapturedImagePreview(imagePath = frontImagePath!!)
                    }
                    CaptureHintCard()
                    if (frontImagePath != null) {
                        StatusBadge(text = "Đã chụp mặt trước", type = BadgeType.Success)
                    }
                    if (lastError != null) Text(lastError ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        Box(modifier = Modifier.weight(1f)) {
                            SecondaryButton(
                                text = if (capturing) "Đang chụp..." else if (frontImagePath == null) "Chụp ảnh" else "Chụp lại",
                                enabled = !uploading && !capturing,
                                onClick = {
                                    if (frontImagePath != null) {
                                        frontImagePath = null
                                        lastError = null
                                        nfcInfo = null
                                        nfcStage = NfcStage.READY
                                        return@SecondaryButton
                                    }
                                    captureDocumentImage(
                                        context = context,
                                        executor = mainExecutor,
                                        imageCapture = imageCapture,
                                        filePrefix = "id_front",
                                        onStart = {
                                            capturing = true
                                            lastError = null
                                            nfcInfo = null
                                            nfcStage = NfcStage.READY
                                        },
                                        onSaved = {
                                            frontImagePath = it
                                            capturing = false
                                            upload(
                                                type = UploadFileType.ID_FRONT,
                                                path = it,
                                                onSuccess = {
                                                    frontUploaded = true
                                                    step = DocumentStep.BACK_CAPTURE
                                                },
                                                onQualityFailed = {
                                                    frontUploaded = false
                                                    frontImagePath = null
                                                }
                                            )
                                        },
                                        onError = {
                                            capturing = false
                                            lastError = it
                                        }
                                    )
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            PrimaryButton(
                                text = if (uploading) "Đang kiểm tra..." else "Tự động kiểm tra",
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                }

                DocumentStep.BACK_CAPTURE -> {
                    Text("Chụp mặt sau", style = MaterialTheme.typography.headlineLarge)
                    if (backImagePath.isNullOrBlank()) {
                        IdCardCameraPreview(onImageCaptureReady = { imageCapture = it })
                    } else {
                        CapturedImagePreview(imagePath = backImagePath!!)
                    }
                    CaptureHintCard()
                    if (backImagePath != null) {
                        StatusBadge(text = "Đã chụp mặt sau", type = BadgeType.Success)
                    }
                    if (lastError != null) Text(lastError ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        Box(modifier = Modifier.weight(1f)) {
                            SecondaryButton(
                                text = if (capturing) "Đang chụp..." else if (backImagePath == null) "Chụp ảnh" else "Chụp lại",
                                enabled = !uploading && !capturing,
                                onClick = {
                                    if (backImagePath != null) {
                                        backImagePath = null
                                        lastError = null
                                        nfcInfo = null
                                        nfcStage = NfcStage.READY
                                        return@SecondaryButton
                                    }
                                    captureDocumentImage(
                                        context = context,
                                        executor = mainExecutor,
                                        imageCapture = imageCapture,
                                        filePrefix = "id_back",
                                        onStart = {
                                            capturing = true
                                            lastError = null
                                            nfcInfo = null
                                            nfcStage = NfcStage.READY
                                        },
                                        onSaved = {
                                            backImagePath = it
                                            capturing = false
                                            upload(
                                                type = UploadFileType.ID_BACK,
                                                path = it,
                                                onSuccess = {
                                                    backUploaded = true
                                                    step = DocumentStep.REVIEW
                                                },
                                                onQualityFailed = {
                                                    backUploaded = false
                                                    backImagePath = null
                                                }
                                            )
                                        },
                                        onError = {
                                            capturing = false
                                            lastError = it
                                        }
                                    )
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            PrimaryButton(
                                text = if (uploading) "Đang kiểm tra..." else "Tự động kiểm tra",
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                }

                DocumentStep.REVIEW -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Md)
                    ) {
                        Text("Xem lại giấy tờ", style = MaterialTheme.typography.headlineLarge)
                        ReviewRow(
                            title = "Mặt trước",
                            passed = frontUploaded,
                            imagePath = frontImagePath
                        )
                        ReviewRow(
                            title = "Mặt sau",
                            passed = backUploaded,
                            imagePath = backImagePath
                        )
                        if (lastError != null) Text(lastError ?: "", color = MaterialTheme.colorScheme.error)
                        if (nfcInfo != null) Text(nfcInfo ?: "", color = Color(0xFF0F766E))
                    }
                    SecondaryButton(
                        text = "Chụp lại",
                        onClick = {
                            nfcInfo = null
                            nfcStage = NfcStage.READY
                            step = DocumentStep.FRONT_CAPTURE
                        }
                    )
                    SecondaryButton(
                        text = "Xác thực qua NFC",
                        enabled = frontUploaded && backUploaded,
                        onClick = {
                            nfcInfo = null
                            nfcStage = NfcStage.READY
                            step = DocumentStep.NFC_VERIFY
                        }
                    )
                    PrimaryButton(
                        text = "Tiếp tục selfie",
                        enabled = frontUploaded && backUploaded,
                        onClick = {
                            nfcInfo = null
                            nfcStage = NfcStage.READY
                            onCompleted()
                        }
                    )
                }

                DocumentStep.NFC_VERIFY -> {
                    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
                    val hostActivity = remember(context) { context.findActivity() }
                    val nfcUnsupported = nfcAdapter == null
                    val nfcDisabled = nfcAdapter?.isEnabled == false

                    if (nfcScanning && nfcAdapter != null && hostActivity != null) {
                        NfcReaderEffect(
                            activity = hostActivity,
                            nfcAdapter = nfcAdapter,
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
                                    NfcStage.SCANNING -> "Đang quét NFC trên thiết bị..."
                                    NfcStage.VERIFYING -> "Đang gửi server giải mã và đối soát..."
                                    NfcStage.PASSED -> "Thông tin NFC khớp dữ liệu giấy tờ"
                                    NfcStage.FAILED -> "Thông tin NFC không khớp"
                                }
                                Text(statusText, fontWeight = FontWeight.SemiBold)
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
                        onClick = {
                            nfcInfo = null
                            nfcStage = NfcStage.READY
                            step = DocumentStep.REVIEW
                        }
                    )

                    if (nfcStage == NfcStage.PASSED) {
                        PrimaryButton(
                            text = "Tiếp tục quay video",
                            onClick = onNfcPassed
                        )
                    } else if (nfcStage == NfcStage.FAILED) {
                        PrimaryButton(
                            text = "Về màn hình chính",
                            onClick = onExitToHome
                        )
                    } else {
                        PrimaryButton(
                            text = if (nfcScanning || nfcVerifying) "Đang xử lý..." else "Bắt đầu quét NFC",
                            enabled = !nfcScanning && !nfcVerifying && !nfcUnsupported && !nfcDisabled,
                            onClick = { startNfcVerification() }
                        )
                    }
                }
            }
        }
    }
}

private fun isDocumentQualityPassed(qualityCheck: String?): Boolean {
    if (qualityCheck.isNullOrBlank()) return false
    val normalized = qualityCheck.uppercase()
    return normalized == "DOC_FRAME_OK" || normalized == "DOC_BACK_OK" || normalized.endsWith("_OK")
}

@Composable
private fun NfcReaderEffect(
    activity: Activity,
    nfcAdapter: NfcAdapter,
    onTagRead: (String) -> Unit,
    onError: (String) -> Unit
) {
    DisposableEffect(activity, nfcAdapter) {
        val callback = NfcAdapter.ReaderCallback { tag ->
            try {
                val token = buildNfcToken(tag)
                activity.runOnUiThread { onTagRead(token) }
            } catch (ex: Exception) {
                activity.runOnUiThread { onError(ex.message ?: "Không đọc được dữ liệu NFC") }
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

private fun buildNfcToken(tag: Tag): String {
    val tagIdHex = tag.id?.joinToString("") { b -> "%02X".format(b) } ?: "UNKNOWN"
    val techs = tag.techList.joinToString(",")
    val ndefMessage = runCatching {
        val ndef = Ndef.get(tag)
        ndef?.cachedNdefMessage
    }.getOrNull()
    val payloadBytes = ndefMessage?.toByteArray() ?: ByteArray(0)
    val payloadBase64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)
    return "tag_id=$tagIdHex;tech=$techs;ndef_b64=$payloadBase64"
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun CapturedImagePreview(imagePath: String) {
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val ratio = 85.6f / 53.98f
        val frameWidth = maxWidth * 0.92f
        val frameHeight = frameWidth / ratio
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = frameWidth, height = frameHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Ảnh CCCD đã chụp",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Không đọc được ảnh đã chụp", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CaptureHintCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFED7AA)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF5)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Lưu ý khi chụp", fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412))
            Text("- Đặt CCCD đúng giữa khung", color = Color(0xFF9A3412))
            Text("- Giữ đủ ánh sáng, tránh vùng tối", color = Color(0xFF9A3412))
            Text("- Tránh chói lóa và rung tay", color = Color(0xFF9A3412))
        }
    }
}

@Composable
private fun IdCardCameraPreview(
    onImageCaptureReady: (ImageCapture?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            val executor = ContextCompat.getMainExecutor(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val capture = ImageCapture.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                onImageCaptureReady(capture)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }, executor)
        }
        onDispose {
            onImageCaptureReady(null)
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    if (!hasCameraPermission) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.Md),
                verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                Text("Cần quyền camera để chụp CCCD")
                PrimaryButton(text = "Cấp quyền camera", onClick = { launcher.launch(Manifest.permission.CAMERA) })
            }
        }
        return
    }

    val ratio = 85.6f / 53.98f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val frameWidth = maxWidth * 0.92f
        val frameHeight = frameWidth / ratio
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = frameWidth, height = frameHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun captureDocumentImage(
    context: Context,
    executor: Executor,
    imageCapture: ImageCapture?,
    filePrefix: String,
    onStart: () -> Unit,
    onSaved: (String) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: run {
        onError("Camera chưa sẵn sàng")
        return
    }
    val dir = File(context.cacheDir, "ekyc_capture").apply { mkdirs() }
    val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}.jpg")
    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    onStart()
    capture.takePicture(
        output,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "Không thể chụp ảnh")
            }
        }
    )
}

@Composable
private fun GuideRuleCard(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color(0xFF22C55E), RoundedCornerShape(999.dp))
            )
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, color = Color(0xFF166534))
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReviewRow(title: String, passed: Boolean) {
    ReviewRow(title = title, passed = passed, imagePath = null)
}

@Composable
private fun ReviewRow(
    title: String,
    passed: Boolean,
    imagePath: String?
) {
    var showPreview by remember(imagePath) { mutableStateOf(false) }
    val bitmap = remember(imagePath) {
        imagePath?.let { BitmapFactory.decodeFile(it) }
    }

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title)
                if (passed) {
                    StatusBadge(text = "ĐÃ TẢI LÊN", type = BadgeType.Success)
                } else {
                    StatusBadge(text = "CHƯA TẢI LÊN", type = BadgeType.Warning)
                }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Ảnh $title",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showPreview = true }
                )
                Text(
                    "Chạm để xem ảnh lớn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPreview && bitmap != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text(title) },
            text = {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Xem ảnh lớn $title",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                Text(
                    "Đóng",
                    modifier = Modifier.clickable { showPreview = false },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}

