package org.localsend.miuix

import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreClassificationTest {

    data class MediaStoreDestination(
        val baseDir: String,
        val mediaType: String
    )

    private fun resolveDestination(mimeType: String, isCategorized: Boolean): MediaStoreDestination {
        if (!isCategorized) {
            return MediaStoreDestination(Environment.DIRECTORY_DOWNLOADS + "/LocalSend", "downloads")
        }
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime.startsWith("image/") -> MediaStoreDestination(Environment.DIRECTORY_PICTURES + "/LocalSend", "images")
            lowerMime.startsWith("video/") -> MediaStoreDestination(Environment.DIRECTORY_MOVIES + "/LocalSend", "video")
            lowerMime.startsWith("audio/") -> MediaStoreDestination(Environment.DIRECTORY_MUSIC + "/LocalSend", "audio")
            else -> MediaStoreDestination(Environment.DIRECTORY_DOWNLOADS + "/LocalSend", "downloads")
        }
    }

    @Test
    fun testDefaultSavesToDownloadWhenCategorizeDisabled() {
        val dest = resolveDestination("image/jpeg", isCategorized = false)
        assertEquals(Environment.DIRECTORY_DOWNLOADS + "/LocalSend", dest.baseDir)
        assertEquals("downloads", dest.mediaType)
    }

    @Test
    fun testCategorizeEnabledRoutesAppropriately() {
        val imageDest = resolveDestination("image/png", isCategorized = true)
        assertEquals(Environment.DIRECTORY_PICTURES + "/LocalSend", imageDest.baseDir)
        assertEquals("images", imageDest.mediaType)

        val videoDest = resolveDestination("video/mp4", isCategorized = true)
        assertEquals(Environment.DIRECTORY_MOVIES + "/LocalSend", videoDest.baseDir)
        assertEquals("video", videoDest.mediaType)

        val audioDest = resolveDestination("audio/mpeg", isCategorized = true)
        assertEquals(Environment.DIRECTORY_MUSIC + "/LocalSend", audioDest.baseDir)
        assertEquals("audio", audioDest.mediaType)

        val otherDest = resolveDestination("application/pdf", isCategorized = true)
        assertEquals(Environment.DIRECTORY_DOWNLOADS + "/LocalSend", otherDest.baseDir)
        assertEquals("downloads", otherDest.mediaType)
    }
}
