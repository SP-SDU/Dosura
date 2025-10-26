package dk.sdu.dosura.presentation.patient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.data.local.entity.MotivationalMessage
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import dk.sdu.dosura.data.repository.MessageRepository
import dk.sdu.dosura.domain.model.AdherenceStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PatientHomeViewModel @Inject constructor(
    private val medicationRepository: MedicationRepository,
    private val logRepository: MedicationLogRepository,
    private val messageRepository: MessageRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val userPreferences = preferencesManager.userPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val medications: StateFlow<List<Medication>> = userPreferences
        .filterNotNull()
        .flatMapLatest { prefs ->
            medicationRepository.getAllMedications(prefs.userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<MedicationLog>> = userPreferences
        .filterNotNull()
        .flatMapLatest { prefs ->
            logRepository.getRecentLogs(prefs.userId, limit = 20)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadMessages: StateFlow<List<MotivationalMessage>> = userPreferences
        .filterNotNull()
        .flatMapLatest { prefs ->
            messageRepository.getUnreadMessages(prefs.userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _adherenceStats = MutableStateFlow(AdherenceStats.empty())
    val adherenceStats: StateFlow<AdherenceStats> = _adherenceStats.asStateFlow()

    init {
        loadAdherenceStats()
    }

    private fun loadAdherenceStats() {
        viewModelScope.launch {
            userPreferences.filterNotNull().collect { prefs ->
                val stats = logRepository.calculateAdherenceStats(prefs.userId, days = 7)
                _adherenceStats.value = stats
            }
        }
    }

    fun markLogAsTaken(logId: Long) {
        viewModelScope.launch {
            logRepository.markAsTaken(logId)
            loadAdherenceStats()
        }
    }

    fun markLogAsMissed(logId: Long) {
        viewModelScope.launch {
            logRepository.markAsMissed(logId)
            loadAdherenceStats()
        }
    }

    fun markMessageAsRead(messageId: Long) {
        viewModelScope.launch {
            messageRepository.markAsRead(messageId)
        }
    }
}
