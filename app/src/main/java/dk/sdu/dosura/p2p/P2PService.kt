package dk.sdu.dosura.p2p

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
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
    private val pendingResolves = mutableSetOf<String>()
    
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
            
            // Start accepting connections
            Thread {
                while (!serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        if (!serverSocket!!.isClosed) {
                            Log.e("P2P", "Error accepting connection", e)
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
                    writer.println(message.toJson())
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("P2P", "Error sending message", e)
            false
        }
    }
    
    private fun handleClient(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val messageJson = reader.readLine()
                messageJson?.let {
                    val message = P2PMessage.fromJson(it)
                    messageChannel.trySend(message)
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
    val data: String
) {
    enum class MessageType {
        LINK_REQUEST,
        LINK_RESPONSE,
        MEDICATION_SYNC,
        MOTIVATIONAL_MESSAGE
    }
    
    fun toJson(): String {
        return """{"type":"$type","senderId":"$senderId","data":"$data"}"""
    }
    
    companion object {
        fun fromJson(json: String): P2PMessage {
            // Simple JSON parsing (could use Gson for complex cases)
            val typeMatch = Regex(""""type":"([^"]+)"""").find(json)
            val senderMatch = Regex(""""senderId":"([^"]+)"""").find(json)
            val dataMatch = Regex(""""data":"([^"]+)"""").find(json)
            
            return P2PMessage(
                type = MessageType.valueOf(typeMatch?.groupValues?.get(1) ?: "LINK_REQUEST"),
                senderId = senderMatch?.groupValues?.get(1) ?: "",
                data = dataMatch?.groupValues?.get(1) ?: ""
            )
        }
    }
}
