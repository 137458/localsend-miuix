package org.localsend.miuix.network

import android.content.Context
import android.net.wifi.WifiManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
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
import kotlinx.serialization.decodeFromString
import org.localsend.miuix.core.AppJson
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.core.NetworkConstants
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** 监听端配置快照：组播组地址 + 发现超时，二者在绑定与设置读取两侧总是结伴出现。 */
internal data class ListenerConfig(
    val group: InetAddress,
    val timeoutSeconds: Int,
) {
    companion object {
        /** 解析当前设置；组地址非法时返回 null（不触发重建，等待合法值再切换）。 */
        fun parse(
            group: String,
            timeoutSeconds: Int,
        ): ListenerConfig? =
            try {
                ListenerConfig(InetAddress.getByName(group), timeoutSeconds)
            } catch (e: Exception) {
                null
            }
    }
}

/**
 * 兜底解析：设置中的组播地址非法时退回默认组播组，避免一条错误配置把监听端彻底打死。
 * 仅在连默认地址都不可解析时返回 null（正常系统下不会发生）。
 */
internal fun resolveListenerConfig(
    group: String,
    timeoutSeconds: Int,
): ListenerConfig? =
    ListenerConfig.parse(group, timeoutSeconds)
        ?: ListenerConfig.parse(NetworkConstants.DEFAULT_MULTICAST_IP, timeoutSeconds)

/** 监听 socket 的空闲超时（毫秒）。与“发现超时”设置解耦，保证取消/重建最多等待该时长。 */
private const val LISTENER_SOCKET_TIMEOUT_MS = 3000

/** 单目标扫描探测上限（毫秒）：避免“能连却不响应”的目标把 254×4 次探测拖长。 */
private const val SCAN_PROBE_TIMEOUT_MS = 3000L

/**
 * 扫描进度上报器：保证一次扫描只上报一次完成态（current >= total）。
 * 正常结束、无可扫描网卡的早退、异常退出都走同一条完成路径，调用方据此复位扫描状态。
 */
internal class ScanProgressReporter(
    private val onProgress: ((current: Int, total: Int) -> Unit)?,
) {
    private val finished = AtomicBoolean(false)

    fun report(
        current: Int,
        total: Int,
    ) {
        // 已上报完成态后不再回吐进度，避免调用方在复位后又被旧进度拉回“扫描中”
        if (finished.get()) return
        if (current >= total) {
            finish(total)
        } else {
            onProgress?.invoke(current, total)
        }
    }

    /** 上报完成态；重复调用无副作用。 */
    fun finish(total: Int = 0) {
        if (!finished.compareAndSet(false, true)) return
        onProgress?.invoke(total, total)
    }
}

/**
 * 监听端 staleness 判定：绑定配置与当前设置不一致即需重建 socket。
 * 当前组地址非法（解析为 null）时返回 false（不触发重建风暴，等待合法值再切换）。
 */
internal fun isListenerStale(
    bound: ListenerConfig,
    current: ListenerConfig?,
): Boolean = current != null && current != bound

