package org.localsend.miuix.network

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Date

/**
 * 管理本机自签名证书（HTTPS 模式用）。
 *
 * 与官方 LocalSend 对齐：证书在首次启动时用 RSA-2048 运行时生成并持久化（之后复用），
 * 协议 §2 规定 HTTPS 模式下 fingerprint = 证书的 SHA-256 哈希。
 */
object TlsStore {

    const val KEYSTORE_FILENAME = "localsend_keystore.p12"
    const val KEY_ALIAS = "localsend_selfsigned"
    const val STORE_PASSWORD = "localsend"

    @Volatile
    private var cachedFingerprint: String? = null

    private fun keystoreFile(context: Context): File = File(context.filesDir, KEYSTORE_FILENAME)

    /**
     * 加载（或首次生成）本地 keystore。已存在且可读取时直接复用，损坏时重新生成，
     * 保证设备重启后 fingerprint 稳定（同证书复用）。
     */
    @Synchronized
    fun loadKeyStore(context: Context): KeyStore {
        val keystore = KeyStore.getInstance("PKCS12")
        val file = keystoreFile(context)
        if (file.exists()) {
            try {
                file.inputStream().use { keystore.load(it, STORE_PASSWORD.toCharArray()) }
                keystore.getCertificate(KEY_ALIAS)
                return keystore
            } catch (e: Exception) {
                // keystore 损坏或版本不符时走生成流程
            }
        }
        return createAndSave(file)
    }

    private fun createAndSave(file: File): KeyStore {
        val keystore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val dn = X500Name("CN=LocalSend")
        val now = Date()
        val notAfter = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)
        val builder = JcaX509v3CertificateBuilder(
            /* issuer = */ dn,
            /* serial = */ BigInteger.valueOf(System.currentTimeMillis()),
            now,
            notAfter,
            /* subject = */ dn,
            keyPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment or KeyUsage.dataEncipherment)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert: X509Certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        cert.checkValidity()
        cert.verify(keyPair.public)

        keystore.setKeyEntry(KEY_ALIAS, keyPair.private, STORE_PASSWORD.toCharArray(), arrayOf(cert))
        file.outputStream().use { keystore.store(it, STORE_PASSWORD.toCharArray()) }
        return keystore
    }

    /** HTTPS 模式下遵循协议 §2：fingerprint = 自签名证书的 SHA-256 哈希。 */
    fun fingerprint(context: Context): String {
        cachedFingerprint?.let { return it }
        val cert = loadKeyStore(context).getCertificate(KEY_ALIAS)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return FingerprintTrust.normalize(
            digest.joinToString("") { "%02x".format(it) }
        ).also { cachedFingerprint = it }
    }
}