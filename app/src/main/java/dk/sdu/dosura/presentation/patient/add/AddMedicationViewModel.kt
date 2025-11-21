package dk.sdu.dosura.presentation.patient.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.data.repository.MedicationRepository
import dk.sdu.dosura.notification.MedicationScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val preferencesManager: UserPreferencesManager,
    private val medicationScheduler: MedicationScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddMedicationUiState())
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()

    fun updateMedicationName(name: String) {
        _uiState.value = _uiState.value.copy(medicationName = name)
    }

    fun updateDosage(dosage: String) {
        _uiState.value = _uiState.value.copy(dosage = dosage)
    }

    fun updateInstructions(instructions: String) {
        _uiState.value = _uiState.value.copy(instructions = instructions)
    }

    fun updatePhotoUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    fun addReminderTime(time: String) {
        val currentTimes = _uiState.value.reminderTimes.toMutableList()
        if (!currentTimes.contains(time)) {
            currentTimes.add(time)
            _uiState.value = _uiState.value.copy(reminderTimes = currentTimes)
        }
    }

    fun removeReminderTime(time: String) {
        val currentTimes = _uiState.value.reminderTimes.toMutableList()
        currentTimes.remove(time)
        _uiState.value = _uiState.value.copy(reminderTimes = currentTimes)
    }

    fun toggleDayOfWeek(day: Int) {
        val currentDays = _uiState.value.daysOfWeek.toMutableList()
        if (currentDays.contains(day)) {
            currentDays.remove(day)
        } else {
            currentDays.add(day)
        }
        _uiState.value = _uiState.value.copy(daysOfWeek = currentDays.sorted())
    }

    fun saveMedication(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            
            if (state.medicationName.isBlank() || state.dosage.isBlank() || state.reminderTimes.isEmpty()) {
                _uiState.value = state.copy(error = "Please fill all required fields")
                return@launch
            }

            _uiState.value = state.copy(isLoading = true, error = null)

            try {
                preferencesManager.userPreferences.first().let { prefs ->
                    val medication = Medication(
                        name = state.medicationName,
                        dosage = state.dosage,
                        instructions = state.instructions,
                        photoUri = state.photoUri?.toString(),
                        userId = prefs.userId,
                        reminderTimes = state.reminderTimes,
                        daysOfWeek = state.daysOfWeek.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) },
                        startDate = Calendar.getInstance().timeInMillis
                    )
                    
                    val medicationId = medicationRepository.insertMedication(medication)
                    val savedMedication = medication.copy(id = medicationId)
                    medicationScheduler.scheduleMedicationReminders(savedMedication)
                    
                    _uiState.value = state.copy(isLoading = false, isSaved = true)
                    onSuccess()
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    error = "Failed to save medication: ${e.message}"
                )
            }
        }
    }
}

data class AddMedicationUiState(
    val medicationName: String = "",
    val dosage: String = "",
    val instructions: String = "",
    val photoUri: Uri? = null,
    val reminderTimes: List<String> = emptyList(),
    val daysOfWeek: List<Int> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)
