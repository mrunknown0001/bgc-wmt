package com.wmt.app.ui.components

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.UserSummary

/**
 * The signed-in person's avatar, as a top-app-bar action that opens Profile.
 *
 * Profile is reached this way rather than from the navigation bar, whose five slots go
 * to the destinations people move between all day. Every top-level screen shows it in
 * the same place so the way out is always where it was last time.
 */
@Composable
fun ProfileAvatarAction(
    user: UserSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        // One label for the whole button: the avatar underneath announces a name or a
        // placeholder initial, neither of which says what tapping it does.
        modifier = modifier.clearAndSetSemantics { contentDescription = "Profile and settings" },
    ) {
        UserAvatar(user = user, size = 28.dp)
    }
}
