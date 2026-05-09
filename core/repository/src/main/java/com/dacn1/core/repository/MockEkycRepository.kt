package com.dacn1.core.repository

import com.dacn1.core.model.BadgeLevel
import com.dacn1.core.model.CreateSessionRequest
import com.dacn1.core.model.CreateSessionResponse
import com.dacn1.core.model.EkycError
import com.dacn1.core.model.EkycSessionStatus
import com.dacn1.core.model.ErrorCatalogItem
import com.dacn1.core.model.FaceMatch
import com.dacn1.core.model.FinalDecision
import com.dacn1.core.model.Liveness
import com.dacn1.core.model.OcrFields
import com.dacn1.core.model.ProcessingStep
import com.dacn1.core.model.ResultResponse
import com.dacn1.core.model.StatusResponse
import com.dacn1.core.model.SubmitSessionRequest
import com.dacn1.core.model.SubmitSessionResponse
import com.dacn1.core.model.UploadFileResponse
import com.dacn1.core.model.UploadFileType
import com.dacn1.core.model.VerificationResult
import kotlinx.coroutines.delay
import java.util.UUID

class MockEkycRepository(
    private val config: MockConfig = MockConfig()
) : EkycRepository {

    private data class SessionState(
        var status: EkycSessionStatus,
        var statusCursor: Int,
        val uploaded: MutableSet<UploadFileType>
    )

    private val sessions = mutableMapOf<String, SessionState>()

    override suspend fun createSession(request: CreateSessionRequest): CreateSessionResponse {
        delay(config.networkDelayMs)
        val sessionId = "sess_${UUID.randomUUID().toString().take(8)}"
        sessions[sessionId] = SessionState(
            status = EkycSessionStatus.CREATED,
            statusCursor = 0,
            uploaded = mutableSetOf()
        )
        return CreateSessionResponse(sessionId = sessionId, status = EkycSessionStatus.CREATED)
    }

    override suspend fun uploadFile(
        sessionId: String,
        fileType: UploadFileType,
        localPath: String
    ): UploadFileResponse {
        delay(config.networkDelayMs)
        val state = sessions[sessionId] ?: return UploadFileResponse(
            sessionId = sessionId,
            fileType = fileType,
            uploaded = false,
            error = EkycError("SESSION_NOT_FOUND", "Session not found", retryable = false)
        )

        state.uploaded.add(fileType)
        state.status = EkycSessionStatus.COLLECTING

        val qualityCheck = when (fileType) {
            UploadFileType.ID_FRONT -> "DOC_FRAME_OK"
            UploadFileType.ID_BACK -> "DOC_BACK_OK"
            UploadFileType.SELFIE -> "FACE_FRAME_OK"
            UploadFileType.LIVENESS_VIDEO -> "LIVENESS_FRAME_OK"
        }

        return UploadFileResponse(
            sessionId = sessionId,
            fileType = fileType,
            uploaded = true,
            qualityCheck = qualityCheck
        )
    }

    override suspend fun submitSession(
        sessionId: String,
        request: SubmitSessionRequest
    ): SubmitSessionResponse {
        delay(config.networkDelayMs)
        val state = sessions[sessionId] ?: return SubmitSessionResponse(
            sessionId = sessionId,
            status = EkycSessionStatus.FAILED
        )

        val required = setOf(
            UploadFileType.ID_FRONT,
            UploadFileType.ID_BACK,
            UploadFileType.SELFIE,
            UploadFileType.LIVENESS_VIDEO
        )

        if (!state.uploaded.containsAll(required)) {
            state.status = EkycSessionStatus.COLLECTING
            return SubmitSessionResponse(sessionId = sessionId, status = EkycSessionStatus.COLLECTING)
        }

        state.status = EkycSessionStatus.PROCESSING
        state.statusCursor = 0
        return SubmitSessionResponse(sessionId = sessionId, status = EkycSessionStatus.PROCESSING)
    }

    override suspend fun getStatus(sessionId: String): StatusResponse {
        delay(config.networkDelayMs)
        val state = sessions[sessionId]
            ?: return StatusResponse(sessionId, EkycSessionStatus.FAILED, 0, ProcessingStep.QUEUED)

        if (config.scenario == MockScenario.TIMEOUT) {
            delay(config.networkDelayMs * 8)
            return StatusResponse(
                sessionId = sessionId,
                status = EkycSessionStatus.PROCESSING,
                progress = 85,
                currentStep = ProcessingStep.LIVENESS_ANALYSIS,
                etaSeconds = 120
            )
        }

        val steps = listOf(
            ProcessingStep.OCR_ANALYSIS,
            ProcessingStep.FACE_MATCH,
            ProcessingStep.LIVENESS_ANALYSIS,
            ProcessingStep.FINALIZING,
            ProcessingStep.DONE
        )

        val idx = state.statusCursor.coerceIn(0, steps.lastIndex)
        val currentStep = steps[idx]
        val progress = ((idx + 1) * 20).coerceAtMost(100)

        if (config.statusAdvanceOnEachCall && idx < steps.lastIndex) {
            state.statusCursor += 1
        }

        if (currentStep == ProcessingStep.DONE) {
            state.status = when (config.scenario) {
                MockScenario.PASSED -> EkycSessionStatus.PASSED
                MockScenario.FAILED_LIVENESS -> EkycSessionStatus.FAILED
                MockScenario.REVIEW_FACE -> EkycSessionStatus.REVIEW
                MockScenario.TIMEOUT -> EkycSessionStatus.PROCESSING
            }
        } else {
            state.status = EkycSessionStatus.PROCESSING
        }

        return StatusResponse(
            sessionId = sessionId,
            status = state.status,
            progress = progress,
            currentStep = currentStep,
            etaSeconds = if (currentStep == ProcessingStep.DONE) 0 else (steps.lastIndex - idx) * 7
        )
    }

    override suspend fun getResult(sessionId: String): ResultResponse {
        delay(config.networkDelayMs)
        return when (config.scenario) {
            MockScenario.PASSED -> passedResult(sessionId)
            MockScenario.FAILED_LIVENESS -> failedLivenessResult(sessionId)
            MockScenario.REVIEW_FACE -> reviewFaceResult(sessionId)
            MockScenario.TIMEOUT -> timeoutResult(sessionId)
        }
    }

    override suspend fun getErrorCatalog(): List<ErrorCatalogItem> {
        delay(config.networkDelayMs / 2)
        return listOf(
            ErrorCatalogItem("DOC_BLURRY", "Anh mo", "Vui long chup lai giay to ro net hon.", true),
            ErrorCatalogItem("DOC_GLARE", "Anh bi choi", "Giam anh sang va tranh phan xa tren be mat the.", true),
            ErrorCatalogItem("FACE_MISMATCH", "Khong trung khop", "Khuon mat selfie khong trung khop voi giay to.", true),
            ErrorCatalogItem("LIVENESS_LOW_SCORE", "Liveness thap", "Vui long quay lai video liveness theo huong dan.", true),
            ErrorCatalogItem("TIMEOUT_PROCESSING", "Xu ly qua lau", "He thong dang ban, thu lai sau it phut.", true)
        )
    }

    override suspend fun getHistory(): List<VerificationResult> {
        delay(config.networkDelayMs)
        return listOf(
            VerificationResult(
                sessionId = "sess_1001",
                finalDecision = FinalDecision.PASSED,
                riskScore = 0.08,
                ocr = OcrFields("NGUYEN VAN A", "0792xxxxxxx", "2001-01-01", 0.96),
                faceMatch = FaceMatch(0.91, 0.80, true),
                liveness = Liveness(0.95, 0.70, true)
            ),
            VerificationResult(
                sessionId = "sess_1002",
                finalDecision = FinalDecision.FAILED,
                errors = listOf(EkycError("LIVENESS_LOW_SCORE", "Liveness score below threshold", true))
            ),
            VerificationResult(
                sessionId = "sess_1003",
                finalDecision = FinalDecision.REVIEW,
                errors = listOf(EkycError("FACE_MISMATCH", "Face match in gray zone", true, BadgeLevel.WARNING))
            )
        )
    }

    private fun passedResult(sessionId: String): ResultResponse = ResultResponse(
        sessionId = sessionId,
        finalDecision = FinalDecision.PASSED,
        riskScore = 0.08,
        ocr = OcrFields("NGUYEN VAN A", "0792xxxxxxx", "2001-01-01", 0.96),
        faceMatch = FaceMatch(similarity = 0.91, threshold = 0.80, matched = true),
        liveness = Liveness(score = 0.95, threshold = 0.70, isLive = true)
    )

    private fun failedLivenessResult(sessionId: String): ResultResponse = ResultResponse(
        sessionId = sessionId,
        finalDecision = FinalDecision.FAILED,
        riskScore = 0.72,
        ocr = OcrFields("NGUYEN VAN A", "0792xxxxxxx", "2001-01-01", 0.95),
        faceMatch = FaceMatch(similarity = 0.90, threshold = 0.80, matched = true),
        liveness = Liveness(score = 0.43, threshold = 0.70, isLive = false),
        errors = listOf(
            EkycError(
                code = "LIVENESS_LOW_SCORE",
                message = "Liveness score below threshold",
                retryable = true
            )
        )
    )

    private fun reviewFaceResult(sessionId: String): ResultResponse = ResultResponse(
        sessionId = sessionId,
        finalDecision = FinalDecision.REVIEW,
        riskScore = 0.49,
        ocr = OcrFields("NGUYEN VAN A", "0792xxxxxxx", "2001-01-01", 0.94),
        faceMatch = FaceMatch(similarity = 0.79, threshold = 0.80, matched = false),
        liveness = Liveness(score = 0.90, threshold = 0.70, isLive = true),
        errors = listOf(
            EkycError(
                code = "FACE_MISMATCH",
                message = "Face similarity in review zone",
                retryable = true,
                displayLevel = BadgeLevel.WARNING
            )
        )
    )

    private fun timeoutResult(sessionId: String): ResultResponse = ResultResponse(
        sessionId = sessionId,
        finalDecision = FinalDecision.REVIEW,
        riskScore = null,
        errors = listOf(
            EkycError(
                code = "TIMEOUT_PROCESSING",
                message = "Processing timeout",
                retryable = true
            )
        )
    )
}
