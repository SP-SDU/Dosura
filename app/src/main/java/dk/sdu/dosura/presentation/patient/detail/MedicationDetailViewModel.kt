package dk.sdu.dosura.presentation.patient.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import dk.sdu.dosura.notification.MedicationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MedicationDetailUiState(
    val medication: Medication? = null,
    val logs: List<MedicationLog> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class MedicationDetailViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val logRepository: MedicationLogRepository,
    private val medicationScheduler: MedicationScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // medicationId may be passed as Long (NavType.LongType), Int or as String (from other flows)
    private val medicationId: Long = run {
        val raw: Any? = savedStateHandle.get<Any>("medicationId")
        when (raw) {
            is Long -> raw
            is Int -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private val _uiState = MutableStateFlow(MedicationDetailUiState())
    val uiState: StateFlow<MedicationDetailUiState> = _uiState.asStateFlow()

    init {
        loadMedication()
    }

    private fun loadMedication() {
        viewModelScope.launch {
            try {
                medicationRepository.getMedicationById(medicationId).collect { medication ->
                    if (medication != null) {
                        loadLogs(medication.id)
                        _uiState.value = _uiState.value.copy(
                            medication = medication,
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            error = "Medication not found",
                            isLoading = false
                        )
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

    private fun loadLogs(medId: Long) {
        viewModelScope.launch {
            try {
                logRepository.getLogsForMedication(medId).collect { logs ->
                    _uiState.value = _uiState.value.copy(logs = logs)
                }
            } catch (e: Exception) {
            }
        }
    }

    fun deleteMedication(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value.medication?.let {
                    medicationScheduler.cancelMedicationReminders(it.id)
                    medicationRepository.deleteMedication(it)
                    onDeleted()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }
}
