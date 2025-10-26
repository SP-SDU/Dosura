package dk.sdu.dosura.data.local.dao

import androidx.room.*
import dk.sdu.dosura.data.local.entity.MotivationalMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface MotivationalMessageDao {
    @Query("SELECT * FROM motivational_messages WHERE receiverId = :userId ORDER BY sentAt DESC")
    fun getMessagesForUser(userId: String): Flow<List<MotivationalMessage>>

    @Query("SELECT * FROM motivational_messages WHERE receiverId = :userId AND isRead = 0 ORDER BY sentAt DESC")
    fun getUnreadMessages(userId: String): Flow<List<MotivationalMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MotivationalMessage): Long

    @Update
    suspend fun updateMessage(message: MotivationalMessage)

    @Query("UPDATE motivational_messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: Long)
}
