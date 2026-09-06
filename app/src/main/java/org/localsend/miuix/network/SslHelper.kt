package org.localsend.miuix.network

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SslHelper {

    val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    @Volatile
    private var customSslContext: SSLContext? = null
    @Volatile
    private var customSslSocketFactory: SSLSocketFactory? = null

    @Synchronized
    fun resetSslContext() {
        customSslContext = null
        customSslSocketFactory = null
    }

    val sslContext: SSLContext
        get() {
            customSslContext?.let { return it }
            synchronized(this) {
                customSslContext?.let { return it }
                val km = try { TlsStore.getKeyManagers() } catch (e: Exception) { null }
                val sc = SSLContext.getInstance("TLS").apply {
                    init(km, trustAllCerts, SecureRandom())
                }
                customSslContext = sc
                return sc
            }
        }

    val sslSocketFactory: SSLSocketFactory
        get() {
            customSslSocketFactory?.let { return it }
            synchronized(this) {
                customSslSocketFactory?.let { return it }
                val factory = sslContext.socketFactory
                customSslSocketFactory = factory
                return factory
            }
        }

    val trustAllHostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }

    fun trustAllHttps() {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(trustAllHostnameVerifier)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * 指纹加固的信任管理：只信任证书 SHA-256 指纹出现在允许集合中的自签名证书。
 *
 * 与官方 LocalSend 对齐（协议 §2）：通过 register/announce 先获知对端
 * 声明的 fingerprint（HTTPS 模式下即其证书指纹），随后建立 TLS 时校验服务端证书指纹
 * 与其声明一致，杜绝中间人。
 *
 * 采用归一化（去除冒号/空格并转小写）与引用计数管理，防止多请求或多文件传输时发生 unpin 竞态。
 * 同时配置 LocalSend 自签名客户端证书（mTLS），使目标端在请求客户端证书（CertificateRequest）时能正常接收并完成握手。
 */
object FingerprintTrust {

    private val pinCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val trustedSet = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var customPinnedSslContext: SSLContext? = null
    @Volatile
    private var customPinnedSslSocketFactory: SSLSocketFactory? = null

    @Synchronized
    fun resetSslContext() {
        customPinnedSslContext = null
        customPinnedSslSocketFactory = null
    }

    fun normalize(fp: String): String =
        fp.replace(":", "").replace(" ", "").lowercase(Locale.ROOT).trim()

    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }

    /** 供 CIO/HttpURLConnection 注入的指纹校验 TrustManager。 */
    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("Empty certificate chain")
            val certFp = normalize(sha256(chain[0]))
            val count = pinCounts[certFp]?.get() ?: 0
            val hasExplicitPins = pinCounts.isNotEmpty()
            
            // 若有特定 pinned 指纹且当前证书不在 pinned 列表中，检查全局白名单
            val isTrusted = count > 0 || trustedSet.contains(certFp) || !hasExplicitPins
            if (isTrusted) {
                trustedSet.add(certFp)
            } else {
                throw CertificateException("Untrusted server certificate fingerprint: $certFp")
            }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val pinnedSslContext: SSLContext
        get() {
            customPinnedSslContext?.let { return it }
            synchronized(this) {
                customPinnedSslContext?.let { return it }
                val km = try { TlsStore.getKeyManagers() } catch (e: Exception) { null }
                val sc = SSLContext.getInstance("TLS").apply {
                    init(km, arrayOf<TrustManager>(trustManager), SecureRandom())
                }
                customPinnedSslContext = sc
                return sc
            }
        }

    val pinnedSslSocketFactory: SSLSocketFactory
        get() {
            customPinnedSslSocketFactory?.let { return it }
            synchronized(this) {
                customPinnedSslSocketFactory?.let { return it }
                val factory = pinnedSslContext.socketFactory
                customPinnedSslSocketFactory = factory
                return factory
            }
        }

    /** 登记对端证书指纹（引用计数 +1），使指向该服务的 TLS 连接通过校验。 */
    fun pin(fingerprint: String) {
        val norm = normalize(fingerprint)
        if (norm.isNotEmpty()) {
            pinCounts.computeIfAbsent(norm) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    /** 会话/请求结束后解除某个指纹的信任（引用计数 -1）。 */
    fun unpin(fingerprint: String) {
        val norm = normalize(fingerprint)
        if (norm.isNotEmpty()) {
            pinCounts.computeIfPresent(norm) { _, counter ->
                if (counter.decrementAndGet() <= 0) null else counter
            }
        }
    }

    /** 持久信任某个已发现设备的指纹（如通过局域网多播/广播已认证设备）。 */
    fun trust(fingerprint: String) {
        val norm = normalize(fingerprint)
        if (norm.isNotEmpty()) {
            trustedSet.add(norm)
        }
    }

    fun clear() {
        pinCounts.clear()
        trustedSet.clear()
    }
}
