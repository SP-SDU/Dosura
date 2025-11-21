package dk.sdu.dosura.presentation.caregiver.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import dk.sdu.dosura.p2p.P2PLinkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LinkPatientViewModel @Inject constructor(
    private val caregiverLinkRepository: CaregiverLinkRepository,
    private val preferencesManager: UserPreferencesManager,
    private val p2pLinkManager: P2PLinkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkPatientUiState())
    val uiState: StateFlow<LinkPatientUiState> = _uiState.asStateFlow()

    fun updateLinkCode(code: String) {
        _uiState.value = _uiState.value.copy(linkCode = code, error = null)
    }

    fun linkPatient(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            
            if (state.linkCode.length != 6) {
                _uiState.value = state.copy(error = "Link code must be 6 digits")
                return@launch
            }

            _uiState.value = state.copy(isLoading = true, error = null)

            try {
                val prefs = preferencesManager.userPreferences.first()
                val caregiverId = prefs.userId
                
                // Use P2P to find and link to patient
                val success = p2pLinkManager.linkToPatient(caregiverId, state.linkCode)
                
                if (success) {
                    _uiState.value = state.copy(isLoading = false, isLinked = true)
                    onSuccess()
                } else {
                    _uiState.value = state.copy(
                        isLoading = false,
                        error = "Could not find patient. Make sure patient is nearby and has generated the code."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = "Failed to link patient: ${e.message}"
                )
            }
        }
    }
}

data class LinkPatientUiState(
    val linkCode: String = "",
    val isLoading: Boolean = false,
    val isLinked: Boolean = false,
    val error: String? = null
)
