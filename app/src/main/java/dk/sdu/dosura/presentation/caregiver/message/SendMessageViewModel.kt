package dk.sdu.dosura.presentation.caregiver.message

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dk.sdu.dosura.data.local.entity.MotivationalMessage
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendMessageUiState(
    val message: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val isSent: Boolean = false
)

@HiltViewModel
class SendMessageViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val userPreferencesManager: UserPreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val patientId: String = savedStateHandle.get<String>("patientId") ?: ""

    private val _uiState = MutableStateFlow(SendMessageUiState())
    val uiState: StateFlow<SendMessageUiState> = _uiState.asStateFlow()

    fun updateMessage(newMessage: String) {
        _uiState.value = _uiState.value.copy(message = newMessage, error = null)
    }

    fun sendMessage(onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (_uiState.value.message.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "Please enter a message")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            try {
                val caregiverId = userPreferencesManager.userPreferences.first().userId

                messageRepository.sendMessage(
                    senderId = caregiverId,
                    receiverId = patientId,
                    message = _uiState.value.message
                )

                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isSent = true
                )
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    error = "Failed to send: ${e.message}"
                )
            }
        }
    }
}
