package com.dacn1.feature.document

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
    onCompleted: () -> Unit
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
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val warnings = remember { mutableStateListOf<String>() }

    fun upload(type: UploadFileType, path: String, onSuccess: () -> Unit) {
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
                                text = if (uploading) "Đang tải lên..." else "Xác nhận ảnh",
                                enabled = !uploading && !capturing && !frontImagePath.isNullOrBlank(),
                                onClick = {
                                    val path = frontImagePath ?: return@PrimaryButton
                                    upload(UploadFileType.ID_FRONT, path) {
                                        frontUploaded = true
                                        step = DocumentStep.BACK_CAPTURE
                                    }
                                }
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
                                text = if (uploading) "Đang tải lên..." else "Xác nhận ảnh",
                                enabled = !uploading && !capturing && !backImagePath.isNullOrBlank(),
                                onClick = {
                                    val path = backImagePath ?: return@PrimaryButton
                                    upload(UploadFileType.ID_BACK, path) {
                                        backUploaded = true
                                        step = DocumentStep.REVIEW
                                    }
                                }
                            )
                        }
                    }
                }

                DocumentStep.REVIEW -> {
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
                    Spacer(modifier = Modifier.weight(1f))
                    SecondaryButton(text = "Chụp lại", onClick = { step = DocumentStep.FRONT_CAPTURE })
                    PrimaryButton(
                        text = "Tiếp tục selfie",
                        enabled = frontUploaded && backUploaded,
                        onClick = onCompleted
                    )
                }
            }
        }
    }
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
                    StatusBadge(text = "UPLOADED", type = BadgeType.Success)
                } else {
                    StatusBadge(text = "PENDING", type = BadgeType.Warning)
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
