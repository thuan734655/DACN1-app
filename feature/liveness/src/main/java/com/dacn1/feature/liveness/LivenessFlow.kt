package com.dacn1.feature.liveness

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.SecondaryButton
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.model.UploadFileType
import com.dacn1.core.repository.EkycRepository
import kotlinx.coroutines.launch

private enum class LivenessStage {
    TRACKING,
    REVIEW
}

@Composable
fun LivenessFlowRoute(
    repository: EkycRepository,
    sessionId: String,
    onCompleted: () -> Unit
) {
    val required = remember {
        linkedSetOf(
            FacePoseDirection.LEFT,
            FacePoseDirection.RIGHT,
            FacePoseDirection.UP,
            FacePoseDirection.DOWN
        )
    }
    var stage by remember { mutableStateOf(LivenessStage.TRACKING) }
    var currentDirection by remember { mutableStateOf(FacePoseDirection.NO_FACE) }
    var doneDirections by remember { mutableStateOf(setOf<FacePoseDirection>()) }
    var faceDetected by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (stage == LivenessStage.TRACKING) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Xác thực khuôn mặt", style = MaterialTheme.typography.titleLarge)
            Text("Bước 4/5", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Thực hiện lần lượt: quay trái, quay phải, ngẩng lên, cúi xuống.")

            PoseCameraTrackerView(
                onPoseUpdate = { result ->
                    currentDirection = result.direction
                    faceDetected = result.faceDetected
                    if (result.direction in required) {
                        doneDirections = doneDirections + result.direction
                    }
                }
            )

            LivenessOverlayStatus(
                currentDirection = currentDirection,
                doneDirections = doneDirections
            )

            if (errorMessage != null) {
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "Hoàn tất quay video",
                enabled = faceDetected && doneDirections.containsAll(required),
                onClick = { stage = LivenessStage.REVIEW }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Kiểm tra video", style = MaterialTheme.typography.titleLarge)
            Text("Bước 5/5", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Kết quả động tác:")
            Text("• Trái: ${if (FacePoseDirection.LEFT in doneDirections) "Đạt" else "Chưa đạt"}")
            Text("• Phải: ${if (FacePoseDirection.RIGHT in doneDirections) "Đạt" else "Chưa đạt"}")
            Text("• Lên: ${if (FacePoseDirection.UP in doneDirections) "Đạt" else "Chưa đạt"}")
            Text("• Xuống: ${if (FacePoseDirection.DOWN in doneDirections) "Đạt" else "Chưa đạt"}")

            if (errorMessage != null) {
                Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Box(modifier = Modifier.weight(1f)) {
                    SecondaryButton(
                        text = "Quay lại",
                        onClick = {
                            doneDirections = emptySet()
                            stage = LivenessStage.TRACKING
                            errorMessage = null
                        }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                        text = if (uploading) "Đang gửi..." else "Dùng video này",
                        enabled = !uploading && doneDirections.containsAll(required),
                        onClick = {
                            uploading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val response = repository.uploadFile(
                                        sessionId = sessionId,
                                        fileType = UploadFileType.LIVENESS_VIDEO,
                                        localPath = "mock/liveness.mp4"
                                    )
                                    if (response.uploaded) onCompleted()
                                    else errorMessage = response.error?.message ?: "Không thể tải video xác thực"
                                } catch (ex: Exception) {
                                    errorMessage = ex.message ?: "Không thể gửi video xác thực"
                                } finally {
                                    uploading = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PoseCameraTrackerView(
    onPoseUpdate: (FacePoseResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val poseTracker = remember { FacePoseTracker() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (hasCameraPermission) {
            val executor = ContextCompat.getMainExecutor(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    poseTracker.analyze(imageProxy, mirrorForFrontCamera = true, onResult = onPoseUpdate)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            }, executor)
        }

        onDispose {
            poseTracker.close()
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
        ) {
            Text("Cần quyền camera để theo dõi chuyển động khuôn mặt.")
            PrimaryButton(text = "Cấp quyền camera", onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(Color(0xFF0B1220), RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun LivenessOverlayStatus(
    currentDirection: FacePoseDirection,
    doneDirections: Set<FacePoseDirection>
) {
    val allDirections = listOf(
        FacePoseDirection.LEFT to "Trái",
        FacePoseDirection.RIGHT to "Phải",
        FacePoseDirection.UP to "Lên",
        FacePoseDirection.DOWN to "Xuống"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp))
            .padding(Spacing.Sm),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Hướng hiện tại: $currentDirection", fontWeight = FontWeight.SemiBold)
        allDirections.forEach { (direction, label) ->
            val done = direction in doneDirections
            Text("${if (done) "✓" else "•"} $label", color = if (done) Color(0xFF166534) else Color(0xFF334155))
        }
    }
}
