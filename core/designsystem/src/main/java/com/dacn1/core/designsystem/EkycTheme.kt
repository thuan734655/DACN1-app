package com.dacn1.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.dacn1.core.designsystem.theme.BackgroundLight
import com.dacn1.core.designsystem.theme.EkycShapes
import com.dacn1.core.designsystem.theme.EkycTypography
import com.dacn1.core.designsystem.theme.ErrorRed
import com.dacn1.core.designsystem.theme.OutlineSoft
import com.dacn1.core.designsystem.theme.PrimaryOrange
import com.dacn1.core.designsystem.theme.SecondaryTeal
import com.dacn1.core.designsystem.theme.SurfaceLight

private val EkycColors = lightColorScheme(
    primary = PrimaryOrange,
    secondary = SecondaryTeal,
    error = ErrorRed,
    background = BackgroundLight,
    surface = SurfaceLight,
    outline = OutlineSoft
)

@Composable
fun EkycTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EkycColors,
        typography = EkycTypography,
        shapes = EkycShapes,
        content = content
    )
}
