package org.localsend.miuix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.network.CertificateBinding
import org.localsend.miuix.network.FingerprintTrust

class CertificateBindingTest {

    @Test
    fun officialHttpsFingerprintMustMatchObservedCert() {
        val declared = "AA:BB:CC:DD:EE:FF:11:22"
        val cert = "aabbccddeeff1122"
        assertTrue(CertificateBinding.dtoMatchesCert(declared, cert))
        assertFalse(CertificateBinding.dtoMatchesCert(declared, "0000111122223333"))
    }

    @Test
    fun blankFingerprintIsCompatibleWithHttpPeers() {
        assertTrue(CertificateBinding.dtoMatchesCert("", "aabbcc"))
        assertTrue(CertificateBinding.dtoMatchesCert("aabbcc", ""))
    }

    @Test
    fun unpinnedUnknownCertIsRejectedEvenWhenNoPinsExist() {
        FingerprintTrust.clear()
        try {
            assertFalse(FingerprintTrust.isAccepted("aabbccddeeff1122"))
            FingerprintTrust.pin("aa:bb:cc:dd")
            assertTrue(FingerprintTrust.isAccepted("AABBCCDD"))
            assertFalse(FingerprintTrust.isAccepted("00112233"))
            FingerprintTrust.trust("00:11:22:33")
            assertTrue(FingerprintTrust.isAccepted("00112233"))
        } finally {
            FingerprintTrust.clear()
        }
    }
}
