    package com.dacn1.feature.liveness

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

enum class FacePoseDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
    CENTER,
    NO_FACE
}

data class FacePoseResult(
    val direction: FacePoseDirection,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val faceDetected: Boolean = false,
    val faceInGuide: Boolean = false
)

class FacePoseTracker(
    private val yawThreshold: Float = 15f,
    private val pitchThreshold: Float = 12f
) {
    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build()
        )
    }

    fun analyze(
        imageProxy: ImageProxy,
        mirrorForFrontCamera: Boolean = true,
        onResult: (FacePoseResult) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(FacePoseResult(direction = FacePoseDirection.NO_FACE))
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { faces ->
                onResult(
                    resolvePose(
                        faces = faces,
                        mirrorForFrontCamera = mirrorForFrontCamera,
                        frameWidth = image.width.toFloat(),
                        frameHeight = image.height.toFloat()
                    )
                )
            }
            .addOnFailureListener {
                onResult(FacePoseResult(direction = FacePoseDirection.NO_FACE))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun resolvePose(
        faces: List<Face>,
        mirrorForFrontCamera: Boolean,
        frameWidth: Float,
        frameHeight: Float
    ): FacePoseResult {
        val face = faces.firstOrNull()
            ?: return FacePoseResult(direction = FacePoseDirection.NO_FACE, faceDetected = false)

        var yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX

        // Front camera preview is mirrored; invert yaw so "RIGHT" means user turns right.
        if (mirrorForFrontCamera) yaw = -yaw

        val direction = when {
            yaw > yawThreshold -> FacePoseDirection.RIGHT
            yaw < -yawThreshold -> FacePoseDirection.LEFT
            pitch > pitchThreshold -> FacePoseDirection.UP
            pitch < -pitchThreshold -> FacePoseDirection.DOWN
            else -> FacePoseDirection.CENTER
        }
        return FacePoseResult(
            direction = direction,
            yaw = yaw,
            pitch = pitch,
            faceDetected = true,
            faceInGuide = isFaceInsideOvalGuide(face, frameWidth, frameHeight)
        )
    }

    private fun isFaceInsideOvalGuide(
        face: Face,
        frameWidth: Float,
        frameHeight: Float
    ): Boolean {
        if (frameWidth <= 0f || frameHeight <= 0f) return false

        val ovalWidth = frameWidth * 0.50f
        val ovalHeight = frameHeight * 0.75f
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

    fun close() {
        detector.close()
    }
}
