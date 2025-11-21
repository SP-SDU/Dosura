package dk.sdu.dosura.presentation.patient.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.local.entity.LogStatus
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import dk.sdu.dosura.notification.MedicationScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MedicationDialogViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationRepository: MedicationRepository,
    private val medicationScheduler: MedicationScheduler
) : ViewModel() {

    fun takeMedication(medicationId: Long, scheduledTime: Long) {
        viewModelScope.launch {
            val log = MedicationLog(
                medicationId = medicationId,
                scheduledTime = scheduledTime,
                takenTime = System.currentTimeMillis(),
                status = LogStatus.TAKEN
            )
            medicationLogRepository.insertLog(log)
        }
    }

    fun snoozeMedication(medicationId: Long, medicationName: String) {
        viewModelScope.launch {
            medicationRepository.getMedicationById(medicationId).firstOrNull()?.let { medication ->
                medicationScheduler.scheduleSnoozeReminder(medication, medicationName, 10)
            }
        }
    }
}
