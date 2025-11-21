package dk.sdu.dosura.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.stringPreferencesKey
import dk.sdu.dosura.data.preferences.dataStore
import androidx.room.Room
import dk.sdu.dosura.data.local.DosuraDatabase
import dk.sdu.dosura.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch



class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    val database = Room.databaseBuilder(
                        context.applicationContext,
                        DosuraDatabase::class.java,
                        "dosura_database"
                    ).build()
                    
                    val medicationRepository = MedicationRepository(database.medicationDao())
                    val notificationHelper = NotificationHelper(context)
                    val medicationScheduler = MedicationScheduler(context, notificationHelper)
                    
                    val userIdKey = stringPreferencesKey("user_id")
                    val userId = context.dataStore.data
                        .map { preferences -> preferences[userIdKey] ?: "" }
                        .first()
                    
                    if (userId.isNotEmpty()) {
                        val medications = medicationRepository.getActiveMedications(userId)
                        medications.forEach { medication ->
                            medicationScheduler.scheduleMedicationReminders(medication)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
    }
}
