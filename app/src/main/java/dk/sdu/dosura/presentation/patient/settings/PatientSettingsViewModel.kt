package dk.sdu.dosura.presentation.patient.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientSettingsViewModel @Inject constructor(
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    fun logout() {
        viewModelScope.launch {
            // Reset onboarding completed flag to show role selection again
            // Keep user data (medications) intact
            preferencesManager.clearAllPreferences()
        }
    }
}