class DiscoveryService(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getLocalDevice: () -> Device,
    private val onDeviceDiscovered: (Device) -> Unit,
    private val getMulticastGroup: () -> String = { NetworkConstants.DEFAULT_MULTICAST_IP },
    private val getDiscoveryTimeoutSeconds: () -> Int = { NetworkConstants.DEFAULT_DISCOVERY_TIMEOUT_SECONDS },
    private val getNetworkWhitelist: () -> String = { "" },
    private val getNetworkBlacklist: () -> String = { "" },
) {
    private val json = AppJson.default
    private val scanDispatcher = Dispatchers.IO.limitedParallelism(32)
    private var multicastJob: Job? = null
    private var periodicBroadcastJob: Job? = null
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** 当前在用的监听 socket：停止/重建前主动关闭，唤醒阻塞中的 receive，避免等空闲超时。 */
    @Volatile
    private var activeSocket: MulticastSocket? = null

    private val httpClient by lazy {
        HttpClient(CIO) {
            followRedirects = false
            engine {
                https {
                    trustManager = SslHelper.discoveryTrustManager
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
        // 先关 socket 再取消协程：阻塞中的 receive 立即返回，退出不必等空闲超时
        closeActiveSocket()
        multicastJob?.cancel()
        periodicBroadcastJob?.cancel()
        scanJob?.cancel()
        multicastJob = null
        periodicBroadcastJob = null
        scanJob = null
        releaseLocks()
    }

    /** 关闭当前监听 socket；重复调用安全。 */
    private fun closeActiveSocket() {
        val socket = activeSocket ?: return
        activeSocket = null
        try {
            socket.close()
        } catch (ignored: Exception) {
        }
    }

    private fun acquireLocks() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null && multicastLock == null) {
                multicastLock =
                    wifiManager.createMulticastLock(NetworkConstants.MULTICAST_LOCK_TAG).apply {
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
        // 先关旧 socket 再起新任务：避免新旧两个 socket 同时绑定同端口、同组播组而重复收包
        closeActiveSocket()
        multicastJob?.cancel()
        multicastJob =
            scope.launch(Dispatchers.IO) {
                while (isActive) {
                    var socket: MulticastSocket? = null
                    try {
                        val bound = resolveListenerConfig(getMulticastGroup(), getDiscoveryTimeoutSeconds())
                        if (bound == null) {
                            // 理论上不可达（默认组播地址恒可解析）；兜底等待，绝不让监听线程被打死
                            delay(2000)
                            continue
                        }
                        socket =
                            MulticastSocket(null).apply {
                                reuseAddress = true
                                bind(InetSocketAddress(NetworkConstants.DEFAULT_PORT))
                                joinGroup(bound.group)
                                // 固定空闲超时：探测设置变更与停止/重建不必等“发现超时”（最长 30 秒）
                                soTimeout = LISTENER_SOCKET_TIMEOUT_MS
                            }
                        activeSocket = socket

                        val buffer = ByteArray(65535)
                        while (isActive) {
                            // 组地址或超时设置变更后重建 socket：周期广播每轮即时读新值，
                            // 监听端滞留旧组会出现“广播发新组、监听留旧组”的单向发现
                            if (isListenerStale(bound, resolveListenerConfig(getMulticastGroup(), getDiscoveryTimeoutSeconds()))) break
                            try {
                                val packet = DatagramPacket(buffer, buffer.size)
                                socket.receive(packet)
                                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                                val senderIp = packet.address.hostAddress ?: continue

                                // Ignore our own broadcast
                                val localDevice = getLocalDevice()
                                // 自检不过滤：本机包可能经任意接口回来，套用白/黑名单会误判为自己
                                if (NetworkUtils.getLocalIpAddresses().contains(senderIp)) {
                                    continue
                                }

                                try {
                                    val dto = json.decodeFromString<DeviceDto>(text)
                                    if (dto.fingerprint != localDevice.fingerprint) {
                                        val device = Device.fromDto(dto, senderIp)
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
                        if (activeSocket === socket) activeSocket = null
                        try {
                            socket?.close()
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
    }

    private fun startPeriodicBroadcast() {
        periodicBroadcastJob?.cancel()
        periodicBroadcastJob =
            scope.launch(Dispatchers.IO) {
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

                // 与监听端共用同一兜底口径：组播地址非法时退回默认组，避免广播被静默丢弃
                val multicastGroup =
                    resolveListenerConfig(getMulticastGroup(), getDiscoveryTimeoutSeconds())?.group
                        ?: return@launch
                val broadcasts = NetworkUtils.getBroadcastAddresses(getNetworkWhitelist(), getNetworkBlacklist())

                DatagramSocket().use { socket ->
                    socket.broadcast = true

                    // 1. Send to multicast 224.0.0.167
                    try {
                        val packet1 = DatagramPacket(payload, payload.size, multicastGroup, NetworkConstants.DEFAULT_PORT)
                        socket.send(packet1)
                    } catch (ignored: Exception) {
                    }

                    // 2. Send to all broadcast addresses (directed subnet + 255.255.255.255)
                    for (bcast in broadcasts) {
                        try {
                            val addr = InetAddress.getByName(bcast)
                            val packet2 = DatagramPacket(payload, payload.size, addr, NetworkConstants.DEFAULT_PORT)
                            socket.send(packet2)
                        } catch (ignored: Exception) {
                        }
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
            for (route in listOf(LocalSendRoutes.REGISTER_V2, LocalSendRoutes.REGISTER_V1)) {
                try {
                    val url = "${targetDevice.url}$route"
                    val response =
                        httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(localDevice.toDto())
                        }
                    if (response.status == io.ktor.http.HttpStatusCode.OK) {
                        break
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    /** 发起子网扫描并返回该轮任务；被新一轮扫描取代时返回的 Job 以取消态完成。 */
    fun scanSubnet(onScanProgress: ((current: Int, total: Int) -> Unit)? = null): Job {
        scanJob?.cancel()
        val job =
            scope.launch(scanDispatcher) {
                val reporter = ScanProgressReporter(onScanProgress)
                var total = 0
                try {
                    val baseIps = NetworkUtils.getSubnetBaseIps(getNetworkWhitelist(), getNetworkBlacklist())
                    // 无可用网卡（例如名单把接口全部排除）也要走完成上报：否则调用方的扫描状态永远为 true
                    if (baseIps.isEmpty()) return@launch
                    val localIps = NetworkUtils.getLocalIpAddresses(getNetworkWhitelist(), getNetworkBlacklist())
                    val localDevice = getLocalDevice()
                    total = baseIps.size * 254
                    val current = AtomicInteger(0)

                    val deferreds =
                        baseIps.flatMap { baseIp ->
                            (1..254).map { i ->
                                async {
                                    val targetIp = "$baseIp.$i"
                                    if (!localIps.contains(targetIp)) {
                                        // Try HTTPS first (LocalSend default), then HTTP
                                        var found = false
                                        for (proto in listOf("https", "http")) {
                                            if (found) break
                                            for (route in listOf(LocalSendRoutes.INFO_V2, LocalSendRoutes.INFO_V1)) {
                                                if (found) break
                                                try {
                                                    val url = "$proto://$targetIp:${NetworkConstants.DEFAULT_PORT}$route"
                                                    val response =
                                                        httpClient.get(url) {
                                                            timeout {
                                                                // 单目标探测封顶：不受“发现超时”设置放大，
                                                                // 避免 254×4 次探测被个别无响应目标拖长
                                                                requestTimeoutMillis = SCAN_PROBE_TIMEOUT_MS
                                                            }
                                                        }
                                                    val dto = response.body<DeviceDto>()
                                                    if (dto.fingerprint != localDevice.fingerprint) {
                                                        val device = Device.fromDto(dto, targetIp)
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
                                    reporter.report(current.incrementAndGet(), total)
                                }
                            }
                        }
                    deferreds.awaitAll()
                } finally {
                    // 正常结束/早退/异常统一收口；被新扫描取代时不复位，避免把新一轮状态踩掉
                    if (isActive) reporter.finish(total)
                }
            }
        scanJob = job
        return job
    }
}
