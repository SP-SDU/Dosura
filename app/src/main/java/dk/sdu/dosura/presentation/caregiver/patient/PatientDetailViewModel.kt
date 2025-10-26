package dk.sdu.dosura.presentation.caregiver.patient

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientDetailUiState(
    val patientName: String = "Patient",
    val medications: List<Medication> = emptyList(),
    val recentLogs: List<MedicationLog> = emptyList(),
    val adherencePercentage: Float = 0f,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val logRepository: MedicationLogRepository,
    private val caregiverLinkRepository: CaregiverLinkRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val patientId: String = savedStateHandle.get<String>("patientId") ?: ""

    private val _uiState = MutableStateFlow(PatientDetailUiState())
    val uiState: StateFlow<PatientDetailUiState> = _uiState.asStateFlow()

    init {
        loadPatientData()
    }

    private fun loadPatientData() {
        viewModelScope.launch {
            try {
                // Load patient's medications
                medicationRepository.getAllMedications(patientId).collect { medications ->
                    _uiState.value = _uiState.value.copy(
                        medications = medications,
                        isLoading = false
                    )
                    
                    // Calculate adherence
                    if (medications.isNotEmpty()) {
                        calculateAdherence(patientId)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    private fun calculateAdherence(userId: String) {
        viewModelScope.launch {
            try {
                // Get all logs for all medications of this user
                val allLogs = mutableListOf<MedicationLog>()
                _uiState.value.medications.forEach { medication ->
                    logRepository.getLogsForMedication(medication.id).collect { logs ->
                        allLogs.addAll(logs)
                    }
                }
                
                val takenCount = allLogs.count { 
                    it.status == dk.sdu.dosura.data.local.entity.LogStatus.TAKEN 
                }
                val totalCount = allLogs.count { 
                    it.status != dk.sdu.dosura.data.local.entity.LogStatus.PENDING 
                }
                
                val percentage = if (totalCount > 0) {
                    (takenCount.toFloat() / totalCount.toFloat()) * 100
                } else 0f
                
                _uiState.value = _uiState.value.copy(
                    adherencePercentage = percentage,
                    recentLogs = allLogs.sortedByDescending { it.scheduledTime }.take(5)
                )
            } catch (e: Exception) {
                // Adherence calculation is optional
            }
        }
    }
}
