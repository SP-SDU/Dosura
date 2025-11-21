package dk.sdu.dosura.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dk.sdu.dosura.domain.model.UserRole
import dk.sdu.dosura.presentation.caregiver.home.CaregiverHomeScreen
import dk.sdu.dosura.presentation.caregiver.link.LinkPatientScreen
import dk.sdu.dosura.presentation.caregiver.message.SendMessageScreen
import dk.sdu.dosura.presentation.caregiver.patient.PatientDetailScreen
import dk.sdu.dosura.presentation.caregiver.settings.CaregiverSettingsScreen
import dk.sdu.dosura.presentation.onboarding.PermissionScreen
import dk.sdu.dosura.presentation.onboarding.RoleSelectionScreen
import dk.sdu.dosura.presentation.onboarding.WelcomeScreen
import dk.sdu.dosura.presentation.patient.add.AddMedicationScreen
import dk.sdu.dosura.presentation.patient.detail.MedicationDetailScreen
import dk.sdu.dosura.presentation.patient.dialog.MedicationDialogViewModel
import dk.sdu.dosura.presentation.patient.dialog.TakeMedicationDialog
import dk.sdu.dosura.presentation.patient.home.PatientHomeScreen
import dk.sdu.dosura.presentation.patient.link.LinkCaregiverScreen
import dk.sdu.dosura.presentation.patient.settings.PatientSettingsScreen
import dk.sdu.dosura.presentation.onboarding.OnboardingViewModel

@Composable
fun DosuraNavGraph(
    navController: NavHostController,
    startDestination: String,
    showMedicationDialog: Boolean = false,
    medicationIdFromNotification: Long? = null,
    medicationNameFromNotification: String? = null,
    scheduledTimeFromNotification: Long? = null
) {
    var showDialog by remember { mutableStateOf(showMedicationDialog) }
    val dialogViewModel: MedicationDialogViewModel = hiltViewModel()
    
    if (showDialog && medicationIdFromNotification != null && medicationNameFromNotification != null) {
        TakeMedicationDialog(
            medicationName = medicationNameFromNotification,
            scheduledTime = scheduledTimeFromNotification ?: System.currentTimeMillis(),
            onTake = {
                dialogViewModel.takeMedication(
                    medicationIdFromNotification,
                    scheduledTimeFromNotification ?: System.currentTimeMillis()
                )
            },
            onSnooze = {
                dialogViewModel.snoozeMedication(medicationIdFromNotification, medicationNameFromNotification)
            },
            onDismiss = { showDialog = false }
        )
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Onboarding
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToRoleSelection = {
                    navController.navigate(Screen.Permissions.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Permissions.route) {
            PermissionScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RoleSelection.route) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    viewModel.selectRole(role)
                    val destination = if (role == UserRole.PATIENT) {
                        Screen.PatientHome.route
                    } else {
                        Screen.CaregiverHome.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }

        // Patient Screens
        composable(Screen.PatientHome.route) {
            PatientHomeScreen(
                onNavigateToAddMedication = {
                    navController.navigate(Screen.AddMedication.route)
                },
                onNavigateToMedicationDetail = { medicationId ->
                    navController.navigate(Screen.MedicationDetail.createRoute(medicationId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.PatientSettings.route)
                },
                onNavigateToLinkCaregiver = {
                    navController.navigate(Screen.LinkCaregiver.route)
                }
            )
        }

        composable(Screen.AddMedication.route) {
            AddMedicationScreen(
                onNavigateBack = { navController.popBackStack() },
                onMedicationAdded = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MedicationDetail.route,
            arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: 0L
            MedicationDetailScreen(
                medicationId = medicationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PatientSettings.route) {
            PatientSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLinkCaregiver = {
                    navController.navigate(Screen.LinkCaregiver.route)
                },
                onLogout = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.LinkCaregiver.route) {
            LinkCaregiverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Caregiver Screens
        composable(Screen.CaregiverHome.route) {
            CaregiverHomeScreen(
                onNavigateToLinkPatient = {
                    navController.navigate(Screen.LinkPatient.route)
                },
                onNavigateToPatientDetail = { patientId ->
                    navController.navigate(Screen.PatientDetail.createRoute(patientId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.CaregiverSettings.route)
                }
            )
        }

        composable(Screen.LinkPatient.route) {
            LinkPatientScreen(
                onNavigateBack = { navController.popBackStack() },
                onPatientLinked = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PatientDetail.route,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            PatientDetailScreen(
                patientId = patientId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSendMessage = {
                    navController.navigate(Screen.SendMessage.createRoute(patientId))
                }
            )
        }

        composable(
            route = Screen.SendMessage.route,
            arguments = listOf(navArgument("patientId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patientId = backStackEntry.arguments?.getString("patientId") ?: ""
            SendMessageScreen(
                patientId = patientId,
                onNavigateBack = { navController.popBackStack() },
                onMessageSent = { navController.popBackStack() }
            )
        }

        composable(Screen.CaregiverSettings.route) {
            CaregiverSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
