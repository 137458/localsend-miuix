package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.core.AppActions
import org.localsend.miuix.core.LocalSendRoutes
import org.localsend.miuix.core.NetworkConstants
import org.localsend.miuix.core.ProtocolMessages
import org.localsend.miuix.model.Device
import org.localsend.miuix.model.DeviceDto

class ConstantsConsistencyTest {

    @Test
    fun protocolRoutesShareVersionedApiPrefix() {
        assertEquals("/", LocalSendRoutes.WEB_ROOT)

        val v1Routes = listOf(LocalSendRoutes.INFO_V1, LocalSendRoutes.REGISTER_V1)
        assertEquals(2, v1Routes.count { it.startsWith("/api/localsend/v1/") })

        val v2Routes = listOf(
            LocalSendRoutes.INFO_V2,
            LocalSendRoutes.REGISTER_V2,
            LocalSendRoutes.PREPARE_UPLOAD,
            LocalSendRoutes.UPLOAD,
            LocalSendRoutes.CANCEL,
            LocalSendRoutes.PREPARE_DOWNLOAD,
            LocalSendRoutes.DOWNLOAD,
            LocalSendRoutes.DOWNLOAD_ZIP
        )
        assertEquals(8, v2Routes.count { it.startsWith("/api/localsend/v2/") })
        assertEquals(8, v2Routes.distinct().size)
    }

    @Test
    fun receiverCancelAndDeclineMessagesStayDistinguishable() {
        val canceled = ProtocolMessages.CANCELED_BY_RECEIVER
        val declined = ProtocolMessages.DECLINED_BY_USER
        assertFalse(canceled == declined)
        assertFalse(declined.contains(canceled))
        assertFalse(canceled.contains(declined))
    }

    @Test
    fun defaultPortAndProtocolVersionMatchDeviceDefaults() {
        val device = Device(alias = "a", fingerprint = "f", ip = "1.1.1.1")
        assertEquals(NetworkConstants.DEFAULT_PORT, device.port)
        assertEquals(NetworkConstants.PROTOCOL_VERSION, device.version)
        assertEquals(DeviceDto(alias = "a", fingerprint = "f").version, NetworkConstants.PROTOCOL_VERSION)
        assertTrue(NetworkConstants.DEFAULT_MULTICAST_IP.startsWith("224."))
    }

    @Test
    fun appActionsUseApplicationIdPrefix() {
        val actions = listOf(
            AppActions.ACTION_CANCEL_TRANSFER,
            AppActions.ACTION_ACCEPT_TRANSFER,
            AppActions.ACTION_DECLINE_TRANSFER,
            AppActions.ACTION_START_SERVICE,
            AppActions.ACTION_STOP_SERVICE
        )
        assertEquals(5, actions.count { it.startsWith("org.localsend.miuix.") })
        assertEquals(5, actions.distinct().size)
    }
}