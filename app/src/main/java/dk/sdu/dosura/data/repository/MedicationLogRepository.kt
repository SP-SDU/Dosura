package dk.sdu.dosura.data.repository

import dk.sdu.dosura.data.local.dao.MedicationLogDao
import dk.sdu.dosura.data.local.entity.LogStatus
import dk.sdu.dosura.data.local.entity.MedicationLog
import dk.sdu.dosura.domain.model.AdherenceStats
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationLogRepository @Inject constructor(
    private val logDao: MedicationLogDao
) {
    fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>> {
        return logDao.getLogsForMedication(medicationId)
    }

    fun getRecentLogs(userId: String, limit: Int = 50): Flow<List<MedicationLog>> {
        return logDao.getRecentLogs(userId, limit)
    }

    suspend fun getLogsInTimeRange(userId: String, startTime: Long, endTime: Long): List<MedicationLog> {
        return logDao.getLogsInTimeRange(userId, startTime, endTime)
    }

    suspend fun getMissedLogs(userId: String): List<MedicationLog> {
        return logDao.getMissedLogsForUser(userId)
    }

    suspend fun insertLog(log: MedicationLog): Long {
        return logDao.insertLog(log)
    }

    suspend fun updateLog(log: MedicationLog) {
        logDao.updateLog(log)
    }

    suspend fun markAsTaken(logId: Long) {
        logDao.updateLogStatus(logId, LogStatus.TAKEN, System.currentTimeMillis())
    }

    suspend fun markAsMissed(logId: Long) {
        logDao.updateLogStatus(logId, LogStatus.MISSED, null)
    }

    suspend fun markAsSkipped(logId: Long) {
        logDao.updateLogStatus(logId, LogStatus.SKIPPED, null)
    }

    suspend fun calculateAdherenceStats(userId: String, days: Int = 7): AdherenceStats {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startTime = calendar.timeInMillis

        val logs = getLogsInTimeRange(userId, startTime, endTime)
        
        val taken = logs.count { it.status == LogStatus.TAKEN }
        val missed = logs.count { it.status == LogStatus.MISSED }
        val skipped = logs.count { it.status == LogStatus.SKIPPED }
        val total = logs.size

        val adherencePercentage = if (total > 0) {
            (taken.toFloat() / total.toFloat()) * 100f
        } else {
            0f
        }

        return AdherenceStats(
            totalScheduled = total,
            totalTaken = taken,
            totalMissed = missed,
            totalSkipped = skipped,
            adherencePercentage = adherencePercentage,
            weeklyStreak = calculateWeeklyStreak(logs),
            currentStreak = calculateCurrentStreak(logs)
        )
    }

    private fun calculateWeeklyStreak(logs: List<MedicationLog>): Int {
        // Count consecutive weeks with >80% adherence
        val calendar = Calendar.getInstance()
        val weeklyData = mutableMapOf<Int, Pair<Int, Int>>() // week -> (taken, total)
        
        logs.forEach { log ->
            calendar.timeInMillis = log.scheduledTime
            val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)
            val weekKey = year * 100 + weekOfYear
            
            val current = weeklyData.getOrDefault(weekKey, Pair(0, 0))
            val taken = if (log.status == dk.sdu.dosura.data.local.entity.LogStatus.TAKEN) 1 else 0
            weeklyData[weekKey] = Pair(current.first + taken, current.second + 1)
        }
        
        var streak = 0
        val sortedWeeks = weeklyData.keys.sorted().reversed()
        
        for (week in sortedWeeks) {
            val (taken, total) = weeklyData[week]!!
            val adherence = if (total > 0) (taken.toFloat() / total) * 100 else 0f
            if (adherence >= 80f) {
                streak++
            } else {
                break
            }
        }
        
        return streak
    }

    private fun calculateCurrentStreak(logs: List<MedicationLog>): Int {
        // Count consecutive days without missed doses
        if (logs.isEmpty()) return 0
        
        val sortedLogs = logs.sortedByDescending { it.scheduledTime }
        val calendar = Calendar.getInstance()
        var currentStreak = 0
        var lastDate: Long? = null
        
        for (log in sortedLogs) {
            if (log.status == dk.sdu.dosura.data.local.entity.LogStatus.PENDING) continue
            
            calendar.timeInMillis = log.scheduledTime
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)
            val dateKey = year * 1000L + dayOfYear
            
            if (lastDate != null && dateKey != lastDate && dateKey != lastDate - 1) {
                break // Streak broken
            }
            
            if (log.status == dk.sdu.dosura.data.local.entity.LogStatus.TAKEN) {
                if (lastDate == null || dateKey != lastDate) {
                    currentStreak++
                }
            } else if (log.status == dk.sdu.dosura.data.local.entity.LogStatus.MISSED) {
                break // Missed dose breaks streak
            }
            
            lastDate = dateKey
        }
        
        return currentStreak
    }
}
