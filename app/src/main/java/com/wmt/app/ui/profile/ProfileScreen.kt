package com.wmt.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wmt.app.BuildConfig
import com.wmt.app.domain.model.User
import com.wmt.app.domain.model.UserSummary
import com.wmt.app.ui.components.UserAvatar
import com.wmt.app.ui.components.WmtTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onChangeServer: () -> Unit,
    onLoggedOut: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pushDiag by viewModel.pushDiag.collectAsStateWithLifecycle()
    val cpState by viewModel.changePassword.collectAsStateWithLifecycle()
    val soState by viewModel.signOutOthers.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showSignOutOthers by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // On success, close the dialog and confirm via snackbar. Errors stay inline in the dialog.
    LaunchedEffect(soState.success) {
        val message = soState.success ?: return@LaunchedEffect
        showSignOutOthers = false
        snackbarHostState.showSnackbar(message)
        viewModel.clearSignOutOthersState()
    }

    if (showSignOutOthers) {
        LogoutOtherDevicesDialog(
            submitting = soState.submitting,
            error = soState.error,
            onDismiss = {
                showSignOutOthers = false
                viewModel.clearSignOutOthersState()
            },
            onConfirm = { password -> viewModel.logoutOtherDevices(password) },
        )
    }

    // On a successful password change, confirm via snackbar then force a re-login so the
    // session is re-established with the new password (in case the server rotated the token).
    // Note: don't clear the cp-state here — mutating cpState.success would cancel this effect
    // before logout runs. logout() flips isLoggedIn, which routes the app back to Login.
    LaunchedEffect(cpState.success) {
        val message = cpState.success ?: return@LaunchedEffect
        showChangePassword = false
        snackbarHostState.showSnackbar("$message Please sign in again.")
        viewModel.logout(onLoggedOut)
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            submitting = cpState.submitting,
            error = cpState.error,
            onDismiss = {
                showChangePassword = false
                viewModel.clearChangePasswordState()
            },
            onSubmit = { current, new, confirm -> viewModel.changePassword(current, new, confirm) },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            WmtTopAppBar(
                title = "Profile",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    // Profile is pushed from the top-bar avatar rather than being a
                    // navigation-bar destination, so it needs a way back.
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileHeaderCard(user = state.user)

            AppearanceSection(current = state.themeMode, onSelect = viewModel::setTheme)

            ChangePasswordSection(onClick = { showChangePassword = true })

            SignOutOtherDevicesSection(onClick = { showSignOutOthers = true })

            ServerSection(
                serverUrl = state.serverUrl,
                onChangeServer = { viewModel.changeServer(onChangeServer) },
            )

            PushDiagnosticsSection(
                result = pushDiag,
                onTest = viewModel::runPushDiagnostics,
            )

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Logout")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "WMT v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLoggedOut)
                    },
                ) {
                    Text("Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(current: String, onSelect: (String) -> Unit) {
    val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = current == option.first,
                        onClick = { onSelect(option.first) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(option.second)
                    }
                }
            }
        }
    }
}

@Composable
private fun PushDiagnosticsSection(result: String?, onTest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Push diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Fetches this device's FCM token and registers it with the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
                Text("Test push registration")
            }
            if (result != null) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(user: User?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (user == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    UserAvatar(
                        user = UserSummary(user.id, user.name, null),
                        size = 64.dp,
                    )
                    Column {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        user.position?.let { position ->
                            Text(
                                text = position,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                user.department?.let { department ->
                    LabeledRow(label = "Department", value = department.name)
                }
                user.team?.let { team ->
                    LabeledRow(label = "Team", value = team.name)
                }

                if (user.roles.isNotEmpty()) {
                    Text(
                        text = user.roles.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChangePasswordSection(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Change password") },
            supportingContent = { Text("Update your account password") },
            leadingContent = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
private fun SignOutOtherDevicesSection(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Log out other devices") },
            supportingContent = { Text("Sign out of all other sessions") },
            leadingContent = {
                Icon(Icons.Default.Devices, contentDescription = null)
            },
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
private fun ServerSection(
    serverUrl: String?,
    onChangeServer: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Change Server URL") },
            supportingContent = {
                Text(serverUrl ?: "Not set")
            },
            modifier = Modifier.clickable(onClick = onChangeServer),
        )
    }
}

