package org.localsend.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.localsend.miuix.util.InstallHelper
import org.localsend.miuix.util.InstallSource
import org.localsend.miuix.util.OpenableFile
import org.localsend.miuix.util.pickFileSource
import org.localsend.miuix.util.pickOpenableFile
import java.io.File

class InstallHelperTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** 临时目录下必然不存在的路径，用于模拟“声明了路径但文件已丢失”。 */
    private fun missingPath(fileName: String): String = File(tmp.root, "missing/$fileName").absolutePath

    @Test
    fun isApk_acceptsCaseVariants() {
        assertTrue(InstallHelper.isApk("app.apk"))
        assertTrue(InstallHelper.isApk("APP.APK"))
        assertTrue(InstallHelper.isApk("App.Apk"))
        assertTrue(InstallHelper.isApk("应用安装包.apk"))
    }

    @Test
    fun isApk_rejectsNonApkNames() {
        assertFalse(InstallHelper.isApk("app.zip"))
        assertFalse(InstallHelper.isApk("app.apk.bak"))
        assertFalse(InstallHelper.isApk("apk"))
        assertFalse(InstallHelper.isApk(""))
    }

    @Test
    fun firstApkName_picksFirstApkAmongMixed() {
        assertEquals("b.APK", InstallHelper.firstApkName(listOf("a.zip", "b.APK", "c.apk")))
        assertNull(InstallHelper.firstApkName(listOf("a.zip", "b.bin")))
        assertNull(InstallHelper.firstApkName(emptyList()))
    }

    @Test
    fun pickFileSource_prefersExistingDiskFile() {
        val file: File = tmp.newFile("real.apk")
        val source = pickFileSource(path = file.absolutePath, uriString = "content://media/xxx")
        assertEquals(InstallSource.DiskFile(file.absolutePath), source)
    }

    @Test
    fun pickFileSource_fallsBackToContentUriWhenPathMissing() {
        assertEquals(
            InstallSource.ContentUri("content://media/xxx"),
            pickFileSource(path = null, uriString = "content://media/xxx"),
        )
        assertEquals(
            InstallSource.ContentUri("content://media/xxx"),
            pickFileSource(path = missingPath("ghost.apk"), uriString = "content://media/xxx"),
        )
    }

    @Test
    fun pickFileSource_returnsNullWhenNothingAccessible() {
        assertNull(pickFileSource(path = null, uriString = null))
        assertNull(pickFileSource(path = null, uriString = "  "))
        assertNull(pickFileSource(path = missingPath("ghost.apk"), uriString = null))
    }

    @Test
    fun pickOpenableFile_keepsEntryOrderAndPrefersFirstOpenable() {
        val first =
            OpenableFile(
                name = "doc.pdf",
                path = null,
                uriString = "content://media/received/doc",
                mimeType = "application/pdf",
            )
        val second =
            OpenableFile(
                name = "second.bin",
                path = missingPath("second.bin"),
                uriString = null,
            )
        val pick = pickOpenableFile(listOf(first, second))
        assertEquals(first, pick?.file)
        assertEquals(InstallSource.ContentUri("content://media/received/doc"), pick?.source)
    }

    @Test
    fun pickOpenableFile_usesDiskFileWhenAvailable() {
        val file: File = tmp.newFile("photo.jpg")
        val pick =
            pickOpenableFile(
                listOf(
                    OpenableFile(
                        name = "photo.jpg",
                        path = file.absolutePath,
                        uriString = null,
                        mimeType = "image/jpeg",
                    ),
                ),
            )
        assertEquals(InstallSource.DiskFile(file.absolutePath), pick?.source)
        assertEquals("image/jpeg", pick?.file?.mimeType)
    }

    @Test
    fun pickOpenableFile_returnsNullWhenNoEntryAccessible() {
        assertNull(pickOpenableFile(emptyList()))
        assertNull(pickOpenableFile(listOf(OpenableFile(name = "ghost.bin", path = missingPath("ghost.bin"), uriString = null))))
    }
}
