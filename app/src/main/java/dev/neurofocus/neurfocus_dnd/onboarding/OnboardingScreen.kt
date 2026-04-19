package dev.neurofocus.neurfocus_dnd.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.neurofocus.neurfocus_dnd.ui.theme.NeurfocusdndTheme
import kotlinx.coroutines.delay

/**
 * Two-step onboarding: Welcome (name capture) → Connecting (BLE pairing).
 * On successful pairing, [onComplete] is invoked with the captured profile.
 *
 * The Connecting step currently fakes pairing with a 2.5s delay so the rest
 * of the app can develop without hardware. Real BLE scan replaces this in
 * Phase 4 — the screen contract does not change.
 */
@Composable
fun OnboardingScreen(
    onComplete: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }

    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(
            firstName = firstName,
            lastName = lastName,
            onFirstNameChange = { firstName = it },
            onLastNameChange = { lastName = it },
            onNext = { step = OnboardingStep.Connecting },
            modifier = modifier,
        )
        OnboardingStep.Connecting -> ConnectingStep(
            modifier = modifier,
            onPaired = {
                onComplete(
                    UserProfile(
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                    )
                )
            },
        )
    }
}

private enum class OnboardingStep { Welcome, Connecting }

@Composable
private fun WelcomeStep(
    firstName: String,
    lastName: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canProceed = firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "NeuroFocus",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(56.dp))
        Text(
            text = "Hi, I'm",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = { Text("First name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = { Text("Last name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(48.dp))
        AnimatedVisibility(visible = canProceed) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun ConnectingStep(
    onPaired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "pairing-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )

    LaunchedEffect(Unit) {
        delay(FAKE_PAIR_DELAY_MS)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onPaired()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.height(48.dp))
        Text(
            text = "Searching for your NeuroFocus device…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

private const val FAKE_PAIR_DELAY_MS = 2_500L

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun WelcomeStepPreview() {
    NeurfocusdndTheme {
        WelcomeStep(
            firstName = "",
            lastName = "",
            onFirstNameChange = {},
            onLastNameChange = {},
            onNext = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ConnectingStepPreview() {
    NeurfocusdndTheme {
        ConnectingStep(onPaired = {})
    }
}
