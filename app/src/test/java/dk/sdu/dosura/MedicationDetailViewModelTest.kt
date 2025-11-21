package dk.sdu.dosura

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import dk.sdu.dosura.data.local.dao.MedicationDao
import dk.sdu.dosura.data.local.dao.MedicationLogDao
import dk.sdu.dosura.data.local.entity.Medication
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.data.repository.MedicationLogRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import dk.sdu.dosura.notification.MedicationScheduler
import dk.sdu.dosura.presentation.patient.detail.MedicationDetailViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MedicationDetailViewModelTest {
    private val fakeMedicationDao = object : MedicationDao {
        override fun getAllMedications(userId: String): Flow<List<Medication>> = flowOf(emptyList())
        override fun getMedicationById(id: Long): Flow<Medication?> = flowOf(null)
        override suspend fun getActiveMedicationsForUser(userId: String): List<Medication> = emptyList()
        override suspend fun insertMedication(medication: Medication): Long = 1L
        override suspend fun updateMedication(medication: Medication) {}
        override suspend fun deleteMedication(medication: Medication) {}
        override suspend fun deactivateMedication(id: Long) {}
    }

    private val fakeLogDao = object : MedicationLogDao {
        override fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>> = flowOf(emptyList())
        override fun getRecentLogs(userId: String, limit: Int): Flow<List<MedicationLog>> = flowOf(emptyList())
        override suspend fun getLogsInTimeRange(userId: String, startTime: Long, endTime: Long): List<MedicationLog> = emptyList()
        override suspend fun getLogsByStatusAndTimeRange(status: dk.sdu.dosura.data.local.entity.LogStatus, startTime: Long, endTime: Long): List<MedicationLog> = emptyList()
        override suspend fun getMissedLogsForUser(userId: String): List<MedicationLog> = emptyList()
        override suspend fun insertLog(log: MedicationLog): Long = 1L
        override suspend fun updateLog(log: MedicationLog) {}
        override suspend fun deleteLog(log: MedicationLog) {}
        override suspend fun updateLogStatus(logId: Long, status: dk.sdu.dosura.data.local.entity.LogStatus, takenTime: Long?) {}
    }

    private val medicationRepository = MedicationRepository(fakeMedicationDao)
    private val logRepository = MedicationLogRepository(fakeLogDao)
    private val medicationScheduler by lazy {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notificationHelper = dk.sdu.dosura.notification.NotificationHelper(context)
        dk.sdu.dosura.notification.MedicationScheduler(context, notificationHelper)
    }

    @Test
    fun testViewModelInitWithLongId() {
        runBlocking {
            val savedStateHandle = SavedStateHandle(mapOf("medicationId" to 42L))
            MedicationDetailViewModel(medicationRepository, logRepository, medicationScheduler, savedStateHandle)
            // If no exception thrown, test passes
        }
    }

    @Test
    fun testViewModelInitWithStringId() {
        runBlocking {
            val savedStateHandle = SavedStateHandle(mapOf("medicationId" to "42"))
            MedicationDetailViewModel(medicationRepository, logRepository, medicationScheduler, savedStateHandle)
            // If no exception thrown, test passes
        }
    }
}
