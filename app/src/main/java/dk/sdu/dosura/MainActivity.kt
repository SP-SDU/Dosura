package dk.sdu.dosura

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dk.sdu.dosura.data.preferences.UserPreferencesManager
import dk.sdu.dosura.notification.NotificationHelper
import dk.sdu.dosura.navigation.DosuraNavGraph
import dk.sdu.dosura.navigation.Screen
import dk.sdu.dosura.ui.theme.DosuraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferencesManager: UserPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val medicationIdFromNotification = intent?.getLongExtra(NotificationHelper.EXTRA_MEDICATION_ID, -1L)
        val medicationNameFromNotification = intent?.getStringExtra(NotificationHelper.EXTRA_MEDICATION_NAME)
        val scheduledTimeFromNotification = intent?.getLongExtra(NotificationHelper.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
        val showDialog = intent?.action == "SHOW_MEDICATION_DIALOG" && medicationIdFromNotification != -1L
        
        setContent {
            DosuraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    val startDestination = remember {
                        runBlocking {
                            val prefs = preferencesManager.userPreferences.first()
                            if (prefs.isOnboardingCompleted) {
                                when (prefs.userRole) {
                                    dk.sdu.dosura.domain.model.UserRole.PATIENT -> Screen.PatientHome.route
                                    dk.sdu.dosura.domain.model.UserRole.CAREGIVER -> Screen.CaregiverHome.route
                                }
                            } else {
                                Screen.Welcome.route
                            }
                        }
                    }
                    
                    DosuraNavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        showMedicationDialog = showDialog,
                        medicationIdFromNotification = medicationIdFromNotification,
                        medicationNameFromNotification = medicationNameFromNotification,
                        scheduledTimeFromNotification = scheduledTimeFromNotification
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
