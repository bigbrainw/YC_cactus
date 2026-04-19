package dev.neurofocus.neurfocus_dnd

import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BlePermissions
import dev.neurofocus.neurfocus_dnd.brain.ui.BrainScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.SettingsScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.rememberBrainViewModel
import dev.neurofocus.neurfocus_dnd.onboarding.UserPrefs
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroGradientBackground
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroTopBar
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroTokens
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = UserPrefs(applicationContext)
        setContent {
            NeurfocusdndTheme {
                AppRoot(prefs = prefs)
            }
        }
    }
}

@Composable
private fun AppRoot(prefs: UserPrefs) {
    var profile by remember { mutableStateOf(prefs.ensureDefaultProfile()) }
    MainShell(
        profile = profile,
        onResetProfile = {
            prefs.clear()
            profile = prefs.ensureDefaultProfile()
        },
    )
}

@Composable
private fun MainShell(
    profile: UserProfile,
    onResetProfile: () -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val needsBle = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    val brainViewModel = rememberBrainViewModel()
    val brainState by brainViewModel.state.collectAsStateWithLifecycle()
    val blePickerOpen by brainViewModel.blePickerOpen.collectAsStateWithLifecycle()
    val blePickerBusy by brainViewModel.blePickerBusy.collectAsStateWithLifecycle()
    val blePickerDevices by brainViewModel.blePickerDevices.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            brainViewModel.retryConnect()
        }
    }
    LaunchedEffect(needsBle) {
        if (needsBle && !BlePermissions.hasAll(app)) {
            permissionLauncher.launch(BlePermissions.requiredPermissions())
        }
    }

    BackHandler(enabled = settingsOpen) {
        settingsOpen = false
    }

    BleDevicePickerDialog(
        visible = blePickerOpen,
        busy = blePickerBusy,
        devices = blePickerDevices,
        onDismiss = { brainViewModel.dismissBleDevicePicker() },
        onRefresh = { brainViewModel.refreshBleDeviceList() },
        onSelect = { brainViewModel.connectBleDevice(it) },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NeuroGradientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NeuroTopBar(
                onBleDevicesClick = {
                    if (needsBle) {
                        if (!BlePermissions.hasAll(app)) {
                            permissionLauncher.launch(BlePermissions.requiredPermissions())
                        } else {
                            brainViewModel.openBleDevicePicker()
                        }
                    }
                },
                onNotificationsClick = { },
                onProfileClick = { settingsOpen = true },
                isConnected = brainState is dev.neurofocus.neurfocus_dnd.brain.domain.BrainState.Live,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = NeuroTokens.shellContentHorizontal),
            ) {
                if (settingsOpen) {
                    SettingsScreen(
                        profile = profile,
                        onResetProfile = onResetProfile,
                        onNavigateBack = { settingsOpen = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BrainScreen(
                        profile = profile,
                        viewModel = brainViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BleDevicePickerDialog(
    visible: Boolean,
    busy: Boolean,
    devices: List<BleDeviceCandidate>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "NeuroFocus headband") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NeuroTokens.spaceSm)) {
                when {
                    busy -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NeuroTokens.spaceMd),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(NeuroTokens.spaceXs),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Scanning nearby devices…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    devices.isEmpty() -> Text(
                        text = "No matching devices yet. Turn the headband on and tap Scan again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(NeuroTokens.spaceXs),
                    ) {
                        items(devices, key = { it.address }) { device ->
                            TextButton(
                                onClick = { onSelect(device.address) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        text = device.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !busy) {
                Text("Scan again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

