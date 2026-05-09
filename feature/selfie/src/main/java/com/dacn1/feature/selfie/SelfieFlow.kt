package com.dacn1.feature.selfie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.SecondaryButton
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.UploadFileType
import com.dacn1.core.repository.EkycRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.Executor
import kotlinx.coroutines.launch

@Composable
fun SelfieFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scope = rememberCoroutineScope()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedPath by remember { mutableStateOf<String?>(null) }
    var faceInFrame by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var uploaded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050B17))
            .padding(Spacing.Md)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Chụp ảnh selfie", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Bước 3/5", color = Color(0xFF94A3B8))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .background(Color(0xFF0A1325), RoundedCornerShape(16.dp))
            ) {
                if (capturedPath == null) {
                    SelfieCameraView(
                        onImageCaptureReady = { imageCapture = it },
                        onFaceDetected = { faceInFrame = it }
                    )
                    FaceGuideOverlay(faceInFrame = faceInFrame)
                } else {
                    val bitmap = remember(capturedPath) { BitmapFactory.decodeFile(capturedPath) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Ảnh selfie đã chụp",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(errorMessage ?: "", color = Color(0xFFFCA5A5))
            }

            Spacer(modifier = Modifier.weight(1f))

            if (capturedPath == null) {
                PrimaryButton(
                    text = if (capturing) "Đang chụp..." else "Chụp ảnh",
                    enabled = faceInFrame && !capturing,
                    onClick = {
                        captureSelfie(
                            context = context,
                            executor = mainExecutor,
                            imageCapture = imageCapture,
                            onStart = {
                                capturing = true
                                errorMessage = null
                            },
                            onSaved = {
                                capturedPath = it
                                capturing = false
                            },
                            onError = {
                                errorMessage = it
                                capturing = false
                            }
                        )
                    }
                )
            } else {
                SecondaryButton(
                    text = "Chụp lại",
                    enabled = !uploading,
                    onClick = {
                        capturedPath = null
                        uploaded = false
                        errorMessage = null
                    }
                )
                PrimaryButton(
                    text = if (uploaded) "Tiếp tục xác thực" else if (uploading) "Đang tải ảnh..." else "Xác nhận ảnh",
                    enabled = !uploading,
                    onClick = {
                        if (uploaded) {
                            onCompleted()
                            return@PrimaryButton
                        }
                        val path = capturedPath ?: return@PrimaryButton
                        uploading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val response = repository.uploadFile(
                                    sessionId = sessionId,
                                    fileType = UploadFileType.SELFIE,
                                    localPath = path
                                )
                                if (response.uploaded) {
                                    uploaded = true
                                } else {
                                    errorMessage = response.error?.message ?: "Không thể tải ảnh selfie"
                                }
                            } catch (ex: Exception) {
                                errorMessage = ex.message ?: "Không thể tải ảnh selfie"
                            } finally {
                                uploading = false
                            }
                        }
                    }
                )
            }

            Text(
                "Ảnh sẽ được sử dụng để xác thực danh tính của bạn",
                color = Color(0xFF64748B),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun BoxScope.FaceGuideOverlay(faceInFrame: Boolean) {
    val ovalWidth = 180.dp
    val ovalHeight = 250.dp

    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind {
                val w = ovalWidth.toPx()
                val h = ovalHeight.toPx()
                val left = (size.width - w) / 2f
                val top = (size.height - h) / 2f
                val path = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))
                }
                drawPath(path = path, color = Color(0xFF22C55E), style = Stroke(width = 4.dp.toPx()))
            }
    )

    if (!faceInFrame) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = Spacing.Md)
                .background(Color(0xFF7F1D1D), RoundedCornerShape(10.dp))
                .padding(horizontal = Spacing.Md, vertical = Spacing.Sm)
        ) {
            Text("Vui lòng đưa khuôn mặt vào đúng vị trí", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }

    Text(
        "Giữ khuôn mặt trong khung",
        color = Color(0xFFE2E8F0),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.Md)
    )
}

@Composable
private fun SelfieCameraView(
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onFaceDetected: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build()
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
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

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            val frameWidth = image.width.toFloat()
                            val frameHeight = image.height.toFloat()
                            val inside = faces.any { face ->
                                isFaceInsideOvalGuide(
                                    face = face,
                                    frameWidth = frameWidth,
                                    frameHeight = frameHeight
                                )
                            }
                            onFaceDetected(inside)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    capture,
                    analysis
                )
            }, executor)
        }

        onDispose {
            detector.close()
            onImageCaptureReady(null)
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Cần quyền camera để chụp selfie", color = Color.White)
            PrimaryButton(text = "Cấp quyền camera", onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }
        return
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

private fun isFaceInsideOvalGuide(
    face: Face,
    frameWidth: Float,
    frameHeight: Float
): Boolean {
    if (frameWidth <= 0f || frameHeight <= 0f) return false

    val ovalWidth = frameWidth * 0.50f
    val ovalHeight = frameHeight * 0.58f
    val cx = frameWidth / 2f
    val cy = frameHeight / 2f
    val rx = ovalWidth / 2f
    val ry = ovalHeight / 2f

    val faceCenterX = face.boundingBox.exactCenterX()
    val faceCenterY = face.boundingBox.exactCenterY()
    val nx = (faceCenterX - cx) / rx
    val ny = (faceCenterY - cy) / ry

    return (nx * nx + ny * ny) <= 1f
}

private fun captureSelfie(
    context: Context,
    executor: Executor,
    imageCapture: ImageCapture?,
    onStart: () -> Unit,
    onSaved: (String) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: run {
        onError("Camera chưa sẵn sàng")
        return
    }
    val dir = File(context.cacheDir, "ekyc_selfie").apply { mkdirs() }
    val file = File(dir, "selfie_${System.currentTimeMillis()}.jpg")
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
                onError(exception.message ?: "Không thể chụp selfie")
            }
        }
    )
}
