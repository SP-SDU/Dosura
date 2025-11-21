package dk.sdu.dosura

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dk.sdu.dosura.data.local.DosuraDatabase
import dk.sdu.dosura.data.local.entity.LogStatus
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MedicationManagementTest {

    private lateinit var database: DosuraDatabase
    private lateinit var medicationRepository: MedicationRepository
    private lateinit var logRepository: MedicationLogRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            DosuraDatabase::class.java
        ).allowMainThreadQueries().build()

        medicationRepository = MedicationRepository(database.medicationDao())
        logRepository = MedicationLogRepository(database.medicationLogDao())
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testMedicationWithNullPhotoHandledGracefully() = runBlocking {
        val medication = Medication(
            name = "Aspirin",
            dosage = "100mg",
            instructions = "Take with water",
            photoUri = null,
            userId = "user_001",
            reminderTimes = listOf("08:00", "20:00"),
            daysOfWeek = listOf(1, 2, 3, 4, 5),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)
        assertTrue(medicationId > 0)

        val retrieved = medicationRepository.getMedicationById(medicationId).first()
        assertNotNull(retrieved)
        assertNull(retrieved?.photoUri)
    }

    @Test
    fun testMedicationWithInvalidPhotoUriHandledGracefully() = runBlocking {
        val medication = Medication(
            name = "Vitamin D",
            dosage = "1000 IU",
            instructions = "Take once daily",
            photoUri = "invalid://uri/that/does/not/exist",
            userId = "user_001",
            reminderTimes = listOf("09:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)
        assertTrue(medicationId > 0)

        val retrieved = medicationRepository.getMedicationById(medicationId).first()
        assertNotNull(retrieved)
        assertEquals("invalid://uri/that/does/not/exist", retrieved?.photoUri)
    }

    @Test
    fun testMedicationLogCreation() = runBlocking {
        val medication = Medication(
            name = "Metformin",
            dosage = "500mg",
            userId = "user_001",
            reminderTimes = listOf("08:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)
        
        val log = MedicationLog(
            medicationId = medicationId,
            scheduledTime = System.currentTimeMillis(),
            takenTime = System.currentTimeMillis(),
            status = LogStatus.TAKEN
        )

        val logId = logRepository.insertLog(log)
        assertTrue(logId > 0)

        val logs = logRepository.getLogsForMedication(medicationId).first()
        assertEquals(1, logs.size)
        assertEquals(LogStatus.TAKEN, logs.first().status)
    }

    @Test
    fun testMarkMedicationAsTaken() = runBlocking {
        val medication = Medication(
            name = "Lisinopril",
            dosage = "10mg",
            userId = "user_001",
            reminderTimes = listOf("20:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)
        
        val log = MedicationLog(
            medicationId = medicationId,
            scheduledTime = System.currentTimeMillis(),
            status = LogStatus.PENDING
        )

        val logId = logRepository.insertLog(log)
        logRepository.markAsTaken(logId)

        val logs = logRepository.getLogsForMedication(medicationId).first()
        assertEquals(LogStatus.TAKEN, logs.first().status)
        assertNotNull(logs.first().takenTime)
    }

    @Test
    fun testAdherenceCalculation() = runBlocking {
        val userId = "user_001"
        val medication = Medication(
            name = "Test Med",
            dosage = "100mg",
            userId = userId,
            reminderTimes = listOf("08:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)

        val calendar = Calendar.getInstance()
        for (i in 0 until 7) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val status = if (i < 5) LogStatus.TAKEN else LogStatus.MISSED
            
            logRepository.insertLog(
                MedicationLog(
                    medicationId = medicationId,
                    scheduledTime = calendar.timeInMillis,
                    takenTime = if (status == LogStatus.TAKEN) calendar.timeInMillis else null,
                    status = status
                )
            )
        }

        val logs = logRepository.getLogsForMedication(medicationId).first()
        assertEquals(7, logs.size)
        
        val taken = logs.count { it.status == LogStatus.TAKEN }
        val missed = logs.count { it.status == LogStatus.MISSED }
        
        assertEquals(5, taken)
        assertEquals(2, missed)
        
        val adherencePercentage = (taken.toFloat() / logs.size.toFloat()) * 100f
        assertTrue(adherencePercentage > 70f)
        assertTrue(adherencePercentage < 72f)
    }

    @Test
    fun testMultipleRemindersPerDay() = runBlocking {
        val medication = Medication(
            name = "Multi-dose Med",
            dosage = "50mg",
            userId = "user_001",
            reminderTimes = listOf("08:00", "12:00", "18:00", "22:00"),
            daysOfWeek = emptyList(),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)
        val retrieved = medicationRepository.getMedicationById(medicationId).first()
        
        assertNotNull(retrieved)
        assertEquals(4, retrieved?.reminderTimes?.size)
        assertTrue(retrieved?.reminderTimes?.contains("08:00") == true)
        assertTrue(retrieved?.reminderTimes?.contains("22:00") == true)
    }

    @Test
    fun testSpecificDaysOfWeek() = runBlocking {
        val medication = Medication(
            name = "Weekend Med",
            dosage = "25mg",
            userId = "user_001",
            reminderTimes = listOf("10:00"),
            daysOfWeek = listOf(6, 7),
            startDate = System.currentTimeMillis()
        )

        val medicationId = medicationRepository.insertMedication(medication)
        val retrieved = medicationRepository.getMedicationById(medicationId).first()
        
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.daysOfWeek?.size)
        assertTrue(retrieved?.daysOfWeek?.contains(6) == true)
        assertTrue(retrieved?.daysOfWeek?.contains(7) == true)
    }
}
