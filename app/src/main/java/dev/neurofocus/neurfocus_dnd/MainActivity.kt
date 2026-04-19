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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.neurofocus.neurfocus_dnd.brain.data.BleDeviceCandidate
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BlePermissions
import dev.neurofocus.neurfocus_dnd.brain.domain.BrainState
import dev.neurofocus.neurfocus_dnd.brain.ui.BrainScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.CactusSettingsViewModel
import dev.neurofocus.neurfocus_dnd.brain.ui.DebugScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.SettingsScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.rememberBrainViewModel
import dev.neurofocus.neurfocus_dnd.onboarding.UserPrefs
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroGradientBackground
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroTopBar
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavBar
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroNavy
import dev.neurofocus.neurfocus_dnd.ui.theme.NeuroSkyBlue
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

private enum class NavTab { Home, Debug }

@Composable
private fun MainShell(
    profile: UserProfile,
    onResetProfile: () -> Unit,
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(NavTab.Home.ordinal) }
    val currentTab = NavTab.entries[selectedTab]

    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val needsBle = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    val brainViewModel = rememberBrainViewModel()
    val cactusSettingsViewModel: CactusSettingsViewModel = viewModel(
        factory = remember(app) { CactusSettingsViewModel.factory(app) },
    )
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
            // Only show top bar on Home tab (not Debug — debug uses full screen real estate)
            if (currentTab == NavTab.Home) {
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
                    isConnected = brainState is BrainState.Live,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (currentTab == NavTab.Debug) 0.dp else NeuroTokens.shellContentHorizontal),
            ) {
                when {
                    settingsOpen && currentTab == NavTab.Home -> {
                        SettingsScreen(
                            profile = profile,
                            onResetProfile = onResetProfile,
                            cactusSettingsViewModel = cactusSettingsViewModel,
                            onNavigateBack = { settingsOpen = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    currentTab == NavTab.Home -> {
                        BrainScreen(
                            profile = profile,
                            viewModel = brainViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    currentTab == NavTab.Debug -> {
                        DebugScreen(
                            viewModel = brainViewModel,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Bottom Navigation Bar
            NavigationBar(
                containerColor = NeuroNavBar,
                contentColor = NeuroSkyBlue,
            ) {
                NavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { selectedTab = tab.ordinal },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeuroNavy,
                            selectedTextColor = NeuroSkyBlue,
                            indicatorColor = NeuroSkyBlue,
                            unselectedIconColor = NeuroSkyBlue.copy(alpha = 0.55f),
                            unselectedTextColor = NeuroSkyBlue.copy(alpha = 0.55f),
                        ),
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    NavTab.Home  -> Icons.Default.Home
                                    NavTab.Debug -> Icons.Default.BugReport
                                },
                                contentDescription = tab.name,
                            )
                        },
                        label = { Text(tab.name) },
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
