package dk.sdu.dosura

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DosuraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val medicationChannel = NotificationChannel(
                MEDICATION_CHANNEL_ID,
                "Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for medication reminders"
                enableVibration(true)
            }

            val missedDoseChannel = NotificationChannel(
                MISSED_DOSE_CHANNEL_ID,
                "Missed Dose Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for caregivers when patient misses a dose"
            }

            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Motivational messages from caregivers"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(medicationChannel)
            notificationManager.createNotificationChannel(missedDoseChannel)
            notificationManager.createNotificationChannel(messageChannel)
        }
    }

    companion object {
        const val MEDICATION_CHANNEL_ID = "medication_reminders"
        const val MISSED_DOSE_CHANNEL_ID = "missed_dose_alerts"
        const val MESSAGE_CHANNEL_ID = "motivational_messages"
    }
}
