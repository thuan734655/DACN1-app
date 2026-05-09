package com.dacn1.core.model

data class CreateSessionRequest(
    val userRef: String,
    val flowVersion: String,
    val platform: String = "android",
    val appVersion: String
)

data class CreateSessionResponse(
    val sessionId: String,
    val status: EkycSessionStatus
)

data class UploadFileResponse(
    val sessionId: String,
    val fileType: UploadFileType,
    val uploaded: Boolean,
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
