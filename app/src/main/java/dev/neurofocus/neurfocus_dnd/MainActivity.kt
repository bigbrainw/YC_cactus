package dev.neurofocus.neurfocus_dnd

import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.neurofocus.neurfocus_dnd.brain.data.ble.BlePermissions
import dev.neurofocus.neurfocus_dnd.brain.ui.BrainScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.HomeScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.rememberBrainViewModel
import dev.neurofocus.neurfocus_dnd.brain.ui.SessionsScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.SettingsScreen
import dev.neurofocus.neurfocus_dnd.onboarding.OnboardingScreen
import dev.neurofocus.neurfocus_dnd.onboarding.UserPrefs
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroFloatingNav
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroGradientBackground
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroNavDestination
import dev.neurofocus.neurfocus_dnd.ui.shell.NeuroTopBar
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
    var profile by remember { mutableStateOf(prefs.getProfile()) }

    val current = profile
    if (current == null) {
        OnboardingScreen(
            onComplete = { newProfile ->
                prefs.saveProfile(newProfile)
                profile = newProfile
            },
        )
    } else {
        MainShell(
            profile = current,
            onResetOnboarding = {
                prefs.clear()
                profile = null
            },
        )
    }
}

@Composable
private fun MainShell(
    profile: UserProfile,
    onResetOnboarding: () -> Unit,
) {
    var destinationIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentDestination = AppDestinations.entries[destinationIndex]

    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val needsBle = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    val brainViewModel = rememberBrainViewModel()
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

    val destinations = AppDestinations.entries.map { dest ->
        NeuroNavDestination(label = dest.label, icon = dest.icon)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NeuroGradientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NeuroTopBar(
                onPowerClick = { /* reserved: device power UX */ },
                onNotificationsClick = { /* reserved */ },
                onProfileClick = {
                    destinationIndex = AppDestinations.entries.indexOf(AppDestinations.SETTINGS)
                },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(
                        profile = profile,
                        viewModel = brainViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppDestinations.BRAIN -> BrainScreen(
                        profile = profile,
                        viewModel = brainViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppDestinations.SESSIONS -> SessionsScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                    AppDestinations.SETTINGS -> SettingsScreen(
                        profile = profile,
                        onResetOnboarding = onResetOnboarding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            NeuroFloatingNav(
                destinations = destinations,
                selectedIndex = destinationIndex,
                onDestinationSelected = { index -> destinationIndex = index },
            )
        }
    }
}

private enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Outlined.Home),
    BRAIN("Brain", Icons.Outlined.Psychology),
    SESSIONS("Chart", Icons.Outlined.BarChart),
    SETTINGS("Settings", Icons.Outlined.Settings),
}
