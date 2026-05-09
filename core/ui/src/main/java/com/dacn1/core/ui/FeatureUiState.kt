package com.dacn1.core.ui

import com.dacn1.core.model.EkycSession
import com.dacn1.core.model.EkycSessionStatus

data class OnboardingUiState(
    val checkingService: Boolean = true,
    val serviceHealthy: Boolean = false,
    val consentChecked: Boolean = false,
    val session: EkycSession? = null,
    val canContinue: Boolean = false,
    val errorMessage: String? = null
)

data class DocumentUiState(
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val qualityWarnings: List<String> = emptyList(),
    val frontConfirmed: Boolean = false,
    val backConfirmed: Boolean = false,
    val currentStatus: EkycSessionStatus = EkycSessionStatus.COLLECTING,
    val canContinue: Boolean = false
)

data class SelfieUiState(
    val selfiePath: String? = null,
    val faceDetected: Boolean = false,
    val warningCode: String? = null,
    val confirmed: Boolean = false,
    val canContinue: Boolean = false
)

data class LivenessUiState(
    val challengeText: String = "Quay mat sang trai roi chop mat",
    val isRecording: Boolean = false,
    val recordedSeconds: Int = 0,
    val totalSeconds: Int = 5,
    val videoPath: String? = null,
    val qualityWarnings: List<String> = emptyList(),
    val canContinue: Boolean = false
)
