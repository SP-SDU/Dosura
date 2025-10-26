package dk.sdu.dosura.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId"), Index("scheduledTime")]
)
data class MedicationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduledTime: Long, // Timestamp in millis
    val takenTime: Long? = null, // Null if not taken yet
    val status: LogStatus,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class LogStatus {
    PENDING,    // Scheduled but not yet due
    TAKEN,      // Marked as taken
    MISSED,     // Missed (past due time)
    SKIPPED     // Intentionally skipped
}
