package org.localsend.miuix.network

/**
 * Binds a LocalSend DeviceDto fingerprint (protocol §2) to the TLS certificate
 * observed on the wire. Discovery still needs trust-all to *read* /info over
 * HTTPS self-signed certs; this check rejects a MITM whose cert does not match
 * the fingerprint the peer declared in that same response.
 */
object CertificateBinding {
    fun dtoMatchesCert(declaredFingerprint: String, certSha256: String): Boolean {
        if (declaredFingerprint.isBlank() || certSha256.isBlank()) return true
        return FingerprintTrust.normalize(declaredFingerprint) == FingerprintTrust.normalize(certSha256)
    }
}
