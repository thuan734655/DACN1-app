package com.dacn1.core.model

data class CreateSessionRequest(
    val userRef: String,
    val flowVersion: String,
    val platform: String = "android",
    val appVersion: String
)

data class CreateSessionResponse(
    val sessionId: String? = null,
    val status: EkycSessionStatus? = null
)

data class UploadFileResponse(
    val sessionId: String,
    val fileType: UploadFileType,
    val uploaded: Boolean,
    val file_id: String? = null,
    val qualityCheck: String? = null,
    val error: EkycError? = null
)

data class SubmitSessionRequest(
    val confirm: Boolean = true
)

data class SubmitSessionResponse(
    val sessionId: String,
    val status: EkycSessionStatus
)

data class StatusResponse(
    val sessionId: String,
    val status: EkycSessionStatus,
    val progress: Int,
    val currentStep: ProcessingStep,
    val etaSeconds: Int? = null
)

data class ResultResponse(
    val sessionId: String,
    val finalDecision: FinalDecision,
    val riskScore: Double? = null,
    val ocr: OcrFields? = null,
    val faceMatch: FaceMatch? = null,
    val liveness: Liveness? = null,
    val errors: List<EkycError> = emptyList()
)

data class VerifyNfcRequest(
    val nfcToken: String,
    val idNumber: String? = null
)

data class VerifyNfcResponse(
    val sessionId: String,
    val passed: Boolean,
    val message: String,
    val matchedFields: List<String> = emptyList(),
    val error: EkycError? = null
)

data class OcrRequest(
    val front_file_id: String,
    val back_file_id: String,
    val qr: String? = null
)

data class FaceMatchRequest(
    val document_face_file_id: String,
    val selfie_file_id: String
)

data class FaceMatchResponse(
    val matched: Boolean,
    val similarity: Double,
    val threshold: Double,
    val quality: FaceMatchQuality? = null
)

data class FaceMatchQuality(
    val selfie_blur: Double,
    val selfie_brightness: Double
)

data class OcrResponse(
    val success: String,
    val fields: Map<String, String>? = null,
    val confidence: Double? = null,
    val warnings: List<String>? = null
)
