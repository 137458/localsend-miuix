package org.localsend.miuix.network

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * 出站 HTTP 连接的唯一入口：统一 HTTPS 指纹固定工厂、禁用缓存与超时设置，
 * 握手、上传、取消、校验确认与发现探测都从这里取连接，避免各处重复拼装连接模板。
 * 调用方负责在请求前后执行 [FingerprintTrust.pin] / [FingerprintTrust.unpin]。
 */
internal object LocalSendHttpClient {
    fun open(
        url: String,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        followRedirects: Boolean = true,
        sslSocketFactory: SSLSocketFactory = FingerprintTrust.pinnedSslSocketFactory,
        configure: HttpURLConnection.() -> Unit = {},
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            if (this is HttpsURLConnection) {
                this.sslSocketFactory = sslSocketFactory
                hostnameVerifier = SslHelper.trustAllHostnameVerifier
            }
            requestMethod = method
            useCaches = false
            instanceFollowRedirects = followRedirects
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            configure()
        }
}
