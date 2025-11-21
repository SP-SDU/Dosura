package dk.sdu.dosura.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal val gson = Gson()

class P2PService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val serviceName = "Dosura_P2P"
    private val serviceType = "_dosura._tcp."
    
    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private val _discoveredPeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerInfo>> = _discoveredPeers
    
    private val messageChannel = Channel<P2PMessage>(Channel.UNLIMITED)
    data class IncomingP2PMessage(val message: P2PMessage, val reply: (P2PMessage) -> Unit)
    private val incomingChannel = Channel<IncomingP2PMessage>(Channel.UNLIMITED)
    private val pendingResolves = mutableSetOf<String>()
    // pendingAcks map no longer used; ACKs are replied to directly on socket-level
    private var serverUserId: String? = null
    
    fun startAdvertising(port: Int, userId: String) {
        try {
            // Avoid binding twice if we already have a server socket
            if (serverSocket != null && !serverSocket!!.isClosed) {
                Log.d("P2P", "Server already bound on port ${serverSocket!!.localPort}, skipping bind")
            } else {
                try {
                    serverSocket = ServerSocket(port)
                } catch (bindEx: java.net.BindException) {
                    Log.w("P2P", "Requested port $port in use, falling back to ephemeral port", bindEx)
                    serverSocket = ServerSocket(0)
                }
            }
            
            val actualPort = serverSocket?.localPort ?: port
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "${this@P2PService.serviceName}_$userId"
                serviceType = this@P2PService.serviceType
                setPort(actualPort)
            }
            
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e("P2P", "Service registration failed: $errorCode")
                }
                
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e("P2P", "Service unregistration failed: $errorCode")
                }
                
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                    Log.d("P2P", "Service registered: ${serviceInfo?.serviceName}")
                }
                
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                    Log.d("P2P", "Service unregistered")
                }
            }
            
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            serverUserId = userId
            
            // Start accepting connections
                Thread {
                while (!serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        Log.d("P2P", "Accepted connection from ${clientSocket.inetAddress.hostAddress}:${clientSocket.port}")
                        handleClient(clientSocket)
                    } catch (e: java.net.SocketTimeoutException) {
                        // On some devices, accept() can EAGAIN/SocketTimeoutException frequently; avoid noisy logs and tight-loop CPU spin
                        if (!serverSocket!!.isClosed) {
                            Log.d("P2P", "Accept timed out (EAGAIN); continuing")
                            try { Thread.sleep(5) } catch (_: Exception) { }
                        }
                    } catch (e: Exception) {
                        if (!serverSocket!!.isClosed) {
                            Log.e("P2P", "Error accepting connection", e)
                            try { Thread.sleep(10) } catch (_: Exception) { }
                        }
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e("P2P", "Error starting advertising", e)
        }
    }
    
    fun startDiscovery() {
        // Acquire multicast lock for physical devices
        multicastLock = wifiManager.createMulticastLock("DosuraP2P").apply {
            setReferenceCounted(true)
            acquire()
            Log.d("P2P", "Multicast lock acquired")
        }
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d("P2P", "Discovery started for $serviceType")
            }
            
            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d("P2P", "Discovery stopped")
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let {
                    Log.d("P2P", "Service found: ${it.serviceName}, type: ${it.serviceType}")
                    if (it.serviceType.startsWith("_dosura._tcp")) {
                        val serviceName = it.serviceName
                        // Prevent duplicate resolve attempts
                        if (pendingResolves.contains(serviceName)) {
                            Log.d("P2P", "Already resolving $serviceName, skipping")
                            return
                        }
                        pendingResolves.add(serviceName)
                        
                        nsdManager.resolveService(it, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.e("P2P", "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                                pendingResolves.remove(serviceName)
                            }
                            
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                                serviceInfo?.let { info ->
                                    pendingResolves.remove(serviceName)
                                    val hostAddress = info.host?.hostAddress ?: ""
                                    if (hostAddress.isNotEmpty()) {
                                        val peer = PeerInfo(
                                            name = info.serviceName,
                                            host = hostAddress,
                                            port = info.port
                                        )
                                        val currentPeers = _discoveredPeers.value.toMutableList()
                                        if (!currentPeers.any { it.name == peer.name }) {
                                            currentPeers.add(peer)
                                            _discoveredPeers.value = currentPeers
                                        }
                                        Log.d("P2P", "✓ Peer discovered: ${peer.name} at ${peer.host}:${peer.port}")
                                    } else {
                                        Log.e("P2P", "Service resolved but no host address: ${info.serviceName}")
                                    }
                                }
                            }
                        })
                    }
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let {
                    val currentPeers = _discoveredPeers.value.toMutableList()
                    currentPeers.removeAll { peer -> peer.name == it.serviceName }
                    _discoveredPeers.value = currentPeers
                    Log.d("P2P", "Peer lost: ${it.serviceName}")
                }
            }
            
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("P2P", "Discovery start failed: $errorCode")
            }
            
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e("P2P", "Discovery stop failed: $errorCode")
            }
        }
        
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
    
    suspend fun sendMessage(peer: PeerInfo, message: P2PMessage): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Socket(peer.host, peer.port).use { socket ->
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println(gson.toJson(message))
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("P2P", "Error sending message", e)
            false
        }
    }

    suspend fun sendMessageWithAck(peer: PeerInfo, message: P2PMessage, timeoutMs: Int = 3000): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                Socket(peer.host, peer.port).use { socket ->
                    // Ensure read timeout so we don't block indefinitely
                    socket.soTimeout = timeoutMs
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    val msgId = message.messageId ?: java.util.UUID.randomUUID().toString()
                    val msgWithId = message.copy(messageId = msgId, timestamp = System.currentTimeMillis())
                    writer.println(gson.toJson(msgWithId))

                    try {
                        val line = reader.readLine()
                        if (line.isNullOrBlank()) return@use false
                        val resp = gson.fromJson(line, P2PMessage::class.java)
                        val isAck = (resp.type == P2PMessage.MessageType.ACK && resp.messageId == msgId)
                        if (isAck) {
                            // Try to read a subsequent message (e.g., response body) and forward to messageChannel
                            try {
                                // Give the server some time to write a response after ACK; network on Android devices can be slow
                                socket.soTimeout = 2000
                                val nextLine = reader.readLine()
                                if (!nextLine.isNullOrBlank()) {
                                    val maybeMessage = gson.fromJson(nextLine, P2PMessage::class.java)
                                    // Avoid loops: do not push ACK back
                                    if (maybeMessage.type != P2PMessage.MessageType.ACK) {
                                        messageChannel.trySend(maybeMessage)
                                    }
                                } else {
                                    Log.d("P2P", "No immediate follow-up message after ACK from ${peer.name}")
                                }
                            } catch (e: Exception) {
                                Log.w("P2P", "No follow-up message after ACK or error reading response: ${e.message}")
                            }
                            return@use true
                        }
                        return@use false
                    } catch (re: java.net.SocketTimeoutException) {
                        Log.w("P2P", "Timed out waiting for ACK from ${peer.name}")
                        return@use false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("P2P", "Error in sendMessageWithAck", e)
            false
        }
    }

    // New method: send message, wait for ACK, and if a response body is received on the same socket, return it.
    suspend fun sendMessageWithResponse(peer: PeerInfo, message: P2PMessage, timeoutMs: Int = 3000): P2PMessage? {
        return try {
            withContext(Dispatchers.IO) {
                Socket(peer.host, peer.port).use { socket ->
                    socket.soTimeout = timeoutMs
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    val msgId = message.messageId ?: java.util.UUID.randomUUID().toString()
                    val msgWithId = message.copy(messageId = msgId, timestamp = System.currentTimeMillis())
                    writer.println(gson.toJson(msgWithId))

                    try {
                        val line = reader.readLine()
                        if (line.isNullOrBlank()) return@use null
                        val resp = gson.fromJson(line, P2PMessage::class.java)
                        Log.d("P2P", "sendMessageWithResponse received initial resp type=${resp.type} id=${resp.messageId}")
                        val isAck = (resp.type == P2PMessage.MessageType.ACK && resp.messageId == msgId)
                        if (isAck) {
                            try {
                                socket.soTimeout = 2000
                                val nextLine = reader.readLine()
                                    if (!nextLine.isNullOrBlank()) {
                                        val maybeMessage = gson.fromJson(nextLine, P2PMessage::class.java)
                                        Log.d("P2P", "sendMessageWithResponse got follow-up message type=${maybeMessage.type} id=${maybeMessage.messageId}")
                                        // Ensure follow-up message corresponds to the request
                                        if (maybeMessage.type != P2PMessage.MessageType.ACK && maybeMessage.messageId == msgId) {
                                            return@use maybeMessage
                                        } else if (maybeMessage.type != P2PMessage.MessageType.ACK) {
                                            Log.w("P2P", "sendMessageWithResponse received follow-up message not matching request id=${msgId} got=${maybeMessage.messageId}; ignoring")
                                        }
                                }
                            } catch (ignore: Exception) { }
                            return@use null
                        }
                        return@use null
                    } catch (re: java.net.SocketTimeoutException) {
                        Log.w("P2P", "Timed out waiting for ACK from ${peer.name}")
                        return@use null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("P2P", "Error in sendMessageWithResponse", e)
            null
        }
    }
    
    private fun handleClient(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val messageJson = reader.readLine()
                messageJson?.let {
                    try {
                        val message = gson.fromJson(it, P2PMessage::class.java)
                        Log.d("P2P", "Received message type=${message.type} from sender=${message.senderId} messageId=${message.messageId}")
                        // If this is an ACK, ignore it (ACKs are handled on client sockets)
                        if (message.type == P2PMessage.MessageType.ACK) {
                            // Ignore
                        } else {
                            // Send back ACK
                            try {
                                val ack = P2PMessage(
                                    type = P2PMessage.MessageType.ACK,
                                    senderId = serverUserId ?: "",
                                    data = "",
                                    messageId = message.messageId,
                                    timestamp = System.currentTimeMillis()
                                )
                                Log.d("P2P", "Sending ACK for messageId=${message.messageId} to ${socket.inetAddress.hostAddress}:${socket.port}")
                                // Explicitly flush the output so the client is certain to receive the ACK
                                val ackWriter = PrintWriter(socket.getOutputStream(), true)
                                ackWriter.println(gson.toJson(ack))
                                try {
                                    ackWriter.flush()
                                } catch (e: Exception) {
                                    Log.w("P2P", "Failed to flush ACK writer: ${e.message}")
                                }
                            } catch (e: Exception) {
                                Log.w("P2P", "Failed to send ACK: ${e.message}")
                            }
                            // Allow higher-level components to respond on this same socket
                            // Keep the socket open until replies on this socket have been sent (or timeout)
                            val writer = PrintWriter(socket.getOutputStream(), true)
                            val replyLatch = java.util.concurrent.CountDownLatch(1)
                            val replyFn: (P2PMessage) -> Unit = { resp ->
                                try {
                                    writer.println(gson.toJson(resp))
                                    try {
                                        writer.flush()
                                    } catch (e: Exception) {
                                        Log.w("P2P", "Failed to flush reply writer: ${e.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.w("P2P", "Failed to write reply on same socket: ${e.message}")
                                } finally {
                                    // Signal that a reply has been written (even on error) so the server can close the socket
                                    try { replyLatch.countDown() } catch (_: Exception) {}
                                }
                            }
                            incomingChannel.trySend(IncomingP2PMessage(message, replyFn))
                            // Wait a short moment for a reply to be written on this socket, if any. This ensures the other side (caregiver) has time to read ACK and the follow-up message.
                            try {
                                val didReply = replyLatch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
                                if (didReply) {
                                    Log.d("P2P", "Reply(s) were written on same socket; closing now: ${socket.inetAddress.hostAddress}:${socket.port}")
                                } else {
                                    Log.d("P2P", "No reply written on this socket within timeout; closing: ${socket.inetAddress.hostAddress}:${socket.port}")
                                }
                            } catch (e: Exception) {
                                Log.w("P2P", "Error waiting for reply latch: ${e.message}")
                            }
                            messageChannel.trySend(message)
                        }
                    } catch (e: Exception) {
                        Log.e("P2P", "Failed to parse incoming message", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("P2P", "Error handling client", e)
            } finally {
                socket.close()
            }
        }.start()
    }
    
    suspend fun receiveMessage(): P2PMessage {
        return messageChannel.receive()
    }

    suspend fun receiveIncomingMessage(): IncomingP2PMessage {
        return incomingChannel.receive()
    }
    
    fun stopAdvertising() {
        registrationListener?.let {
            nsdManager.unregisterService(it)
        }
        serverSocket?.close()
    }
    
    fun stopDiscovery() {
        discoveryListener?.let {
            nsdManager.stopServiceDiscovery(it)
        }
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("P2P", "Multicast lock released")
            }
        }
        multicastLock = null
    }
    
    fun cleanup() {
        stopAdvertising()
        stopDiscovery()
        _discoveredPeers.value = emptyList()
        pendingResolves.clear()
    }
}

data class PeerInfo(
    val name: String,
    val host: String,
    val port: Int
)

data class P2PMessage(
    val type: MessageType,
    val senderId: String,
    val data: String,
    val messageId: String? = null,
    val timestamp: Long? = null
) {
    enum class MessageType {
        LINK_REQUEST,
        LINK_RESPONSE,
        MEDICATION_SYNC,
        MEDICATION_LOG,
        ACK,
        MOTIVATIONAL_MESSAGE
    }
    
    fun toJson(): String {
        return gson.toJson(this)
    }

    companion object {
        fun fromJson(json: String): P2PMessage {
            return gson.fromJson(json, P2PMessage::class.java)
        }
    }
}
