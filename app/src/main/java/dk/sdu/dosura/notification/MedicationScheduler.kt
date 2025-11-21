package dk.sdu.dosura.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dk.sdu.dosura.data.local.entity.Medication
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: NotificationHelper
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleMedicationReminders(medication: Medication) {
        cancelMedicationReminders(medication.id)

        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        
        medication.reminderTimes.forEach { timeString ->
            val time = parseTime(timeString)
            
            val daysToSchedule = if (medication.daysOfWeek.isEmpty()) {
                listOf(1, 2, 3, 4, 5, 6, 7)
            } else {
                medication.daysOfWeek
            }

            daysToSchedule.forEach { dayOfWeek ->
                val scheduleCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, time.first)
                    set(Calendar.MINUTE, time.second)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    val targetDay = convertToCalendarDay(dayOfWeek)
                    val currentDay = get(Calendar.DAY_OF_WEEK)
                    
                    var daysToAdd = targetDay - currentDay
                    if (daysToAdd < 0) daysToAdd += 7
                    if (daysToAdd == 0 && timeInMillis <= System.currentTimeMillis()) {
                        daysToAdd = 7
                    }
                    
                    add(Calendar.DAY_OF_YEAR, daysToAdd)
                }

                scheduleAlarm(
                    medication.id,
                    medication.name,
                    scheduleCalendar.timeInMillis,
                    timeString,
                    dayOfWeek
                )
            }
        }
    }

    private fun scheduleAlarm(
        medicationId: Long,
        medicationName: String,
        triggerTime: Long,
        timeString: String,
        dayOfWeek: Int
    ) {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_MEDICATION_ID, medicationId)
            putExtra(NotificationHelper.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(NotificationHelper.EXTRA_SCHEDULED_TIME, triggerTime)
        }

        val requestCode = generateRequestCode(medicationId, timeString, dayOfWeek)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            AlarmManager.INTERVAL_DAY * 7,
            pendingIntent
        )
    }

    fun cancelMedicationReminders(medicationId: Long) {
        val timeStrings = listOf("00:00", "06:00", "12:00", "18:00")
        val days = listOf(1, 2, 3, 4, 5, 6, 7)
        
        timeStrings.forEach { timeString ->
            days.forEach { day ->
                val requestCode = generateRequestCode(medicationId, timeString, day)
                val intent = Intent(context, MedicationAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                
                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }
        }
        
        notificationHelper.cancelNotification(medicationId)
    }

    private fun parseTime(timeString: String): Pair<Int, Int> {
        val parts = timeString.split(":")
        return Pair(parts[0].toInt(), parts[1].toInt())
    }

    private fun convertToCalendarDay(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
    }

    private fun generateRequestCode(medicationId: Long, timeString: String, dayOfWeek: Int): Int {
        return (medicationId.toString() + timeString.replace(":", "") + dayOfWeek.toString()).hashCode()
    }

    fun scheduleSnoozeReminder(medication: Medication, medicationName: String, snoozeMinutes: Int) {
        val snoozeTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)
        
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_MEDICATION_ID, medication.id)
            putExtra(NotificationHelper.EXTRA_MEDICATION_NAME, medicationName)
            putExtra(NotificationHelper.EXTRA_SCHEDULED_TIME, snoozeTime)
        }

        val requestCode = ("snooze_" + medication.id).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            snoozeTime,
            pendingIntent
        )
    }
}
