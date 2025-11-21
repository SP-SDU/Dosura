package dk.sdu.dosura.p2p

import android.util.Log
import dk.sdu.dosura.data.local.entity.CaregiverLink
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import dk.sdu.dosura.data.repository.MessageRepository
import dk.sdu.dosura.notification.NotificationHelper
import dk.sdu.dosura.data.repository.MedicationLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2PLinkManager @Inject constructor(
    private val p2pService: P2PService,
    private val caregiverLinkRepository: CaregiverLinkRepository,
    private val medicationRepository: MedicationRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val messageRepository: MessageRepository,
    private val notificationHelper: NotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    fun startPatientMode(userId: String, linkCode: String) {
        // Start advertising this patient's service
        p2pService.startAdvertising(port = 9876, userId = userId)
        
        // Listen for caregiver link requests (support replying on same socket)
        scope.launch {
            while (true) {
                try {
                    val incoming = p2pService.receiveIncomingMessage()
                    val message = incoming.message
                    val reply = incoming.reply
                    if (message.type == P2PMessage.MessageType.LINK_REQUEST) {
                        Log.d("P2PLinkManager", "LINK_REQUEST received with data=${message.data} expectedCode=$linkCode from=${message.senderId}")
                    }
                    if (message.type == P2PMessage.MessageType.LINK_REQUEST && 
                        message.data == linkCode) {
                        
                        // Create caregiver link
                        val link = CaregiverLink(
                            patientId = userId,
                            caregiverId = message.senderId,
                            patientName = "Patient",
                            caregiverName = "Caregiver",
                            linkCode = linkCode,
                            isActive = true,
                            createdAt = System.currentTimeMillis()
                        )
                        caregiverLinkRepository.insertLink(link)
                        
                        // Send response with patient's medications using the same socket
                        val medications = medicationRepository.getAllMedications(userId).first()
                        val response = P2PMessage(
                            type = P2PMessage.MessageType.LINK_RESPONSE,
                            senderId = userId,
                            data = gson.toJson(medications),
                            messageId = message.messageId,
                            timestamp = System.currentTimeMillis()
                        )
                        try {
                            Log.d("P2PLinkManager", "Replying to LINK_REQUEST with ${medications.size} meds to ${message.senderId}")
                            reply(response)
                            Log.d("P2PLinkManager", "Replied on same socket with LINK_RESPONSE to ${message.senderId}")
                        } catch (e: Exception) {
                            Log.e("P2PLinkManager", "Failed to reply on same socket: ${e.message}")
                        }
                    }
                    // Accept incoming motivational messages for this patient
                    else if (message.type == P2PMessage.MessageType.MOTIVATIONAL_MESSAGE) {
                        Log.d("P2PLinkManager", "Patient received MOTIVATIONAL_MESSAGE from ${message.senderId}")
                        try {
                            // Store message locally for patient
                            messageRepository.sendMessage(message.senderId, userId, message.data)
                            // Show a notification for the patient
                            try {
                                notificationHelper.showMotivationalMessage(message.senderId, message.data)
                            } catch (e: Exception) {
                                Log.w("P2PLinkManager", "Failed to show motivational message notification: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("P2PLinkManager", "Failed to insert motivational message for patient: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    // Continue listening
                }
            }
        }
        
        // Start discovering caregivers
        p2pService.startDiscovery()

        // Publish medication changes to connected caregivers via MEDICATION_SYNC
        scope.launch {
            medicationRepository.getAllMedications(userId).collect { medications ->
                // Serialize medications as JSON
                val data = gson.toJson(medications)
                val message = P2PMessage(
                    type = P2PMessage.MessageType.MEDICATION_SYNC,
                    senderId = userId,
                    data = data
                )

                // Send to all discovered peers (caregivers)
                val peers = p2pService.discoveredPeers.value
                Log.d("P2PLinkManager", "Publishing MEDICATION_SYNC to ${peers.size} peers, medicationCount=${medications.size}")
                peers.forEach { peer ->
                    scope.launch {
                        try {
                            val ok = p2pService.sendMessageWithAck(peer, message)
                            Log.d("P2PLinkManager", "sendMessageWithAck result to ${peer.name} => $ok")
                        } catch (e: Exception) {
                            Log.e("P2PLinkManager", "Failed to send medication sync to ${peer.name}", e)
                        }
                    }
                }
            }
        }

        // Publish log updates (adherence) to connected caregivers via MEDICATION_LOG message
        scope.launch {
            medicationLogRepository.getRecentLogs(userId).collect { logs ->
                val data = gson.toJson(logs)
                val logMessage = P2PMessage(
                    type = P2PMessage.MessageType.MEDICATION_LOG,
                    senderId = userId,
                    data = data
                )
                val peers2 = p2pService.discoveredPeers.value
                Log.d("P2PLinkManager", "Publishing MEDICATION_LOG to ${peers2.size} peers, logCount=${logs.size}")
                peers2.forEach { peer ->
                    scope.launch {
                        try {
                            val ok = p2pService.sendMessageWithAck(peer, logMessage)
                            Log.d("P2PLinkManager", "sendMessageWithAck (log) result to ${peer.name} => $ok")
                        } catch (e: Exception) {
                            Log.e("P2PLinkManager", "Failed to send medication log to ${peer.name}", e)
                        }
                    }
                }
            }
        }
    }
    
    suspend fun linkToPatient(caregiverId: String, linkCode: String): Boolean {
        // Start advertising caregiver service
        p2pService.startAdvertising(port = 9877, userId = caregiverId)
        p2pService.startDiscovery()
        
        // Wait longer for discovery on physical devices
        kotlinx.coroutines.delay(5000)
        
        Log.d("P2P", "Discovery complete. Found ${p2pService.discoveredPeers.value.size} peers")
        
        // Find patient peer and send link request
        val patientPeers = p2pService.discoveredPeers.value

        for (peer in patientPeers) {
            val request = P2PMessage(
                type = P2PMessage.MessageType.LINK_REQUEST,
                senderId = caregiverId,
                data = linkCode
            )
            
            val response = p2pService.sendMessageWithResponse(peer, request)
            Log.d("P2PLinkManager", "Sent LINK_REQUEST to ${peer.name} (host=${peer.host}:${peer.port}). got response -> ${response?.type}")
            if (response == null) {
                Log.w("P2PLinkManager", "No LINK_RESPONSE received from ${peer.name}; continuing to next peer")
                continue
            }

            try {
                if (response.type == P2PMessage.MessageType.LINK_RESPONSE) {
                    Log.d("P2PLinkManager", "Caregiver received LINK_RESPONSE from ${response.senderId}")

                    // Create link
                    val link = CaregiverLink(
                        patientId = response.senderId,
                        caregiverId = caregiverId,
                        patientName = "Patient",
                        caregiverName = "Caregiver",
                        linkCode = linkCode,
                        isActive = true,
                        createdAt = System.currentTimeMillis()
                    )
                    caregiverLinkRepository.insertLink(link)

                    // Parse and upsert medications included in the link response
                    try {
                        val medsFromResponse = parseMedicationsFromData(response.data, response.senderId)
                        Log.d("P2PLinkManager", "Processing LINK_RESPONSE: ${medsFromResponse.size} medications received from ${response.senderId}")
                        medsFromResponse.forEach { m ->
                            try {
                                medicationRepository.insertMedication(m)
                            } catch (e: Exception) {
                                Log.e("P2PLinkManager", "Failed to insert medication from LINK_RESPONSE: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("P2PLinkManager", "Failed to parse LINK_RESPONSE medications: ${e.message}")
                    }

                    Log.d("P2PLinkManager", "Caregiver created link to ${response.senderId} (linkcode=${linkCode})")

                    // Start receiver loop for incoming MEDICATION_SYNC messages
                    scope.launch {
                        while (true) {
                            try {
                                val incoming = p2pService.receiveMessage()
                                Log.d("P2PLinkManager", "Caregiver received message type=${incoming.type} from=${incoming.senderId}")
                                if (incoming.type == P2PMessage.MessageType.MEDICATION_SYNC) {
                                    // Parse medications and upsert locally as this caregiver
                                    val meds = parseMedicationsFromData(incoming.data, incoming.senderId)
                                    Log.d("P2PLinkManager", "Caregiver parsing MEDICATION_SYNC with ${meds.size} meds from ${incoming.senderId}")
                                    meds.forEach { m ->
                                        try {
                                            medicationRepository.insertMedication(m)
                                        } catch (e: Exception) {
                                            Log.e("P2PLinkManager", "Failed to upsert medication for caregiver: ${e.message}")
                                        }
                                    }
                                } else if (incoming.type == P2PMessage.MessageType.MEDICATION_LOG) {
                                    val logs = parseLogsFromData(incoming.data, incoming.senderId)
                                    Log.d("P2PLinkManager", "Caregiver parsing MEDICATION_LOG with ${logs.size} logs from ${incoming.senderId}")
                                    logs.forEach { log ->
                                        try {
                                            medicationLogRepository.insertLog(log)
                                        } catch (e: Exception) {
                                            Log.e("P2PLinkManager", "Failed to insert medication log for caregiver: ${e.message}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Keep listening; any exception should not break the loop
                            }
                        }
                    }

                    return true
                }
            } catch (e: Exception) {
                // Try next peer
            }
        }
        
        return false
    }

    suspend fun sendMotivationalMessage(senderId: String, receiverId: String, message: String): Boolean {
        // Find the peer that matches the receiverId
        val peer = p2pService.discoveredPeers.value.find { it.name.contains(receiverId) }
        if (peer == null) {
            Log.w("P2PLinkManager", "No discovered peer found for receiverId=$receiverId")
            return false
        }
        val payload = P2PMessage(
            type = P2PMessage.MessageType.MOTIVATIONAL_MESSAGE,
            senderId = senderId,
            data = message
        )
        Log.d("P2PLinkManager", "Sending MOTIVATIONAL_MESSAGE from $senderId to ${peer.name}")
        return p2pService.sendMessageWithAck(peer, payload)
    }
    
    fun cleanup() {
        p2pService.cleanup()
    }
}

internal fun parseMedicationsFromData(data: String, patientId: String): List<dk.sdu.dosura.data.local.entity.Medication> {
    if (data.isBlank()) return emptyList()
    return try {
        val meds = gson.fromJson(data, Array<dk.sdu.dosura.data.local.entity.Medication>::class.java).toList()
        // Ensure the userId matches the patientId
        meds.map { med -> med.copy(userId = patientId) }
    } catch (e: Exception) {
        Log.e("P2PLinkManager", "Failed to parse medications JSON: ${e.message}")
        emptyList()
    }
}

internal fun parseLogsFromData(data: String, patientId: String): List<dk.sdu.dosura.data.local.entity.MedicationLog> {
    if (data.isBlank()) return emptyList()
    return try {
        val logs = gson.fromJson(data, Array<dk.sdu.dosura.data.local.entity.MedicationLog>::class.java).toList()
        logs
    } catch (e: Exception) {
        Log.e("P2PLinkManager", "Failed to parse logs JSON: ${e.message}")
        emptyList()
    }
}
