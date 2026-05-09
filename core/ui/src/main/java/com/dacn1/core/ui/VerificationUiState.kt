package com.dacn1.core.ui

import com.dacn1.core.model.EkycSessionStatus
import com.dacn1.core.model.FinalDecision
import com.dacn1.core.model.ProcessingStatus
import com.dacn1.core.model.UploadItem
import com.dacn1.core.model.VerificationResult

data class VerificationUiState(
    val uploads: List<UploadItem> = emptyList(),
    val processing: ProcessingStatus? = null,
    val finalResult: VerificationResult? = null,
    val status: EkycSessionStatus = EkycSessionStatus.COLLECTING,
    val isSubmitting: Boolean = false,
    val canRetry: Boolean = false
)

data class ResultUiState(
    val decision: FinalDecision? = null,
    val result: VerificationResult? = null,
    val primaryActionText: String = "Hoan tat",
    val secondaryActionText: String = "Thu lai",
    val showDetail: Boolean = true
)

data class HistoryItemUi(
    val sessionId: String,
    val createdAtIso: String,
    val decision: FinalDecision,
    val subtitle: String
)

data class HistoryUiState(
    val items: List<HistoryItemUi> = emptyList(),
    val isLoading: Boolean = false,
    val selectedFilter: String = "ALL",
    val errorMessage: String? = null
)
