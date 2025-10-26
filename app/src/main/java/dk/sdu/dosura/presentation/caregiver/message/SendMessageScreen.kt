package dk.sdu.dosura.presentation.caregiver.message

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMessageScreen(
    patientId: String,
    viewModel: SendMessageViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onMessageSent: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Motivational Message") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Send an encouraging message to help your patient stay on track with their medications.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick message suggestions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Suggestions:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val suggestions = listOf(
                        "Great job staying on track! 🎉",
                        "You're doing amazing! Keep it up! 💪",
                        "Proud of your progress! ❤️",
                        "Don't forget your evening meds! 💊",
                        "Thinking of you! Stay healthy! 🌟"
                    )
                    
                    suggestions.forEach { suggestion ->
                        TextButton(
                            onClick = { viewModel.updateMessage(suggestion) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = suggestion,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.message,
                onValueChange = viewModel::updateMessage,
                label = { Text("Your Message") },
                placeholder = { Text("Type your message here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 5,
                maxLines = 10
            )

            if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { viewModel.sendMessage(onMessageSent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSending,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Send Message 💌",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

