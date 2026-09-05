package org.localsend.miuix.network

import android.content.Context
import android.net.wifi.WifiManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.X509TrustManager

class DiscoveryService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getLocalDevice: () -> Device,
    private val onDeviceDiscovered: (Device) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val scanDispatcher = Dispatchers.IO.limitedParallelism(32)
    private var multicastJob: Job? = null
    private var periodicBroadcastJob: Job? = null
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val httpClient by lazy {
        HttpClient(CIO) {
            followRedirects = false
            engine {
                https {
                    trustManager = SslHelper.trustAllCerts[0] as X509TrustManager
                }
            }
            install(ContentNegotiation) {
                json(this@DiscoveryService.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 2000
                connectTimeoutMillis = 1000
                socketTimeoutMillis = 2000
            }
        }
    }

    fun start() {
        acquireLocks()
        startMulticastListener()
        startPeriodicBroadcast()
        scanSubnet()
    }

    fun ensureStarted() {
        acquireLocks()
        if (multicastJob?.isActive != true) {
            startMulticastListener()
        }
        if (periodicBroadcastJob?.isActive != true) {
            startPeriodicBroadcast()
        }
    }

    fun stop() {
        multicastJob?.cancel()
        periodicBroadcastJob?.cancel()
        scanJob?.cancel()
        multicastJob = null
        periodicBroadcastJob = null
        scanJob = null
        releaseLocks()
    }

    private fun acquireLocks() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("LocalSendMiuixMulticastLock").apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startMulticastListener() {
        multicastJob?.cancel()
        multicastJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var socket: MulticastSocket? = null
                try {
                    val group = InetAddress.getByName("224.0.0.167")
                    socket = MulticastSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(53317))
                        joinGroup(group)
                        soTimeout = 3000
                    }

                    val buffer = ByteArray(65535)
                    while (isActive) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                            val senderIp = packet.address.hostAddress ?: continue

                            // Ignore our own broadcast
                            val localDevice = getLocalDevice()
                            if (NetworkUtils.getLocalIpAddresses().contains(senderIp)) {
                                continue
                            }

                            try {
                                val dto = json.decodeFromString<DeviceDto>(text)
                                if (dto.fingerprint != localDevice.fingerprint) {
                                    val device = Device.fromDto(dto, senderIp)
                                    if (device.protocol.equals("https", ignoreCase = true) && device.fingerprint.isNotBlank()) {
                                        FingerprintTrust.trust(device.fingerprint)
                                    }
                                    onDeviceDiscovered(device)

                                    // If it is an announcement, send back direct register response
                                    if (dto.announce == true) {
                                        sendDirectResponse(device)
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignored malformed packets
                            }
                        } catch (e: java.net.SocketTimeoutException) {
                            // Regular timeout to check isActive
                        } catch (e: Exception) {
                            if (!isActive) break
                            throw e
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        delay(2000)
                    }
                } finally {
                    try {
                        socket?.close()
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    private fun startPeriodicBroadcast() {
        periodicBroadcastJob?.cancel()
        periodicBroadcastJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sendAnnouncement()
                delay(10000)
            }
        }
    }

    fun sendAnnouncement() {
        scope.launch(Dispatchers.IO) {
            try {
                val localDevice = getLocalDevice()
                val dto = localDevice.toDto(announce = true)
                val payload = json.encodeToString(DeviceDto.serializer(), dto).toByteArray(Charsets.UTF_8)

                val multicastGroup = InetAddress.getByName("224.0.0.167")
                val broadcasts = NetworkUtils.getBroadcastAddresses()

                DatagramSocket().use { socket ->
                    socket.broadcast = true

                    // 1. Send to multicast 224.0.0.167
                    try {
                        val packet1 = DatagramPacket(payload, payload.size, multicastGroup, 53317)
                        socket.send(packet1)
                    } catch (ignored: Exception) {}

                    // 2. Send to all broadcast addresses (directed subnet + 255.255.255.255)
                    for (bcast in broadcasts) {
                        try {
                            val addr = InetAddress.getByName(bcast)
                            val packet2 = DatagramPacket(payload, payload.size, addr, 53317)
                            socket.send(packet2)
                        } catch (ignored: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendDirectResponse(targetDevice: Device) {
        scope.launch(Dispatchers.IO) {
            val localDevice = getLocalDevice()
            for (route in listOf("/api/localsend/v2/register", "/api/localsend/v1/register")) {
                try {
                    val url = "${targetDevice.url}$route"
                    val response = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(localDevice.toDto())
                    }
                    if (response.status == io.ktor.http.HttpStatusCode.OK) {
                        break
                    }
                } catch (ignored: Exception) {}
            }
        }
    }

    fun scanSubnet(onScanProgress: ((current: Int, total: Int) -> Unit)? = null) {
        scanJob?.cancel()
        scanJob = scope.launch(scanDispatcher) {
            val baseIps = NetworkUtils.getSubnetBaseIps()
            if (baseIps.isEmpty()) return@launch
            val localIps = NetworkUtils.getLocalIpAddresses()
            val localDevice = getLocalDevice()
            val total = baseIps.size * 254
            val current = AtomicInteger(0)

            val deferreds = baseIps.flatMap { baseIp ->
                (1..254).map { i ->
                    async {
                        val targetIp = "$baseIp.$i"
                        if (!localIps.contains(targetIp)) {
                            // Try HTTPS first (LocalSend default), then HTTP
                            var found = false
                            for (proto in listOf("https", "http")) {
                                if (found) break
                                for (route in listOf("/api/localsend/v2/info", "/api/localsend/v1/info")) {
                                    if (found) break
                                    try {
                                        val url = "$proto://$targetIp:53317$route"
                                        val response = httpClient.get(url)
                                        val dto = response.body<DeviceDto>()
                                        if (dto.fingerprint != localDevice.fingerprint) {
                                            val device = Device.fromDto(dto, targetIp)
                                            if (device.protocol.equals("https", ignoreCase = true) && device.fingerprint.isNotBlank()) {
                                                FingerprintTrust.trust(device.fingerprint)
                                            }
                                            onDeviceDiscovered(device)
                                            sendDirectResponse(device)
                                            found = true
                                        }
                                    } catch (ignored: Exception) {
                                        // Target not responding on this proto/route
                                    }
                                }
                            }
                        }
                        val progress = current.incrementAndGet()
                        onScanProgress?.invoke(progress, total)
                    }
                }
            }
            deferreds.awaitAll()
        }
    }
}
