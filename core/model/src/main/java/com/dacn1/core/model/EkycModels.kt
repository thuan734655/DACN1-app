package com.dacn1.core.model

enum class EkycSessionStatus {
    CREATED,
    COLLECTING,
    READY_TO_SUBMIT,
    PROCESSING,
    PASSED,
    FAILED,
    REVIEW
}

enum class UploadFileType {
    ID_FRONT,
    ID_BACK,
    SELFIE,
    LIVENESS_VIDEO
}

enum class UploadItemStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED
}

enum class ProcessingStep {
    QUEUED,
    OCR_ANALYSIS,
    FACE_MATCH,
    LIVENESS_ANALYSIS,
    FINALIZING,
    DONE
}

enum class FinalDecision {
    PASSED,
    FAILED,
    REVIEW
}

enum class BadgeLevel {
    SUCCESS,
    WARNING,
    ERROR,
    INFO
}

data class EkycSession(
    val sessionId: String,
    val userRef: String,
    val status: EkycSessionStatus,
    val createdAtIso: String,
    val updatedAtIso: String
)

data class UploadItem(
    val fileType: UploadFileType,
    val status: UploadItemStatus,
    val progressPercent: Int,
    val localPath: String? = null,
    val remoteKey: String? = null,
    val errorCode: String? = null
)

data class ProcessingStatus(
    val sessionId: String,
    val status: EkycSessionStatus,
    val progressPercent: Int,
    val currentStep: ProcessingStep,
    val etaSeconds: Int? = null
)

data class OcrFields(
    val fullName: String? = null,
    val idNumber: String? = null,
    val dob: String? = null,
    val confidence: Double? = null
)

data class FaceMatch(
    val similarity: Double,
    val threshold: Double,
    val matched: Boolean
)

data class Liveness(
    val score: Double,
    val threshold: Double,
    val isLive: Boolean
)

data class EkycError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val displayLevel: BadgeLevel = BadgeLevel.ERROR
)

data class VerificationResult(
    val sessionId: String,
    val finalDecision: FinalDecision,
    val riskScore: Double? = null,
    val ocr: OcrFields? = null,
    val faceMatch: FaceMatch? = null,
    val liveness: Liveness? = null,
    val errors: List<EkycError> = emptyList()
)

data class ErrorCatalogItem(
    val code: String,
    val title: String,
    val userMessage: String,
    val retryable: Boolean
)
