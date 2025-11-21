package dk.sdu.dosura.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MedicationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getLongExtra(NotificationHelper.EXTRA_MEDICATION_ID, -1)
        val medicationName = intent.getStringExtra(NotificationHelper.EXTRA_MEDICATION_NAME) ?: "Medication"
        val scheduledTime = intent.getLongExtra(NotificationHelper.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())

        if (medicationId != -1L) {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.showMedicationReminder(medicationId, medicationName, scheduledTime)
        }
    }
}
