package dk.sdu.dosura.presentation.caregiver.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dk.sdu.dosura.data.local.entity.CaregiverLink
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.domain.model.AdherenceStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaregiverHomeViewModel @Inject constructor(
    private val caregiverLinkRepository: CaregiverLinkRepository,
    private val logRepository: MedicationLogRepository,
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val userPreferences = preferencesManager.userPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val linkedPatients: StateFlow<List<CaregiverLink>> = userPreferences
        .filterNotNull()
        .flatMapLatest { prefs ->
            caregiverLinkRepository.getLinksForCaregiver(prefs.userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _patientStats = MutableStateFlow<Map<String, AdherenceStats>>(emptyMap())
    val patientStats: StateFlow<Map<String, AdherenceStats>> = _patientStats.asStateFlow()

    init {
        loadPatientStats()
    }

    private fun loadPatientStats() {
        viewModelScope.launch {
            linkedPatients.collect { links ->
                val statsMap = mutableMapOf<String, AdherenceStats>()
                links.forEach { link ->
                    val stats = logRepository.calculateAdherenceStats(link.patientId, days = 7)
                    statsMap[link.patientId] = stats
                }
                _patientStats.value = statsMap
            }
        }
    }
}
