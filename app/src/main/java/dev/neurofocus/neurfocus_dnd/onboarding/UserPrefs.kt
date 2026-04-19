package dev.neurofocus.neurfocus_dnd.onboarding

import android.content.Context
import android.content.SharedPreferences

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
        prefs.edit()
            .putString(KEY_FIRST_NAME, profile.firstName)
            .putString(KEY_LAST_NAME, profile.lastName)
            .putBoolean(KEY_ONBOARDED, true)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "neurofocus_user_prefs"
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_ONBOARDED = "is_onboarded"
    }
}
