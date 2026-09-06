package org.localsend.miuix.network

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.KeyStore
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * 专用于 LocalSend mTLS（双向 TLS 认证）的客户端与服务端 KeyManager。
 *
 * LocalSend 协议 §2 规定：在 HTTPS 模式下通信双方使用自签名证书进行互信认证。
 * 当对端服务端（如官方 LocalSend 应用）在 TLS 握手阶段发送 CertificateRequest 时，
 * 本 KeyManager 保证始终提供本设备的自签名证书与私钥，杜绝因标准 KeyManager 无法在
 * CertificateRequest 提供的可信任 CA 列表匹配到自签名颁发者而导致放弃发送客户端证书（触发 TLS 116 certificate_required 告警）。
 */
class LocalSendKeyManager(
    private val standardKeyManager: X509ExtendedKeyManager?,
    private val keyStore: KeyStore,
    private val alias: String,
    private val password: CharArray
) : X509ExtendedKeyManager() {

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> {
        return arrayOf(alias)
    }

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String {
        return alias
    }

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String {
        return alias
    }

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? {
        return standardKeyManager?.getServerAliases(keyType, issuers) ?: arrayOf(alias)
    }

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? {
        return standardKeyManager?.chooseServerAlias(keyType, issuers, socket) ?: alias
    }

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
        return standardKeyManager?.chooseEngineServerAlias(keyType, issuers, engine) ?: alias
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
        val chain = keyStore.getCertificateChain(alias ?: this.alias)
        return chain?.mapNotNull { it as? X509Certificate }?.toTypedArray()
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        return keyStore.getKey(alias ?: this.alias, password) as? PrivateKey
    }
}
