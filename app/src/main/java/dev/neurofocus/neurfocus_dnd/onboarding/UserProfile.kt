package dev.neurofocus.neurfocus_dnd.onboarding

/**
 * The minimum profile needed to personalize the dashboard and Gemma advice.
 * Persisted via [UserPrefs] after onboarding completes.
 */
data class UserProfile(
    val firstName: String,
    val lastName: String,
) {
    val displayName: String
        get() = if (lastName.isBlank()) firstName else "$firstName $lastName"
}
