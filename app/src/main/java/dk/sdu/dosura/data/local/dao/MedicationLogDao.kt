package dk.sdu.dosura.data.local.dao

import androidx.room.*
import dk.sdu.dosura.data.local.entity.LogStatus
import dk.sdu.dosura.data.local.entity.MedicationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId ORDER BY scheduledTime DESC")
    fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE medicationId IN (SELECT id FROM medications WHERE userId = :userId) ORDER BY scheduledTime DESC LIMIT :limit")
    fun getRecentLogs(userId: String, limit: Int = 50): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE status = :status AND scheduledTime BETWEEN :startTime AND :endTime")
    suspend fun getLogsByStatusAndTimeRange(
        status: LogStatus,
        startTime: Long,
        endTime: Long
    ): List<MedicationLog>

    @Query("""
        SELECT * FROM medication_logs 
        WHERE medicationId IN (SELECT id FROM medications WHERE userId = :userId)
        AND scheduledTime >= :startTime 
        AND scheduledTime <= :endTime
        ORDER BY scheduledTime DESC
    """)
    suspend fun getLogsInTimeRange(userId: String, startTime: Long, endTime: Long): List<MedicationLog>

    @Query("SELECT * FROM medication_logs WHERE status = 'MISSED' AND medicationId IN (SELECT id FROM medications WHERE userId = :userId)")
    suspend fun getMissedLogsForUser(userId: String): List<MedicationLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MedicationLog): Long

    @Update
    suspend fun updateLog(log: MedicationLog)

    @Delete
    suspend fun deleteLog(log: MedicationLog)

    @Query("UPDATE medication_logs SET status = :status, takenTime = :takenTime WHERE id = :logId")
    suspend fun updateLogStatus(logId: Long, status: LogStatus, takenTime: Long?)
}
