package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Test
import org.localsend.miuix.model.DeviceType
import org.localsend.miuix.util.AcceptPolicy

class AcceptPolicyTest {
    @Test
    fun quickSaveDisabledAlwaysRequiresManualApproval() {
        assertEquals(false, AcceptPolicy.shouldAutoAccept(quickSave = false, favoritesOnly = false, isFavorite = false))
        assertEquals(false, AcceptPolicy.shouldAutoAccept(quickSave = false, favoritesOnly = false, isFavorite = true))
        assertEquals(false, AcceptPolicy.shouldAutoAccept(quickSave = false, favoritesOnly = true, isFavorite = false))
        assertEquals(false, AcceptPolicy.shouldAutoAccept(quickSave = false, favoritesOnly = true, isFavorite = true))
    }

    @Test
    fun quickSaveWithoutFavoritesLimitAcceptsEverySender() {
        assertEquals(true, AcceptPolicy.shouldAutoAccept(quickSave = true, favoritesOnly = false, isFavorite = false))
        assertEquals(true, AcceptPolicy.shouldAutoAccept(quickSave = true, favoritesOnly = false, isFavorite = true))
    }

    @Test
    fun quickSaveWithFavoritesLimitAcceptsFavoriteSender() {
        assertEquals(true, AcceptPolicy.shouldAutoAccept(quickSave = true, favoritesOnly = true, isFavorite = true))
    }

    @Test
    fun quickSaveWithFavoritesLimitStillAsksForNonFavoriteSender() {
        assertEquals(false, AcceptPolicy.shouldAutoAccept(quickSave = true, favoritesOnly = true, isFavorite = false))
    }

    @Test
    fun webShareAutoAcceptOnlyAppliesToWebSendersWhenEnabled() {
        assertEquals(true, AcceptPolicy.shouldAutoAcceptWebShare(webShareAutoAccept = true, senderType = DeviceType.web))
        assertEquals(false, AcceptPolicy.shouldAutoAcceptWebShare(webShareAutoAccept = true, senderType = DeviceType.mobile))
        assertEquals(false, AcceptPolicy.shouldAutoAcceptWebShare(webShareAutoAccept = true, senderType = DeviceType.desktop))
        assertEquals(false, AcceptPolicy.shouldAutoAcceptWebShare(webShareAutoAccept = false, senderType = DeviceType.web))
    }
}
