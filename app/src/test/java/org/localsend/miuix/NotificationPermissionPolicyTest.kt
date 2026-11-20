package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.notification.NotificationPermissionPolicy
import org.localsend.miuix.notification.NotificationPermissionPolicy.EntryAction

/** 通知权限申请策略：冷启动只问一次，永久拒绝后改为直达系统设置。 */
class NotificationPermissionPolicyTest {
    @Test
    fun `asks on launch only when never asked`() {
        assertTrue(NotificationPermissionPolicy.shouldAskOnLaunch(sdkInt = 33, granted = false, askRecorded = false))
        assertFalse(NotificationPermissionPolicy.shouldAskOnLaunch(sdkInt = 33, granted = true, askRecorded = false))
        assertFalse(NotificationPermissionPolicy.shouldAskOnLaunch(sdkInt = 33, granted = false, askRecorded = true))
        assertFalse(NotificationPermissionPolicy.shouldAskOnLaunch(sdkInt = 31, granted = false, askRecorded = false))
    }

    @Test
    fun `entry requests permission while the system still allows asking`() {
        assertEquals(
            EntryAction.REQUEST,
            NotificationPermissionPolicy.entryAction(sdkInt = 33, granted = false, askRecorded = false, showRationale = false),
        )
        assertEquals(
            EntryAction.REQUEST,
            NotificationPermissionPolicy.entryAction(sdkInt = 33, granted = false, askRecorded = true, showRationale = true),
        )
    }

    @Test
    fun `entry opens system settings when permission is permanent denied or already granted`() {
        assertEquals(
            EntryAction.OPEN_SETTINGS,
            NotificationPermissionPolicy.entryAction(sdkInt = 33, granted = false, askRecorded = true, showRationale = false),
        )
        assertEquals(
            EntryAction.OPEN_SETTINGS,
            NotificationPermissionPolicy.entryAction(sdkInt = 33, granted = true, askRecorded = true, showRationale = false),
        )
        assertEquals(
            EntryAction.OPEN_SETTINGS,
            NotificationPermissionPolicy.entryAction(sdkInt = 31, granted = false, askRecorded = false, showRationale = false),
        )
    }
}
