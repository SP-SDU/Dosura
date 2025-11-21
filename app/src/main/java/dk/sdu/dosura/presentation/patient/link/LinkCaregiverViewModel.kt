package dk.sdu.dosura.presentation.patient.link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.local.entity.CaregiverLink
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import dk.sdu.dosura.p2p.P2PLinkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class LinkCaregiverUiState(
    val linkCode: String = "",
    val isLoading: Boolean = false,
    val isCodeGenerated: Boolean = false,
    val linkedCaregivers: List<CaregiverLink> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class LinkCaregiverViewModel @Inject constructor(
    private val caregiverLinkRepository: CaregiverLinkRepository,
    private val userPreferencesManager: UserPreferencesManager,
    private val p2pLinkManager: P2PLinkManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkCaregiverUiState())
    val uiState: StateFlow<LinkCaregiverUiState> = _uiState.asStateFlow()

    init {
        loadLinkedCaregivers()
    }

    fun generateLinkCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val prefs = userPreferencesManager.userPreferences.first()
                val userId = prefs.userId
                
                val code = generateUniqueCode()
                
                val caregiverLink = CaregiverLink(
                    patientId = userId,
                    caregiverId = "pending",
                    patientName = "Patient",
                    caregiverName = "Pending",
                    linkCode = code,
                    isActive = false,
                    createdAt = System.currentTimeMillis()
                )
                
                caregiverLinkRepository.insertLink(caregiverLink)
                
                // Start P2P advertising
                p2pLinkManager.startPatientMode(userId, code)
                
                _uiState.value = _uiState.value.copy(
                    linkCode = code,
                    isCodeGenerated = true,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to generate code: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun loadLinkedCaregivers() {
        viewModelScope.launch {
            try {
                val userId = userPreferencesManager.userPreferences.first().userId
                caregiverLinkRepository.getLinksForPatient(userId).collect { links ->
                    _uiState.value = _uiState.value.copy(
                        linkedCaregivers = links.filter { it.isActive }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load caregivers: ${e.message}"
                )
            }
        }
    }

    fun removeCaregiver(link: CaregiverLink) {
        viewModelScope.launch {
            try {
                caregiverLinkRepository.deactivateLink(link.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to remove caregiver: ${e.message}"
                )
            }
        }
    }

    private fun generateUniqueCode(): String {
        // Generate a 6-digit code
        return Random.nextInt(100000, 999999).toString()
    }
}
