package dk.sdu.dosura.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesManager: UserPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectRole(role: UserRole) {
        viewModelScope.launch {
            val userId = UUID.randomUUID().toString()
            preferencesManager.updateUserId(userId)
            preferencesManager.updateUserRole(role)
            preferencesManager.completeOnboarding()
            
            _uiState.value = _uiState.value.copy(
                selectedRole = role,
                isOnboardingCompleted = true
            )
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            preferencesManager.updateUserName(name)
            _uiState.value = _uiState.value.copy(userName = name)
        }
    }
}

data class OnboardingUiState(
    val selectedRole: UserRole? = null,
    val userName: String = "",
    val isOnboardingCompleted: Boolean = false
)
