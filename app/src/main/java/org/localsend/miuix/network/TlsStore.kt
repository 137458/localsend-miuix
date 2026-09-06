package org.localsend.miuix.network

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
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
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.X509ExtendedKeyManager

/**
 * 管理本机自签名证书（HTTPS 模式与 TLS 双向认证 mTLS 用）。
 *
 * 与官方 LocalSend 对齐：证书在首次启动时用 RSA-2048 运行时生成并持久化（之后复用），
 * 协议 §2 规定 HTTPS 模式下 fingerprint = 证书的 SHA-256 哈希。
 */
object TlsStore {

    const val KEYSTORE_FILENAME = "localsend_keystore.p12"
    const val KEY_ALIAS = "localsend_selfsigned"
    const val STORE_PASSWORD = "localsend"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedFingerprint: String? = null

    @Volatile
    private var cachedKeyStore: KeyStore? = null

    @Volatile
    private var cachedKeyManagers: Array<KeyManager>? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun resolveContext(context: Context?): Context {
        return context?.applicationContext ?: appContext
            ?: throw IllegalStateException("TlsStore context not initialized. Call TlsStore.init(context) first.")
    }

    private fun keystoreFile(context: Context): File = File(context.filesDir, KEYSTORE_FILENAME)

    /**
     * 加载（或首次生成）本地 keystore。已存在且可读取时直接复用，损坏时重新生成，
     * 保证设备重启后 fingerprint 稳定（同证书复用）。
     */
    @Synchronized
    fun loadKeyStore(context: Context? = null): KeyStore {
        cachedKeyStore?.let { return it }
        val ctx = resolveContext(context)
        val keystore = KeyStore.getInstance("PKCS12")
        val file = keystoreFile(ctx)
        if (file.exists()) {
            try {
                file.inputStream().use { keystore.load(it, STORE_PASSWORD.toCharArray()) }
                if (keystore.getCertificate(KEY_ALIAS) != null) {
                    cachedKeyStore = keystore
                    return keystore
                }
            } catch (e: Exception) {
                // keystore 损坏或版本不符时走生成流程
            }
        }
        val newStore = createAndSave(file)
        cachedKeyStore = newStore
        return newStore
    }

    /** 重新生成自签名证书并更新指纹。 */
    @Synchronized
    fun regenerateKeyStore(context: Context? = null): KeyStore {
        val ctx = resolveContext(context)
        val file = keystoreFile(ctx)
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (ignored: Exception) {}
        cachedFingerprint = null
        cachedKeyStore = null
        cachedKeyManagers = null
        FingerprintTrust.resetSslContext()
        SslHelper.resetSslContext()
        val newStore = createAndSave(file)
        cachedKeyStore = newStore
        fingerprint(ctx)
        return newStore
    }

    /**
     * 获取用于 SSLContext 的 KeyManager，用于在 TLS 握手时向对端提供本设备的自签名证书（mTLS 客户端认证）。
     */
    @Synchronized
    fun getKeyManagers(context: Context? = null): Array<KeyManager> {
        cachedKeyManagers?.let { return it }
        val ks = loadKeyStore(context)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, STORE_PASSWORD.toCharArray())
        val standardKm = kmf.keyManagers.filterIsInstance<X509ExtendedKeyManager>().firstOrNull()
        val customKm = LocalSendKeyManager(
            standardKeyManager = standardKm,
            keyStore = ks,
            alias = KEY_ALIAS,
            password = STORE_PASSWORD.toCharArray()
        )
        val managers: Array<KeyManager> = arrayOf(customKm)
        cachedKeyManagers = managers
        return managers
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
        // 增加 clientAuth 与 serverAuth，全面支持服务端与客户端 TLS 双向认证
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth))
        )
        // 补充 SAN（Subject Alternative Name），避免 Android/Netty 严格 TLS 模式下握手失败
        val sans = GeneralNames(
            arrayOf(
                GeneralName(GeneralName.dNSName, "localhost"),
                GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                GeneralName(GeneralName.iPAddress, "0.0.0.0")
            )
        )
        builder.addExtension(Extension.subjectAlternativeName, false, sans)

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert: X509Certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        cert.checkValidity()
        cert.verify(keyPair.public)

        keystore.setKeyEntry(KEY_ALIAS, keyPair.private, STORE_PASSWORD.toCharArray(), arrayOf(cert))
        file.outputStream().use { keystore.store(it, STORE_PASSWORD.toCharArray()) }
        return keystore
    }

    /** 遵循协议 §2：fingerprint = 自签名证书的 SHA-256 哈希。 */
    fun fingerprint(context: Context? = null): String {
        cachedFingerprint?.let { return it }
        val cert = loadKeyStore(context).getCertificate(KEY_ALIAS)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return FingerprintTrust.normalize(
            digest.joinToString("") { "%02x".format(it) }
        ).also { cachedFingerprint = it }
    }
}
