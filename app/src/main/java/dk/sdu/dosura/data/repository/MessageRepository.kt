package dk.sdu.dosura.data.repository

import dk.sdu.dosura.data.local.dao.MotivationalMessageDao
import dk.sdu.dosura.data.local.entity.MotivationalMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MotivationalMessageDao
) {
    fun getMessagesForUser(userId: String): Flow<List<MotivationalMessage>> {
        return messageDao.getMessagesForUser(userId)
    }

    fun getUnreadMessages(userId: String): Flow<List<MotivationalMessage>> {
        return messageDao.getUnreadMessages(userId)
    }

    suspend fun sendMessage(senderId: String, receiverId: String, message: String): Long {
        val msg = MotivationalMessage(
            senderId = senderId,
            receiverId = receiverId,
            message = message
        )
        return messageDao.insertMessage(msg)
    }

    suspend fun markAsRead(messageId: Long) {
        messageDao.markAsRead(messageId)
    }
}
