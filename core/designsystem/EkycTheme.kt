package com.dacn1.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EkycColors = lightColorScheme(
    primary = Color(0xFFD66D2E),
    secondary = Color(0xFF0F766E),
    error = Color(0xFFB42318)
)

@Composable
fun EkycTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EkycColors,
        content = content
    )
}
