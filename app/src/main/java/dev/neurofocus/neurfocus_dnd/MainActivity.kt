package dev.neurofocus.neurfocus_dnd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.neurofocus.neurfocus_dnd.brain.ui.BrainScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.SessionsScreen
import dev.neurofocus.neurfocus_dnd.brain.ui.SettingsScreen
import dev.neurofocus.neurfocus_dnd.onboarding.OnboardingScreen
import dev.neurofocus.neurfocus_dnd.onboarding.UserPrefs
import dev.neurofocus.neurfocus_dnd.onboarding.UserProfile
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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.BRAIN) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination },
                )
            }
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.BRAIN -> BrainScreen(
                    profile = profile,
                    modifier = Modifier.padding(innerPadding),
                )
                AppDestinations.SESSIONS -> SessionsScreen(
                    modifier = Modifier.padding(innerPadding),
                )
                AppDestinations.SETTINGS -> SettingsScreen(
                    profile = profile,
                    onResetOnboarding = onResetOnboarding,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    BRAIN("Brain", R.drawable.ic_home),
    SESSIONS("Sessions", R.drawable.ic_favorite),
    SETTINGS("Settings", R.drawable.ic_account_box),
}
