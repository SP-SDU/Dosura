package dk.sdu.dosura.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dk.sdu.dosura.domain.model.UserRole

@Composable
fun RoleSelectionScreen(
    onRoleSelected: (UserRole) -> Unit
) {
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }
    var showConfirmation by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to Dosura",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Choose your role to get started",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Patient Card
            RoleCard(
                title = "Patient",
                description = "Manage your medications and track adherence",
                icon = Icons.Default.Person,
                isSelected = selectedRole == UserRole.PATIENT,
                onClick = { 
                    selectedRole = UserRole.PATIENT
                    showConfirmation = true
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Caregiver Card
            RoleCard(
                title = "Family / Caregiver",
                description = "Support your loved ones with their medication routine",
                icon = Icons.Default.Favorite,
                isSelected = selectedRole == UserRole.CAREGIVER,
                onClick = { 
                    selectedRole = UserRole.CAREGIVER
                    showConfirmation = true
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "💡 Tip: You can change this later in Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    // Confirmation Dialog - Extra safety for elderly users
    if (showConfirmation && selectedRole != null) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmation = false
                selectedRole = null
            },
            title = {
                Text(
                    "Confirm Your Choice",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "You selected:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (selectedRole == UserRole.PATIENT) "PATIENT" else "FAMILY / CAREGIVER",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Is this correct?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedRole?.let { onRoleSelected(it) }
                        showConfirmation = false
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        "Yes, Continue",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showConfirmation = false
                        selectedRole = null
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        "No, Go Back",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        )
    }
}

@Composable
fun RoleCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
