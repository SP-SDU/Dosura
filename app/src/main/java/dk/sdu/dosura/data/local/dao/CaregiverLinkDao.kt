package dk.sdu.dosura.data.local.dao

import androidx.room.*
import dk.sdu.dosura.data.local.entity.CaregiverLink
import kotlinx.coroutines.flow.Flow

@Dao
interface CaregiverLinkDao {
    @Query("SELECT * FROM caregiver_links WHERE patientId = :patientId AND isActive = 1")
    fun getLinksForPatient(patientId: String): Flow<List<CaregiverLink>>

    @Query("SELECT * FROM caregiver_links WHERE caregiverId = :caregiverId AND isActive = 1")
    fun getLinksForCaregiver(caregiverId: String): Flow<List<CaregiverLink>>

    @Query("SELECT * FROM caregiver_links WHERE linkCode = :code AND isActive = 1")
    suspend fun getLinkByCode(code: String): CaregiverLink?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: CaregiverLink): Long

    @Update
    suspend fun updateLink(link: CaregiverLink)

    @Delete
    suspend fun deleteLink(link: CaregiverLink)

    @Query("UPDATE caregiver_links SET isActive = 0 WHERE id = :id")
    suspend fun deactivateLink(id: Long)
}
