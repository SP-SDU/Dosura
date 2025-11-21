package dk.sdu.dosura.presentation.patient.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import dk.sdu.dosura.data.local.entity.MedicationLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    medicationId: Long,
    viewModel: MedicationDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.medication?.name ?: "Medication Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "Error",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState.medication?.let { medication ->
                    if (!medication.photoUri.isNullOrBlank()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = medication.photoUri,
                                    contentDescription = "Medication photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Medication Info Card
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = medication.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                InfoRow("Dosage", medication.dosage)
                                
                                if (medication.instructions.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    InfoRow("Instructions", medication.instructions)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow(
                                    "Reminder Times",
                                    medication.reminderTimes.joinToString(", ")
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                InfoRow(
                                    "Days",
                                    if (medication.daysOfWeek.size == 7) "Daily" 
                                    else medication.daysOfWeek.joinToString(", ") {
                                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it - 1]
                                    }
                                )
                            }
                        }
                    }

                    // Adherence Log
                    if (uiState.logs.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent History",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(uiState.logs.take(10)) { log ->
                            LogCard(log)
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Medication?") },
            text = {
                Text("Are you sure you want to delete ${uiState.medication?.name}? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteMedication(onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun LogCard(log: MedicationLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.status) {
                dk.sdu.dosura.data.local.entity.LogStatus.TAKEN -> MaterialTheme.colorScheme.primaryContainer
                dk.sdu.dosura.data.local.entity.LogStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
                dk.sdu.dosura.data.local.entity.LogStatus.SKIPPED -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(log.scheduledTime)),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        .format(Date(log.scheduledTime)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = when (log.status) {
                    dk.sdu.dosura.data.local.entity.LogStatus.TAKEN -> "✅ Taken"
                    dk.sdu.dosura.data.local.entity.LogStatus.MISSED -> "❌ Missed"
                    dk.sdu.dosura.data.local.entity.LogStatus.SKIPPED -> "⏭️ Skipped"
                    else -> "⏳ Pending"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

