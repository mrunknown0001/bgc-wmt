package com.wmt.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.domain.model.NotificationTarget
import com.wmt.app.fcm.PushPayload
import com.wmt.app.ui.navigation.WmtApp
import com.wmt.app.ui.theme.ThemeViewModel
import com.wmt.app.ui.theme.WmtTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Deep links extracted from notification-tap intents, replayed to the nav layer. */
    private val deepLinks = MutableSharedFlow<NotificationTarget>(replay = 1, extraBufferCapacity = 4)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        handleIntent(intent)

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            WmtTheme(darkTheme = darkTheme) {
                WmtApp(intentDeepLinks = deepLinks)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Reads the tap target back out of the launch intent.
     *
     * The extras are turned back into the data map the push arrived as and routed by
     * [PushPayload], so the notification this app posted and one FCM displayed by
     * itself — which forwards the data payload as string extras — land in the same place.
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val data = PushPayload.TARGET_KEYS.mapNotNull { key ->
            intent.extraAsString(key)?.let { key to it }
        }.toMap()
        PushPayload.targetOf(data)?.let(deepLinks::tryEmit)
    }

    /** Extras arrive as ints from this app and as strings from FCM; accept either. */
    private fun Intent.extraAsString(key: String): String? =
        getStringExtra(key) ?: getIntExtra(key, MISSING_ID).takeIf { it != MISSING_ID }?.toString()

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        // Deliberately the server's own data keys: FCM hands a notification-payload
        // message straight to the launch intent under these names.
        const val EXTRA_TYPE = "type"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_APPROVAL_PROJECT_ID = "approval_project_id"
        const val EXTRA_APPROVAL_REQUEST_ID = "approval_item_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private const val MISSING_ID = -1
    }
}
