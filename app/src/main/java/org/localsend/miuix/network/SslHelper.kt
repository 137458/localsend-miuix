package org.localsend.miuix.network

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
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

    val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    val sslSocketFactory: SSLSocketFactory by lazy {
        sslContext.socketFactory
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
 * 与其声明一致，杜绝中间人。宿主连接前调用 [pin] 登记对端指纹。
 */
object FingerprintTrust {

    private val allowed = ConcurrentHashMap<String, Boolean>()

    private fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }

    /** 供 CIO/HttpURLConnection 注入的指纹校验 TrustManager。 */
    val trustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) throw CertificateException("Empty certificate chain")
            val fingerprint = sha256(chain[0])
            if (allowed[fingerprint] != true) {
                throw CertificateException("Untrusted server certificate fingerprint: $fingerprint")
            }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val pinnedSslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
    }

    val pinnedSslSocketFactory: SSLSocketFactory by lazy {
        pinnedSslContext.socketFactory
    }

    /** 登记对端证书指纹，使后续指向该服务的 TLS 连接通过校验。 */
    fun pin(fingerprint: String) {
        if (fingerprint.isNotBlank()) allowed[fingerprint] = true
    }

    /** 会话结束后解除某个指纹的信任。 */
    fun unpin(fingerprint: String) {
        allowed.remove(fingerprint)
    }

    fun clear() {
        allowed.clear()
    }
}
