package dk.sdu.dosura.data.repository

import dk.sdu.dosura.data.local.dao.MedicationDao
import dk.sdu.dosura.data.local.entity.Medication
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao
) {
    fun getAllMedications(userId: String): Flow<List<Medication>> {
        return medicationDao.getAllMedications(userId)
    }

    fun getMedicationById(id: Long): Flow<Medication?> {
        return medicationDao.getMedicationById(id)
    }

    suspend fun getActiveMedications(userId: String): List<Medication> {
        return medicationDao.getActiveMedicationsForUser(userId)
    }

    suspend fun insertMedication(medication: Medication): Long {
        return medicationDao.insertMedication(medication)
    }

    suspend fun updateMedication(medication: Medication) {
        medicationDao.updateMedication(medication.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMedication(medication: Medication) {
        medicationDao.deleteMedication(medication)
    }

    suspend fun deactivateMedication(id: Long) {
        medicationDao.deactivateMedication(id)
    }
}
