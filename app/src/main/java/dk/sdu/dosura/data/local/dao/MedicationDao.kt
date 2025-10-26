package dk.sdu.dosura.data.local.dao

import androidx.room.*
import dk.sdu.dosura.data.local.entity.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE userId = :userId AND isActive = 1 ORDER BY createdAt DESC")
    fun getAllMedications(userId: String): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationById(id: Long): Flow<Medication?>

    @Query("SELECT * FROM medications WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveMedicationsForUser(userId: String): List<Medication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("UPDATE medications SET isActive = 0 WHERE id = :id")
    suspend fun deactivateMedication(id: Long)
}
