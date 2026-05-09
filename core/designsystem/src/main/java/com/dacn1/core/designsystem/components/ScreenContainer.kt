package com.dacn1.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dacn1.core.designsystem.theme.Spacing

@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(Spacing.Md),
    content: @Composable () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            content()
        }
    }
}
