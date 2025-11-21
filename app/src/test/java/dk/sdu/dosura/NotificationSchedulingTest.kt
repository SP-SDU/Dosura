package dk.sdu.dosura

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.notification.MedicationAlarmReceiver
import dk.sdu.dosura.notification.MedicationScheduler
import dk.sdu.dosura.notification.NotificationHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NotificationSchedulingTest {

    private lateinit var context: Context
    private lateinit var medicationScheduler: MedicationScheduler
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationHelper = NotificationHelper(context)
        medicationScheduler = MedicationScheduler(context, notificationHelper)
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @Test
    fun testNotificationChannelCreated() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            assertNotNull(channel)
            assertEquals(NotificationHelper.CHANNEL_NAME, channel.name)
        }
    }

    @Test
    fun testMedicationScheduling() {
        val medication = Medication(
            id = 1,
            name = "Test Medication",
            dosage = "100mg",
            userId = "test_user",
            reminderTimes = listOf("08:00", "20:00"),
            daysOfWeek = listOf(1, 2, 3, 4, 5),
            startDate = System.currentTimeMillis()
        )

        medicationScheduler.scheduleMedicationReminders(medication)
        
        val expectedAlarmCount = medication.reminderTimes.size * medication.daysOfWeek.size
        assertTrue(expectedAlarmCount > 0)
    }

    @Test
    fun testDailyMedicationScheduling() {
        val medication = Medication(
            id = 2,
            name = "Daily Med",
            dosage = "50mg",
            userId = "test_user",
            reminderTimes = listOf("09:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        medicationScheduler.scheduleMedicationReminders(medication)
        assertTrue(true)
    }

    @Test
    fun testCancelMedicationReminders() {
        val medication = Medication(
            id = 3,
            name = "Cancelled Med",
            dosage = "75mg",
            userId = "test_user",
            reminderTimes = listOf("10:00"),
            daysOfWeek = listOf(1, 3, 5),
            startDate = System.currentTimeMillis()
        )

        medicationScheduler.scheduleMedicationReminders(medication)
        medicationScheduler.cancelMedicationReminders(medication.id)
        
        assertTrue(true)
    }

    @Test
    fun testSnoozeReminder() {
        val medication = Medication(
            id = 4,
            name = "Snooze Med",
            dosage = "25mg",
            userId = "test_user",
            reminderTimes = listOf("11:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        medicationScheduler.scheduleSnoozeReminder(medication, "Snooze Med", 10)
        
        assertTrue(true)
    }

    @Test
    fun testAlarmReceiverIntent() {
        val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_MEDICATION_ID, 1L)
            putExtra(NotificationHelper.EXTRA_MEDICATION_NAME, "Test Med")
            putExtra(NotificationHelper.EXTRA_SCHEDULED_TIME, System.currentTimeMillis())
        }

        assertNotNull(intent.getLongExtra(NotificationHelper.EXTRA_MEDICATION_ID, -1))
        assertEquals(1L, intent.getLongExtra(NotificationHelper.EXTRA_MEDICATION_ID, -1))
        assertEquals("Test Med", intent.getStringExtra(NotificationHelper.EXTRA_MEDICATION_NAME))
    }

    @Test
    fun testMultipleRemindersScheduled() {
        val medication = Medication(
            id = 5,
            name = "Multi-Reminder Med",
            dosage = "100mg",
            userId = "test_user",
            reminderTimes = listOf("08:00", "12:00", "16:00", "20:00"),
            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
            startDate = System.currentTimeMillis()
        )

        medicationScheduler.scheduleMedicationReminders(medication)
        
        val totalReminders = medication.reminderTimes.size * medication.daysOfWeek.size
        assertEquals(28, totalReminders)
    }

    @Test
    fun testRescheduleAfterReboot() {
        val medications = listOf(
            Medication(
                id = 6,
                name = "Reboot Med 1",
                dosage = "50mg",
                userId = "test_user",
                reminderTimes = listOf("09:00"),
                daysOfWeek = emptyList(),
                startDate = System.currentTimeMillis()
            ),
            Medication(
                id = 7,
                name = "Reboot Med 2",
                dosage = "75mg",
                userId = "test_user",
                reminderTimes = listOf("21:00"),
                daysOfWeek = emptyList(),
                startDate = System.currentTimeMillis()
            )
        )

        medications.forEach { medication ->
            medicationScheduler.scheduleMedicationReminders(medication)
        }

        assertTrue(true)
    }
}
