package dk.sdu.dosura.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dk.sdu.dosura.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dosura_preferences")

@Singleton
class UserPreferencesManager @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val USER_ROLE = stringPreferencesKey("user_role")
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                userId = preferences[USER_ID] ?: "",
                userRole = UserRole.valueOf(preferences[USER_ROLE] ?: UserRole.PATIENT.name),
                userName = preferences[USER_NAME] ?: "",
                isOnboardingCompleted = preferences[IS_ONBOARDING_COMPLETED] ?: false,
                themeMode = preferences[THEME_MODE] ?: "system",
                fontScale = preferences[FONT_SCALE] ?: 1.0f,
                vibrationEnabled = preferences[VIBRATION_ENABLED] ?: true,
                soundEnabled = preferences[SOUND_ENABLED] ?: true,
                notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true,
                language = preferences[LANGUAGE] ?: "en"
            )
        }

    suspend fun updateUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
        }
    }

    suspend fun updateUserRole(role: UserRole) {
        dataStore.edit { preferences ->
            preferences[USER_ROLE] = role.name
        }
    }

    suspend fun updateUserName(name: String) {
        dataStore.edit { preferences ->
            preferences[USER_NAME] = name
        }
    }

    suspend fun completeOnboarding() {
        dataStore.edit { preferences ->
            preferences[IS_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun updateThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun updateFontScale(scale: Float) {
        dataStore.edit { preferences ->
            preferences[FONT_SCALE] = scale
        }
    }

    suspend fun updateVibrationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SOUND_ENABLED] = enabled
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = language
        }
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

data class UserPreferences(
    val userId: String,
    val userRole: UserRole,
    val userName: String,
    val isOnboardingCompleted: Boolean,
    val themeMode: String,
    val fontScale: Float,
    val vibrationEnabled: Boolean,
    val soundEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val language: String
)
