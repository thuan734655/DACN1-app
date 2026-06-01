package com.dacn1.core.repository

import com.dacn1.core.model.*
import com.dacn1.core.network.EkycApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class RemoteEkycRepository(
    private val apiService: EkycApiService
) : EkycRepository {

    override suspend fun createSession(request: CreateSessionRequest): CreateSessionResponse {
        return withContext(Dispatchers.IO) {
            apiService.createSession(request)
        }
    }

    override suspend fun uploadFile(
        sessionId: String,
        fileType: UploadFileType,
        localPath: String
    ): UploadFileResponse {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(localPath)
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val typeStr = when (fileType) {
                    UploadFileType.ID_FRONT -> "DOCUMENT_FRONT"
                    UploadFileType.ID_BACK -> "DOCUMENT_BACK"
                    else -> fileType.name
                }
                val typeBody = typeStr.toRequestBody("text/plain".toMediaTypeOrNull())
                apiService.uploadFile(sessionId, typeBody, body)
            } catch (e: Exception) {
                UploadFileResponse(
                    sessionId = sessionId,
                    fileType = fileType,
                    uploaded = false,
                    error = EkycError("UPLOAD_FAILED", "Upload failed: ${e.message}", true)
                )
            }
        }
    }

    override suspend fun submitSession(
        sessionId: String,
        request: SubmitSessionRequest
    ): SubmitSessionResponse {
        return withContext(Dispatchers.IO) {
            try {
                apiService.submitSession(sessionId, request)
            } catch (e: Exception) {
                SubmitSessionResponse(sessionId, EkycSessionStatus.FAILED)
            }
        }
    }

    override suspend fun getStatus(sessionId: String): StatusResponse {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getStatus(sessionId)
            } catch (e: Exception) {
                StatusResponse(sessionId, EkycSessionStatus.FAILED, 0, ProcessingStep.QUEUED)
            }
        }
    }

    override suspend fun getResult(sessionId: String): ResultResponse {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getResult(sessionId)
            } catch (e: Exception) {
                ResultResponse(sessionId, FinalDecision.REVIEW, errors = listOf(EkycError("NET_ERR", "Network error", true)))
            }
        }
    }

    override suspend fun verifyNfc(
        sessionId: String,
        request: VerifyNfcRequest
    ): VerifyNfcResponse {
        return withContext(Dispatchers.IO) {
            try {
                apiService.verifyNfc(sessionId, request)
            } catch (e: Exception) {
                VerifyNfcResponse(sessionId, false, "Lỗi mạng", error = EkycError("NET_ERR", "Network error", true))
            }
        }
    }

    override suspend fun getNfcKey(sessionId: String): com.dacn1.core.model.NfcKeyResponse {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getNfcKey(sessionId)
            } catch (e: Exception) {
                com.dacn1.core.model.NfcKeyResponse(sessionId, "error_key")
            }
        }
    }

    override suspend fun getErrorCatalog(): List<ErrorCatalogItem> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getErrorCatalog()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun getHistory(): List<VerificationResult> {
        return withContext(Dispatchers.IO) {
            try {
                apiService.getHistory()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun processFaceMatch(
        sessionId: String,
        documentFaceFileId: String,
        selfieFileId: String
    ): com.dacn1.core.model.FaceMatchResponse {
        return withContext(Dispatchers.IO) {
            val request = com.dacn1.core.model.FaceMatchRequest(documentFaceFileId, selfieFileId)
            apiService.processFaceMatch(sessionId, request)
        }
    }

    override suspend fun processLiveness(
        sessionId: String,
        videoFileId: String,
        expectedActions: List<String>
    ): com.dacn1.core.model.LivenessResponse {
        return withContext(Dispatchers.IO) {
            val request = com.dacn1.core.model.LivenessRequest(videoFileId, expectedActions)
            apiService.processLiveness(sessionId, request)
        }
    }

    override suspend fun finalizeSession(sessionId: String, consent: Boolean): com.dacn1.core.model.FinalizeResponse {
        return withContext(Dispatchers.IO) {
            val request = com.dacn1.core.model.FinalizeRequest(consent)
            apiService.finalizeSession(sessionId, request)
        }
    }

    override suspend fun processOcr(
        sessionId: String,
        frontFileId: String,
        backFileId: String,
        qrLocalPath: String?
    ): OcrResponse {
        return withContext(Dispatchers.IO) {
            try {
                var qrFileId: String? = null
                if (qrLocalPath != null) {
                    val qrFile = File(qrLocalPath)
                    val qrReqFile = qrFile.asRequestBody("image/*".toMediaTypeOrNull())
                    val qrPart = MultipartBody.Part.createFormData("file", qrFile.name, qrReqFile)
                    val qrTypeBody = "QR_CODE".toRequestBody("text/plain".toMediaTypeOrNull())
                    val response = apiService.uploadFile(sessionId, qrTypeBody, qrPart)
                    qrFileId = response.file_id ?: "QR_CODE"
                }

                val request = com.dacn1.core.model.OcrRequest(
                    front_file_id = frontFileId,
                    back_file_id = backFileId,
                    qr = qrFileId
                )

                apiService.processOcr(sessionId, request)
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: e.message()
                throw Exception("HTTP ${e.code()}: $errorBody")
            } catch (e: Exception) {
                throw e
            }
        }
    }
}
