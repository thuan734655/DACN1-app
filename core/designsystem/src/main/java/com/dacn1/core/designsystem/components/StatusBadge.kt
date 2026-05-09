package com.dacn1.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dacn1.core.designsystem.theme.SuccessBg
import com.dacn1.core.designsystem.theme.SuccessText
import com.dacn1.core.designsystem.theme.WarningBg
import com.dacn1.core.designsystem.theme.WarningText

enum class BadgeType { Success, Warning, Error }

@Composable
fun StatusBadge(
    text: String,
    type: BadgeType,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (type) {
        BadgeType.Success -> SuccessBg to SuccessText
        BadgeType.Warning -> WarningBg to WarningText
        BadgeType.Error -> Color(0xFFFEE4E2) to Color(0xFFB42318)
    }

    Text(
        text = text,
        color = fg,
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
