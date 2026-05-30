package com.dacn1.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dacn1.core.navigation.Routes
import com.dacn1.core.repository.MockRepositoryProvider
import com.dacn1.core.repository.MockScenario
import com.dacn1.feature.document.DocumentFlowRoute
import com.dacn1.feature.liveness.LivenessFlowRoute
import com.dacn1.feature.onboarding.OnboardingFlowRoute
import com.dacn1.feature.selfie.SelfieFlowRoute
import com.dacn1.feature.verification.VerificationFlowRoute
import com.dacn1.app.ui.config.ServerConfigScreen
import com.dacn1.feature.document.qr.QrCaptureRoute
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.dacn1.core.network.NetworkProvider
import com.dacn1.core.repository.RemoteEkycRepository

@Composable
fun EkycNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("EkycConfig", Context.MODE_PRIVATE) }
    
    var ipAddress by remember { mutableStateOf(sharedPrefs.getString("SERVER_IP", "192.168.1.1") ?: "192.168.1.1") }
    var port by remember { mutableStateOf(sharedPrefs.getString("SERVER_PORT", "8080") ?: "8080") }

    val repository = remember(ipAddress, port) {
        val baseUrl = "http://$ipAddress:$port"
        val apiService = NetworkProvider.provideApiService(baseUrl)
        RemoteEkycRepository(apiService)
    }
    
    var sessionId by remember { mutableStateOf<String?>(null) }
    var frontFileId by remember { mutableStateOf<String?>(null) }
    var backFileId by remember { mutableStateOf<String?>(null) }

    NavHost(navController = navController, startDestination = Routes.SERVER_CONFIG) {
        composable(Routes.SERVER_CONFIG) {
            ServerConfigScreen(
                onConfigSaved = {
                    ipAddress = sharedPrefs.getString("SERVER_IP", "192.168.1.1") ?: "192.168.1.1"
                    port = sharedPrefs.getString("SERVER_PORT", "8080") ?: "8080"
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(Routes.SERVER_CONFIG) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SPLASH) {
            OnboardingFlowRoute(
                repository = repository,
                onSessionCreated = { createdSessionId ->
                    sessionId = createdSessionId
                },
                onCompleted = {
                    navController.navigate(Routes.DOCUMENT_GUIDE) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DOCUMENT_GUIDE) {
            val safeSessionId = sessionId
            if (safeSessionId.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Phiên không hợp lệ. Vui lòng bắt đầu lại.")
                }
            } else {
                DocumentFlowRoute(
                    repository = repository,
                    sessionId = safeSessionId,
                    onCompleted = {
                        navController.navigate(Routes.SELFIE_GUIDE)
                    },
                    onNfcPassed = {
                        navController.navigate(Routes.LIVENESS_GUIDE)
                    },
                    onExitToHome = {
                        sessionId = null
                        navController.navigate(Routes.SPLASH) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOcrProcessTriggered = { frontPath, backPath ->
                        frontFileId = frontPath
                        backFileId = backPath
                        navController.navigate(Routes.QR_CAPTURE)
                    }
                )
            }
        }

        composable(Routes.QR_CAPTURE) {
            val safeSessionId = sessionId
            if (safeSessionId.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Phiên không hợp lệ. Vui lòng bắt đầu lại.")
                }
            } else {
                QrCaptureRoute(
                    sessionId = safeSessionId,
                    frontFileId = frontFileId,
                    backFileId = backFileId,
                    repository = repository,
                    onCompleted = {
                        navController.navigate(Routes.SELFIE_GUIDE) {
                            popUpTo(Routes.QR_CAPTURE) { inclusive = true }
                        }
                    },
                    onRestartRequired = {
                        frontFileId = null
                        backFileId = null
                        navController.navigate(Routes.DOCUMENT_GUIDE) {
                            popUpTo(Routes.DOCUMENT_GUIDE) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.SELFIE_GUIDE) {
            val safeSessionId = sessionId
            if (safeSessionId.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Phiên không hợp lệ. Vui lòng bắt đầu lại.")
                }
            } else {
                SelfieFlowRoute(
                    repository = repository,
                    sessionId = safeSessionId,
                    frontFileId = frontFileId,
                    onCompleted = { navController.navigate(Routes.LIVENESS_GUIDE) }
                )
            }
        }

        composable(Routes.LIVENESS_GUIDE) {
            val safeSessionId = sessionId
            if (safeSessionId.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Phiên không hợp lệ. Vui lòng bắt đầu lại.")
                }
            } else {
                LivenessFlowRoute(
                    repository = repository,
                    sessionId = safeSessionId,
                    onCompleted = { navController.navigate(Routes.VERIFICATION) }
                )
            }
        }

        composable(Routes.VERIFICATION) {
            val safeSessionId = sessionId
            if (safeSessionId.isNullOrBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Phiên không hợp lệ. Vui lòng bắt đầu lại.")
                }
            } else {
                VerificationFlowRoute(
                    repository = repository,
                    sessionId = safeSessionId,
                    onDone = { navController.navigate(Routes.HISTORY_SUPPORT) }
                )
            }
        }

        composable(Routes.HISTORY_SUPPORT) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Đã hoàn tất luồng eKYC mock.")
            }
        }
    }
}
