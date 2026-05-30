package com.dacn1.core.network

import com.dacn1.core.model.CreateSessionRequest
import com.dacn1.core.model.CreateSessionResponse
import com.dacn1.core.model.ErrorCatalogItem
import com.dacn1.core.model.OcrRequest
import com.dacn1.core.model.OcrResponse
import com.dacn1.core.model.ResultResponse
import com.dacn1.core.model.StatusResponse
import com.dacn1.core.model.SubmitSessionRequest
import com.dacn1.core.model.SubmitSessionResponse
import com.dacn1.core.model.UploadFileResponse
import com.dacn1.core.model.VerificationResult
import com.dacn1.core.model.VerifyNfcRequest
import com.dacn1.core.model.VerifyNfcResponse
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface EkycApiService {
    @POST("/v1/ekyc/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): CreateSessionResponse

    @Multipart
    @POST("/v1/ekyc/sessions/{session_id}/files")
    suspend fun uploadFile(
        @Path("session_id") sessionId: String,
        @Part("file_type") fileType: okhttp3.RequestBody,
        @Part file: MultipartBody.Part
    ): UploadFileResponse

    @POST("/v1/ekyc/sessions/{session_id}/submit")
    suspend fun submitSession(
        @Path("session_id") sessionId: String,
        @Body request: SubmitSessionRequest
    ): SubmitSessionResponse

    @GET("/v1/ekyc/sessions/{session_id}/status")
    suspend fun getStatus(@Path("session_id") sessionId: String): StatusResponse

    @GET("/v1/ekyc/sessions/{session_id}/result")
    suspend fun getResult(@Path("session_id") sessionId: String): ResultResponse

    @POST("/v1/ekyc/sessions/{session_id}/nfc")
    suspend fun verifyNfc(
        @Path("session_id") sessionId: String,
        @Body request: VerifyNfcRequest
    ): VerifyNfcResponse

    @GET("/v1/ekyc/errors")
    suspend fun getErrorCatalog(): List<ErrorCatalogItem>

    @GET("/v1/ekyc/history")
    suspend fun getHistory(): List<VerificationResult>

    @POST("/v1/ekyc/sessions/{session_id}/ocr")
    suspend fun processOcr(
        @Path("session_id") sessionId: String,
        @Body request: OcrRequest
    ): OcrResponse
}
