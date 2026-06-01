package com.dacn1.feature.document

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
    REVIEW
}



@Composable
fun DocumentFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onCompleted: () -> Unit,
    onExitToHome: () -> Unit,
    onOcrProcessTriggered: (String, String) -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(DocumentStep.GUIDE) }
    var frontUploaded by remember { mutableStateOf(false) }
    var backUploaded by remember { mutableStateOf(false) }
    var frontImagePath by remember { mutableStateOf<String?>(null) }
    var backImagePath by remember { mutableStateOf<String?>(null) }
    var frontFileId by remember { mutableStateOf<String?>(null) }
    var backFileId by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var showOcrErrorPopup by remember { mutableStateOf<String?>(null) }
    var isProcessingOcr by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val warnings = remember { mutableStateListOf<String>() }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

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
                if (response.uploaded) {
                    if (type == UploadFileType.ID_FRONT) frontFileId = response.file_id
                    if (type == UploadFileType.ID_BACK) backFileId = response.file_id
                    response.qualityCheck?.let {
                        warnings.clear()
                        warnings.add(it)
                    }
                    onSuccess()
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

    fun triggerAutoCapture(filePrefix: String, type: UploadFileType, onSuccess: () -> Unit) {
        if (capturing || uploading || cameraControl == null || imageCapture == null) return
        
        capturing = true
        lastError = "Đang lấy nét tự động..."
        
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        
        try {
            cameraControl!!.startFocusAndMetering(action).addListener({
                mainExecutor.execute {
                    lastError = "Đang chụp..."
                    captureDocumentImage(
                        context = context,
                        executor = mainExecutor,
                        imageCapture = imageCapture,
                        filePrefix = filePrefix,
                        onStart = { lastError = null },
                        onSaved = { path ->
                            if (type == UploadFileType.ID_FRONT) frontImagePath = path else backImagePath = path
                            capturing = false
                            upload(
                                type = type,
                                path = path,
                                onSuccess = onSuccess,
                                onQualityFailed = {
                                    if (type == UploadFileType.ID_FRONT) {
                                        frontUploaded = false
                                        frontImagePath = null
                                    } else {
                                        backUploaded = false
                                        backImagePath = null
                                    }
                                }
                            )
                        },
                        onError = {
                            capturing = false
                            lastError = it
                        }
                    )
                }
            }, mainExecutor)
        } catch (e: Exception) {
            capturing = false
            lastError = "Lỗi lấy nét: ${e.message}"
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
                        IdCardCameraPreview(onImageCaptureReady = { capture, control -> 
                            imageCapture = capture
                            cameraControl = control 
                        })
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
                                text = if (capturing) "Đang chụp..." else if (frontImagePath == null) "Chụp thủ công" else "Chụp lại",
                                enabled = !uploading && !capturing,
                                onClick = {
                                    if (frontImagePath != null) {
                                        frontImagePath = null
                                        lastError = null
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
                            if (frontImagePath == null) {
                                PrimaryButton(
                                    text = if (capturing) "Đang tự động..." else "Tự động chụp",
                                    enabled = !uploading && !capturing && cameraControl != null,
                                    onClick = {
                                        triggerAutoCapture("id_front", UploadFileType.ID_FRONT) {
                                            frontUploaded = true
                                            step = DocumentStep.BACK_CAPTURE
                                        }
                                    }
                                )
                            } else {
                                PrimaryButton(
                                    text = "Tiếp tục",
                                    enabled = frontUploaded,
                                    onClick = { step = DocumentStep.BACK_CAPTURE }
                                )
                            }
                        }
                    }
                }

                DocumentStep.BACK_CAPTURE -> {
                    Text("Chụp mặt sau", style = MaterialTheme.typography.headlineLarge)
                    if (backImagePath.isNullOrBlank()) {
                        IdCardCameraPreview(onImageCaptureReady = { capture, control -> 
                            imageCapture = capture
                            cameraControl = control 
                        })
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
                                text = if (capturing) "Đang chụp..." else if (backImagePath == null) "Chụp thủ công" else "Chụp lại",
                                enabled = !uploading && !capturing,
                                onClick = {
                                    if (backImagePath != null) {
                                        backImagePath = null
                                        lastError = null
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
                            if (backImagePath == null) {
                                PrimaryButton(
                                    text = if (capturing) "Đang tự động..." else "Tự động chụp",
                                    enabled = !uploading && !capturing && cameraControl != null,
                                    onClick = {
                                        triggerAutoCapture("id_back", UploadFileType.ID_BACK) {
                                            backUploaded = true
                                            step = DocumentStep.REVIEW
                                        }
                                    }
                                )
                            } else {
                                PrimaryButton(
                                    text = "Hoàn thành",
                                    enabled = backUploaded,
                                    onClick = { step = DocumentStep.REVIEW }
                                )
                            }
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
                            imagePath = frontImagePath,
                            fileId = frontFileId
                        )
                        ReviewRow(
                            title = "Mặt sau",
                            passed = backUploaded,
                            imagePath = backImagePath,
                            fileId = backFileId
                        )
                        if (lastError != null) Text(lastError ?: "", color = MaterialTheme.colorScheme.error)
                    }
                    SecondaryButton(
                        text = "Chụp lại",
                        onClick = {
                            step = DocumentStep.FRONT_CAPTURE
                        }
                    )
                    PrimaryButton(
                        text = "Xác thực CCCD",
                        enabled = frontUploaded && backUploaded,
                        isLoading = isProcessingOcr,
                        onClick = {
                            isProcessingOcr = true
                            scope.launch {
                                try {
                                    val response = repository.processOcr(
                                        sessionId = sessionId,
                                        frontFileId = frontFileId!!,
                                        backFileId = backFileId!!,
                                        qrLocalPath = null
                                    )
                                    if (response.success == "process") {
                                        onOcrProcessTriggered(frontFileId!!, backFileId!!)
                                    } else if (response.success == "true" || response.success == "done") {
                                        onCompleted()
                                    } else {
                                        val warningMsg = response.warnings?.joinToString(", ")
                                        showOcrErrorPopup = if (!warningMsg.isNullOrBlank()) {
                                            "Lỗi OCR: $warningMsg"
                                        } else {
                                            "Lỗi OCR: Thất bại, vui lòng chụp lại"
                                        }
                                    }
                                } catch (e: Exception) {
                                    showOcrErrorPopup = "Lỗi kết nối OCR: ${e.message}"
                                } finally {
                                    isProcessingOcr = false
                                }
                            }
                        }
                    )
                }

            }
        }
    }
    if (showOcrErrorPopup != null) {
        AlertDialog(
            onDismissRequest = { /* Do nothing */ },
            title = { Text("Lỗi Xác thực OCR") },
            text = { Text(showOcrErrorPopup!!) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showOcrErrorPopup = null
                        frontImagePath = null
                        backImagePath = null
                        frontFileId = null
                        backFileId = null
                        frontUploaded = false
                        backUploaded = false
                        step = DocumentStep.FRONT_CAPTURE
                    }
                ) {
                    Text("Đồng ý (Chụp lại)")
                }
            }
        )
    }
}

private fun isDocumentQualityPassed(qualityCheck: String?): Boolean {
    if (qualityCheck.isNullOrBlank()) return false
    val normalized = qualityCheck.uppercase()
    return normalized == "DOC_FRAME_OK" || normalized == "DOC_BACK_OK" || normalized.endsWith("_OK")
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
    onImageCaptureReady: (ImageCapture?, CameraControl?) -> Unit
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
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                onImageCaptureReady(capture, camera.cameraControl)
            }, executor)
        }
        onDispose {
            onImageCaptureReady(null, null)
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
    imagePath: String?,
    fileId: String? = null
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
            if (fileId != null) {
                Text(
                    text = "ID: $fileId",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
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

