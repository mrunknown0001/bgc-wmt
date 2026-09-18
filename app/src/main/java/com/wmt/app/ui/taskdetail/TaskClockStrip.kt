package com.wmt.app.ui.taskdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wmt.app.domain.model.ClockState
import com.wmt.app.domain.model.TaskClock

/**
 * The task clock: how long this has been in motion, and the one button that applies.
 *
 * Hidden entirely unless the project asks for it, which is a project setting rather than
 * something the app decides. The button follows the server's own reading of the state,
 * so it never offers a press that would be refused.
 */
@Composable
fun TaskClockStrip(
    clock: TaskClock,
    busy: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    /** Opens the timesheet. The strip is the natural way in: it is the figure it shows. */
    onOpenTimesheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (!clock.isVisible) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onOpenTimesheet != null) it.clickable(onClick = onOpenTimesheet) else it }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = if (clock.isRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = clock.headline(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                clock.subline()?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (clock.canControl) {
                Spacer(Modifier.width(8.dp))
                ClockButton(
                    state = clock.state,
                    busy = busy,
                    onStart = onStart,
                    onPause = onPause,
                    onResume = onResume,
                )
            }
        }
    }
}

@Composable
private fun ClockButton(
    state: ClockState,
    busy: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    if (busy) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        return
    }
    when (state) {
        ClockState.NOT_STARTED -> Button(onClick = onStart) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Start")
        }
        // Pausing is what records the time, so it leads rather than sitting as a
        // secondary action.
        ClockState.RUNNING -> Button(onClick = onPause) { Text("Pause") }
        ClockState.PAUSED -> OutlinedButton(onClick = onResume) { Text("Resume") }
    }
}

/** "In motion for 2h 15m", or what the state says when it is not running. */
private fun TaskClock.headline(): String = when (state) {
    ClockState.NOT_STARTED -> "Not started"
    ClockState.RUNNING -> timeInMotionMinutes
        ?.let { "In motion for ${formatMinutes(it)}" }
        ?: "In motion"
    ClockState.PAUSED -> "Paused"
}

private fun TaskClock.subline(): String? {
    val logged = loggedMinutes?.takeIf { it > 0 }?.let { "${formatMinutes(it)} logged" }
    val motion = timeInMotionMinutes
        ?.takeIf { state != ClockState.RUNNING && it > 0 }
        ?.let { "${formatMinutes(it)} in motion" }
    val parts = listOfNotNull(motion, logged)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Minutes as hours and minutes, which is how a day of work reads. */
internal fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
}
