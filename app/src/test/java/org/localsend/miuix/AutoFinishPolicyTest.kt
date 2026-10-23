package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.util.AutoFinishPolicy

class AutoFinishPolicyTest {
    @Test
    fun finishedSessionIsAutoDismissedOnlyWhenAutoFinishEnabled() {
        assertEquals(true, AutoFinishPolicy.shouldAutoDismiss(finished = true, autoFinish = true))
    }

    @Test
    fun finishedSessionKeepsResultLingerWhenAutoFinishDisabled() {
        assertEquals(false, AutoFinishPolicy.shouldAutoDismiss(finished = true, autoFinish = false))
    }

    @Test
    fun unfinishedSessionIsNeverAutoDismissed() {
        assertEquals(false, AutoFinishPolicy.shouldAutoDismiss(finished = false, autoFinish = true))
        assertEquals(false, AutoFinishPolicy.shouldAutoDismiss(finished = false, autoFinish = false))
    }
}
