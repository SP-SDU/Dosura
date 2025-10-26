package dk.sdu.dosura.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caregiver_links")
data class CaregiverLink(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val patientId: String,
    val caregiverId: String,
    val patientName: String,
    val caregiverName: String,
    val linkCode: String, // Unique 6-digit code or QR code data
    val isActive: Boolean = true,
    val canViewAdherence: Boolean = true,
    val canSendMessages: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val approvedAt: Long? = null
)
