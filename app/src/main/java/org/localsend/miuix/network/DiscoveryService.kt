package org.localsend.miuix.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

class DiscoveryService(
    private val scope: CoroutineScope,
    private val getLocalDevice: () -> Device,
    private val onDeviceDiscovered: (Device) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var multicastJob: Job? = null
    private var broadcastJob: Job? = null
    private var scanJob: Job? = null

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(this@DiscoveryService.json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 1500
                connectTimeoutMillis = 1000
                socketTimeoutMillis = 1500
            }
        }
    }

    fun start() {
        startMulticastListener()
        sendAnnouncement()
    }

    fun stop() {
        multicastJob?.cancel()
        broadcastJob?.cancel()
        scanJob?.cancel()
        multicastJob = null
        broadcastJob = null
        scanJob = null
    }

    private fun startMulticastListener() {
        multicastJob = scope.launch(Dispatchers.IO) {
            var socket: MulticastSocket? = null
            try {
                val group = InetAddress.getByName("224.0.0.167")
                socket = MulticastSocket(53317).apply {
                    reuseAddress = true
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
                                onDeviceDiscovered(device)
                            }
                        } catch (e: Exception) {
                            // Ignored malformed packets
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Regular timeout to check isActive
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun sendAnnouncement() {
        scope.launch(Dispatchers.IO) {
            try {
                val localDevice = getLocalDevice()
                val dto = localDevice.toDto(announcement = true)
                val payload = json.encodeToString(DeviceDto.serializer(), dto).toByteArray(Charsets.UTF_8)

                val multicastGroup = InetAddress.getByName("224.0.0.167")
                val broadcastAddr = InetAddress.getByName("255.255.255.255")

                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    // Send to multicast
                    val packet1 = DatagramPacket(payload, payload.size, multicastGroup, 53317)
                    socket.send(packet1)

                    // Send to broadcast
                    val packet2 = DatagramPacket(payload, payload.size, broadcastAddr, 53317)
                    socket.send(packet2)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scanSubnet(onScanProgress: ((current: Int, total: Int) -> Unit)? = null) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            val baseIp = NetworkUtils.getSubnetBaseIp() ?: return@launch
            val localIps = NetworkUtils.getLocalIpAddresses()
            val localDevice = getLocalDevice()
            val total = 254
            var current = 0

            val deferreds = (1..total).map { i ->
                async {
                    val targetIp = "$baseIp.$i"
                    if (!localIps.contains(targetIp)) {
                        try {
                            val url = "http://$targetIp:${localDevice.port}/api/localsend/v2/info"
                            val response = httpClient.get(url)
                            val dto = response.body<DeviceDto>()
                            if (dto.fingerprint != localDevice.fingerprint) {
                                val device = Device.fromDto(dto, targetIp)
                                onDeviceDiscovered(device)
                            }
                        } catch (ignored: Exception) {
                            // Offline or unreachable IP
                        }
                    }
                    synchronized(this@DiscoveryService) {
                        current++
                        onScanProgress?.invoke(current, total)
                    }
                }
            }
            deferreds.awaitAll()
        }
    }
}
