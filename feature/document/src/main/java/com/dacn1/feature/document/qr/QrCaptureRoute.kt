package com.dacn1.feature.document.qr

import android.content.Context
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.SecondaryButton
import com.dacn1.core.designsystem.theme.Spacing
import com.dacn1.core.repository.EkycRepository
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

@Composable
fun QrCaptureRoute(
    sessionId: String,
    frontFileId: String?,
    backFileId: String?,
    repository: EkycRepository,
    onCompleted: () -> Unit,
    onRestartRequired: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var isUploading by remember { mutableStateOf(false) }
    var isFocusing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cameraSessionId by remember { mutableStateOf(0) }

    fun captureQr() {
        val capture = imageCapture ?: return
        isUploading = true
        val photoFile = File(context.cacheDir, "qr_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val provider = ProcessCameraProvider.getInstance(context).get()
                        provider.unbindAll()
                    } catch (e: Exception) {}

                    coroutineScope.launch {
                        try {
                            val response = repository.processOcr(
                                sessionId = sessionId,
                                frontFileId = frontFileId!!,
                                backFileId = backFileId!!,
                                qrLocalPath = photoFile.absolutePath
                            )
                            if (response.success == "done" || response.success == "true") {
                                onCompleted()
                            } else {
                                val warningMsg = response.warnings?.joinToString(", ")
                                errorMessage = if (!warningMsg.isNullOrBlank()) {
                                    "Lỗi xử lý OCR: $warningMsg"
                                } else {
                                    "Lỗi xử lý OCR: Thất bại, vui lòng chụp lại"
                                }
                                isUploading = false
                            }
                        } catch (e: Exception) {
                            errorMessage = "Lỗi kết nối."
                            isUploading = false
                        }
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    errorMessage = "Lỗi chụp ảnh: ${exc.message}"
                    isUploading = false
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        key(cameraSessionId) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        errorMessage = "Không thể mở camera."
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
        }

        // Khung ngắm QR vuông
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .border(2.dp, Color.Green)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(Spacing.Lg)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isUploading || isFocusing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(if (isFocusing) "Đang tự động lấy nét..." else "Đang xử lý ảnh QR...", color = Color.White, modifier = Modifier.padding(top = Spacing.Sm))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SecondaryButton(
                            text = "Chụp thủ công",
                            onClick = { captureQr() }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        PrimaryButton(
                            text = "Tự động quét",
                            enabled = cameraControl != null,
                            onClick = {
                                isFocusing = true
                                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                                val point = factory.createPoint(0.5f, 0.5f)
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
                                cameraControl?.startFocusAndMetering(action)?.addListener({
                                    ContextCompat.getMainExecutor(context).execute {
                                        isFocusing = false
                                        captureQr()
                                    }
                                }, ContextCompat.getMainExecutor(context))
                            }
                        )
                    }
                }
            }
        }
    }

    if (errorMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* Do nothing */ },
            title = { androidx.compose.material3.Text("Lỗi Xác thực OCR") },
            text = { androidx.compose.material3.Text(errorMessage!!) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        errorMessage = null
                        cameraSessionId++
                    }
                ) {
                    androidx.compose.material3.Text("Chụp lại QR")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        errorMessage = null
                        onRestartRequired()
                    }
                ) {
                    androidx.compose.material3.Text("Chụp lại CCCD")
                }
            }
        )
    }
}
