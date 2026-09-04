package org.localsend.miuix

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.util.ShareIntentHelper

class ShareIntentHelperTest {

    @Test
    fun testIsShareIntent_null() {
        assertFalse(ShareIntentHelper.isShareIntent(null))
    }

    @Test
    fun testIsShareAction_null() {
        assertFalse(ShareIntentHelper.isShareAction(null))
    }

    @Test
    fun testIsShareAction_actionMain() {
        assertFalse(ShareIntentHelper.isShareAction(Intent.ACTION_MAIN))
    }

    @Test
    fun testIsShareAction_actionSend() {
        assertTrue(ShareIntentHelper.isShareAction(Intent.ACTION_SEND))
    }

    @Test
    fun testIsShareAction_actionSendMultiple() {
        assertTrue(ShareIntentHelper.isShareAction(Intent.ACTION_SEND_MULTIPLE))
    }

    @Test
    fun testIsShareAction_actionView() {
        assertTrue(ShareIntentHelper.isShareAction(Intent.ACTION_VIEW))
    }
}
