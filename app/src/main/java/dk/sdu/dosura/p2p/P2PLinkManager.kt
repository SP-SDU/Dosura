package dk.sdu.dosura.p2p

import android.util.Log
import dk.sdu.dosura.data.local.entity.CaregiverLink
import dk.sdu.dosura.data.repository.CaregiverLinkRepository
import dk.sdu.dosura.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2PLinkManager @Inject constructor(
    private val p2pService: P2PService,
    private val caregiverLinkRepository: CaregiverLinkRepository,
    private val medicationRepository: MedicationRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    fun startPatientMode(userId: String, linkCode: String) {
        // Start advertising this patient's service
        p2pService.startAdvertising(port = 9876, userId = userId)
        
        // Listen for caregiver link requests
        scope.launch {
            while (true) {
                try {
                    val message = p2pService.receiveMessage()
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
                        
                        // Send response with patient's medications
                        val medications = medicationRepository.getAllMedications(userId).first()
                        val response = P2PMessage(
                            type = P2PMessage.MessageType.LINK_RESPONSE,
                            senderId = userId,
                            data = medications.joinToString("|") { 
                                "${it.id}:${it.name}:${it.dosage}:${it.reminderTimes.joinToString(",")}"
                            }
                        )
                        
                        // Find the peer and send response
                        val peer = p2pService.discoveredPeers.value.find { 
                            it.name.contains(message.senderId) 
                        }
                        peer?.let { p2pService.sendMessage(it, response) }
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
                // Serialize medications
                val data = medications.joinToString("|") { med ->
                    val reminders = med.reminderTimes.joinToString(",")
                    val days = med.daysOfWeek.joinToString(",")
                    // Escape '|' and ':' characters by replacing with encoded sequences
                    "${med.id}:${med.name.replace("|", "%7C").replace(":", "%3A")}:${med.dosage.replace("|", "%7C")}:${reminders.replace("|", "%7C")}:${days}"
                }
                val message = P2PMessage(
                    type = P2PMessage.MessageType.MEDICATION_SYNC,
                    senderId = userId,
                    data = data
                )

                // Send to all discovered peers (caregivers)
                val peers = p2pService.discoveredPeers.value
                peers.forEach { peer ->
                    scope.launch {
                        try {
                            p2pService.sendMessage(peer, message)
                        } catch (e: Exception) {
                            Log.e("P2PLinkManager", "Failed to send medication sync to ${peer.name}", e)
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
            
            val sent = p2pService.sendMessage(peer, request)
            if (sent) {
                // Wait for response
                try {
                    val response = p2pService.receiveMessage()
                    if (response.type == P2PMessage.MessageType.LINK_RESPONSE) {
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
                        // Start receiver loop for incoming MEDICATION_SYNC messages
                        scope.launch {
                            while (true) {
                                try {
                                    val incoming = p2pService.receiveMessage()
                                    if (incoming.type == P2PMessage.MessageType.MEDICATION_SYNC) {
                                        // Parse medications and upsert locally as this caregiver
                                        val meds = parseMedicationsFromData(incoming.data, incoming.senderId)
                                        meds.forEach { m ->
                                            try {
                                                medicationRepository.insertMedication(m)
                                            } catch (e: Exception) {
                                                Log.e("P2PLinkManager", "Failed to upsert medication for caregiver: ${e.message}")
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
        }
        
        return false
    }
    
    fun cleanup() {
        p2pService.cleanup()
    }
}

private fun parseMedicationsFromData(data: String, patientId: String): List<dk.sdu.dosura.data.local.entity.Medication> {
    if (data.isBlank()) return emptyList()
    val meds = data.split("|")
    val list = mutableListOf<dk.sdu.dosura.data.local.entity.Medication>()
    for (entry in meds) {
        val parts = entry.split(":")
        if (parts.size >= 5) {
            val id = parts[0].toLongOrNull() ?: 0L
            val name = parts[1].replace("%7C", "|").replace("%3A", ":")
            val dosage = parts[2].replace("%7C", "|").replace("%3A", ":")
            val reminders = if (parts[3].isBlank()) emptyList() else parts[3].split(",")
            val days = if (parts[4].isBlank()) emptyList() else parts[4].split(",").mapNotNull { it.toIntOrNull() }
            val med = dk.sdu.dosura.data.local.entity.Medication(
                id = id,
                name = name,
                dosage = dosage,
                instructions = "",
                photoUri = null,
                userId = patientId,
                reminderTimes = reminders,
                daysOfWeek = days,
                startDate = System.currentTimeMillis(),
                isActive = true
            )
            list.add(med)
        }
    }
    return list
}
