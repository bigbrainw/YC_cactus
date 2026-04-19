package dev.neurofocus.neurfocus_dnd.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Thin SharedPreferences wrapper for the onboarding profile.
 *
 * Kept small on purpose — DataStore is overkill for three strings.
 * Migrate to Jetpack DataStore when the persistence surface grows.
 */
class UserPrefs(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfile(): UserProfile? {
        if (!prefs.getBoolean(KEY_ONBOARDED, false)) return null
        val first = prefs.getString(KEY_FIRST_NAME, null) ?: return null
        val last = prefs.getString(KEY_LAST_NAME, null) ?: return null
        return UserProfile(firstName = first, lastName = last)
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit {
            putString(KEY_FIRST_NAME, profile.firstName)
            putString(KEY_LAST_NAME, profile.lastName)
            putBoolean(KEY_ONBOARDED, true)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    /** Ensures a profile row exists — hackathon default, no onboarding flow. */
    fun ensureDefaultProfile(): UserProfile {
        getProfile()?.let { return it }
        val profile = UserProfile(firstName = DEFAULT_DISPLAY_NAME, lastName = "")
        saveProfile(profile)
        return profile
    }

    companion object {
        /** Shown in the shell until the user changes it in settings. */
        const val DEFAULT_DISPLAY_NAME = "User"

        private const val PREFS_NAME = "neurofocus_user_prefs"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_ONBOARDED = "is_onboarded"
    }
}
