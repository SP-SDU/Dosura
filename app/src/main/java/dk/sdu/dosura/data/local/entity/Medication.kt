package dk.sdu.dosura.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val instructions: String = "",
    val photoUri: String? = null,
    val userId: String, // Patient's user ID
    val reminderTimes: List<String>, // Stored as comma-separated times (HH:mm format)
    val daysOfWeek: List<Int>, // 1=Monday, 7=Sunday (empty = daily)
    val startDate: Long, // Timestamp in millis
    val endDate: Long? = null, // Null = no end date
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
