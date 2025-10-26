package dk.sdu.dosura.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "motivational_messages")
data class MotivationalMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderId: String, // Caregiver ID
    val receiverId: String, // Patient ID
    val message: String,
    val isRead: Boolean = false,
    val sentAt: Long = System.currentTimeMillis()
)
