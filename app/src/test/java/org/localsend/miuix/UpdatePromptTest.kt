package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.localsend.miuix.updates.UpdatePrompt

/**
 * 更新提示策略：版本写法必须先归一，否则用户忽略过 1.2.0 之后远端写成 v1.2 还会再弹一次。
 * 「稍后」只活在进程内（promptedVersion），「忽略此版本」才是持久记录且可撤销。
 */
class UpdatePromptTest {
    @Test
    fun `normalized drops v prefix whitespace and build metadata`() {
        assertEquals("1.2.0", UpdatePrompt.normalized(" v1.2.0 "))
        assertEquals("1.2.0", UpdatePrompt.normalized("V1.2.0"))
        assertEquals("1.2.0", UpdatePrompt.normalized("1.2.0+build.7"))
        assertEquals("", UpdatePrompt.normalized("   "))
        assertEquals("", UpdatePrompt.normalized(null))
    }

    @Test
    fun `same version tolerates v prefix and trailing zeros`() {
        assertTrue(UpdatePrompt.sameVersion("v1.2", "1.2.0"))
        assertTrue(UpdatePrompt.sameVersion("1.2.0", "1.2.0+build"))
        assertFalse(UpdatePrompt.sameVersion("1.2.1", "1.2.0"))
    }

    @Test
    fun `release outranks its prerelease`() {
        assertTrue(UpdatePrompt.compare("1.3.0", "1.3.0-beta.1") > 0)
        assertTrue(UpdatePrompt.compare("1.3.0-beta.2", "1.3.0-beta.1") > 0)
        assertTrue(UpdatePrompt.compare("1.3.0-beta", "1.3.0-beta.1") < 0)
    }

    @Test
    fun `notify only for a newer unignored and unprompted version`() {
        assertTrue(UpdatePrompt.shouldNotify(latestVersion = "v1.4.0", ignoredVersion = null, promptedVersion = null))
        assertFalse(UpdatePrompt.shouldNotify(latestVersion = "v1.4.0", ignoredVersion = "1.4.0", promptedVersion = null))
        assertFalse(UpdatePrompt.shouldNotify(latestVersion = "v1.4.0", ignoredVersion = null, promptedVersion = "1.4.0"))
        assertFalse(UpdatePrompt.shouldNotify(latestVersion = "  ", ignoredVersion = null, promptedVersion = null))
    }

    @Test
    fun `prompted version only blocks the exact same version`() {
        assertTrue(UpdatePrompt.shouldNotify(latestVersion = "1.5.0", ignoredVersion = null, promptedVersion = "1.4.0"))
    }

    @Test
    fun `toggle ignored records a normalized version and clears it again`() {
        assertEquals("1.4.0", UpdatePrompt.toggleIgnored(currentIgnored = null, latestVersion = "v1.4.0"))
        assertNull(UpdatePrompt.toggleIgnored(currentIgnored = "1.4.0", latestVersion = "v1.4.0"))
        assertNull(UpdatePrompt.toggleIgnored(currentIgnored = null, latestVersion = "   "))
    }

    @Test
    fun `is ignored compares by normalized version`() {
        assertTrue(UpdatePrompt.isIgnored(ignoredVersion = "1.4", latestVersion = "v1.4.0"))
        assertFalse(UpdatePrompt.isIgnored(ignoredVersion = "1.3.0", latestVersion = "v1.4.0"))
        assertFalse(UpdatePrompt.isIgnored(ignoredVersion = null, latestVersion = "v1.4.0"))
    }
}
