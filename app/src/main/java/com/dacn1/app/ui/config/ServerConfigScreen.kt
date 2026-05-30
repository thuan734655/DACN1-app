package com.dacn1.app.ui.config

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.dacn1.core.designsystem.components.PrimaryButton
import com.dacn1.core.designsystem.components.ScreenContainer
import com.dacn1.core.designsystem.theme.Spacing

@Composable
fun ServerConfigScreen(
    onConfigSaved: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("EkycConfig", Context.MODE_PRIVATE) }
    
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        ipAddress = sharedPrefs.getString("SERVER_IP", "192.168.1.1") ?: "192.168.1.1"
        port = sharedPrefs.getString("SERVER_PORT", "8080") ?: "8080"
    }

    ScreenContainer {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text("Cấu hình Máy chủ", style = MaterialTheme.typography.headlineLarge)
            Text("Vui lòng nhập địa chỉ IP và Port của máy chủ API để kết nối.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(Spacing.Sm))
            
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { 
                    ipAddress = it
                    errorMsg = null
                },
                label = { Text("Địa chỉ IP hoặc Tên miền") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = port,
                onValueChange = { 
                    port = it
                    errorMsg = null
                },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            if (errorMsg != null) {
                Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error)
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            PrimaryButton(
                text = "Lưu và Tiếp tục",
                onClick = {
                    if (ipAddress.isBlank() || port.isBlank()) {
                        errorMsg = "IP và Port không được để trống"
                    } else {
                        sharedPrefs.edit()
                            .putString("SERVER_IP", ipAddress.trim())
                            .putString("SERVER_PORT", port.trim())
                            .apply()
                        onConfigSaved()
                    }
                }
            )
        }
    }
}
