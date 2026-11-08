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
import com.wmt.app.ui.theme.AvatarHues

// Backgrounds picked deterministically per user, from the shared identity hues
// in the theme -- each already dark enough to carry white initials at AA.

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
        val bg = user?.let { AvatarHues[it.id.mod(AvatarHues.size)] }
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
