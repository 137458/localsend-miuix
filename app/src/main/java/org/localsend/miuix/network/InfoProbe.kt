package org.localsend.miuix.network

import kotlinx.serialization.json.Json
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.core.NetworkConstants
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

/**
 * 单目标 `/info` 探查：HTTPS 优先、失败退回 HTTP，路由按新版到旧版回退。
 * 子网扫描与手动 IP 发送共用同一套探测、协议补齐与证书绑定规则。
 */
internal object InfoProbe {
    private val ROUTES = listOf(LocalSendRoutes.INFO_V2, LocalSendRoutes.INFO_V1)

    /**
     * @param localFingerprint 本机指纹，命中则视为自己并跳过；传空串表示不做自排除。
     * @param requireBodyFingerprintMatch HTTPS 下额外要求响应体指纹与证书一致（手动发送路径使用）。
     */
    fun probe(
        targetIp: String,
        port: Int = NetworkConstants.DEFAULT_PORT,
        json: Json,
        localFingerprint: String,
        requireBodyFingerprintMatch: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        followRedirects: Boolean,
    ): Device? {
        for (proto in listOf("https", "http")) {
            for (route in ROUTES) {
                var connection: HttpURLConnection? = null
                try {
                    connection =
                        LocalSendHttpClient.open(
                            url = "$proto://$targetIp:$port$route",
                            method = "GET",
                            connectTimeoutMs = connectTimeoutMs,
                            readTimeoutMs = readTimeoutMs,
                            followRedirects = followRedirects,
                            sslSocketFactory = SslHelper.sslSocketFactory,
                        ) {
                            setRequestProperty("Accept", "application/json")
                        }
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) continue

                    if (proto == "https") {
                        val certFingerprint = SslHelper.certSha256(connection as HttpsURLConnection)
                        if (certFingerprint.isBlank() || isSameFingerprint(certFingerprint, localFingerprint)) continue
                        val dto = json.decodeFromString<DeviceDto>(connection.readBody())
                        if (requireBodyFingerprintMatch &&
                            !CertificateBinding.dtoMatchesCert(dto.fingerprint, certFingerprint)
                        ) {
                            continue
                        }
                        return deviceFromHttpsProbe(dto, targetIp, certFingerprint)
                    }

                    val dto = json.decodeFromString<DeviceDto>(connection.readBody())
                    if (isSameFingerprint(dto.fingerprint, localFingerprint)) continue
                    return Device.fromDto(dto, targetIp)
                } catch (ignored: Exception) {
                    // 该协议/路由未响应，继续下一个候选
                } finally {
                    runCatching { connection?.disconnect() }
                }
            }
        }
        return null
    }

    private fun HttpURLConnection.readBody(): String = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun isSameFingerprint(
        left: String,
        right: String,
    ): Boolean = FingerprintTrust.normalize(left) == FingerprintTrust.normalize(right)
}
