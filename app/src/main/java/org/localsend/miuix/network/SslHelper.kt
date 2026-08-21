package org.localsend.miuix.network

import java.security.SecureRandom
import java.security.cert.X509Certificate
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
