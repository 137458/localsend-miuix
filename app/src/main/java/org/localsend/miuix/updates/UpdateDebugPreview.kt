package org.localsend.miuix.updates

import org.localsend.miuix.manager.UpdateCheckResult
import org.localsend.miuix.manager.UpdateManager

/**
 * Debug 构建专用的更新弹窗预览数据。
 *
 * 版本号固定高于任何已发布版本，不依赖网络即可稳定弹出弹窗；说明文本刻意超过折叠阈值，
 * 且不带 APK——预览里的主操作只会打开 Releases 页面，不产生下载任务与缓存文件。
 */
object UpdateDebugPreview {
    const val LATEST_VERSION: String = "9.9.9"

    private const val RELEASE_URL = "https://github.com/${UpdateManager.GITHUB_OWNER}/${UpdateManager.GITHUB_REPO}/releases"

    private val CHANGELOG =
        """
        ## v9.9.9 模拟更新

        这份说明由 Debug 构建的「测试更新弹窗」入口注入，不来自远端 release。

        ### 新增
        - 单行版本摘要：**当前 → 最新**，附带发布日期。
        - 更新说明支持 12 行折叠、240dp 限高与上下 40dp 羽化。
        - 「忽略此版本」为可撤销链接，「稍后」仅在本次进程内生效。

        ### 修复
        - 取消下载后不再残留半包文件。
        - 校验失败时删除安装包并给出固定文案。

        ### 说明
        1. 预览数据固定高于当前版本，断网也能弹出弹窗。
        2. 预览不携带 APK，主操作只会打开 Releases 页面。
        3. 需要验证下载与安装链路时，请使用真实 release。
        """.trimIndent()

    fun sample(currentVersion: String): UpdateCheckResult {
        val current = UpdatePrompt.normalized(currentVersion)
        return UpdateCheckResult(
            currentVersion = current,
            latestVersion = LATEST_VERSION,
            releaseTitle = "LocalSend Miuix $LATEST_VERSION",
            changelog = CHANGELOG,
            publishedAt = "",
            releaseUrl = RELEASE_URL,
            apk = null,
            hasUpdate = UpdatePrompt.compare(LATEST_VERSION, current) > 0,
        )
    }
}
