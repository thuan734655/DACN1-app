package com.dacn1.core.repository

import com.dacn1.core.model.CreateSessionRequest
import com.dacn1.core.model.CreateSessionResponse
import com.dacn1.core.model.ErrorCatalogItem
import com.dacn1.core.model.ResultResponse
import com.dacn1.core.model.StatusResponse
import com.dacn1.core.model.SubmitSessionRequest
import com.dacn1.core.model.SubmitSessionResponse
import com.dacn1.core.model.UploadFileResponse
import com.dacn1.core.model.UploadFileType
import com.dacn1.core.model.VerifyNfcRequest
import com.dacn1.core.model.VerifyNfcResponse
import com.dacn1.core.model.VerificationResult

interface EkycRepository {
    suspend fun createSession(request: CreateSessionRequest): CreateSessionResponse
    suspend fun uploadFile(sessionId: String, fileType: UploadFileType, localPath: String): UploadFileResponse
    suspend fun submitSession(sessionId: String, request: SubmitSessionRequest = SubmitSessionRequest()): SubmitSessionResponse
    suspend fun getStatus(sessionId: String): StatusResponse
    suspend fun getResult(sessionId: String): ResultResponse
    suspend fun verifyNfc(sessionId: String, request: VerifyNfcRequest): VerifyNfcResponse
    suspend fun getErrorCatalog(): List<ErrorCatalogItem>
    suspend fun getHistory(): List<VerificationResult>
}
