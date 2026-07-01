package com.wmt.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wmt.app.domain.model.UserSummary

// Distinct, legible-on-white-text background colors picked deterministically per user.
private val avatarColors = listOf(
    Color(0xFF1565C0), Color(0xFF6A1B9A), Color(0xFF2E7D32), Color(0xFFEF6C00),
    Color(0xFFC2185B), Color(0xFF00838F), Color(0xFF4527A0), Color(0xFFAD1457),
    Color(0xFF00695C), Color(0xFF5D4037), Color(0xFF283593), Color(0xFFD84315),
)

@Composable
fun UserAvatar(
    user: UserSummary?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val shape = CircleShape
    val url = user?.avatarUrl
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = user.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape),
        )
    } else {
        val bg = user?.let { avatarColors[it.id.mod(avatarColors.size)] }
            ?: MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user?.initials ?: "?",
                color = if (user != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value / 2.4f).sp,
            )
        }
    }
}
