package org.localsend.miuix.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * LocalSend Miuix 页面路由定义。
 * 遵循 miuix-nav 规范，必须标注 @Serializable 以支持进程生命周期持久化与状态恢复。
 */
@Serializable
sealed interface AppRoute : NavKey {
    /**
     * 主框架路由：承载 LiquidGlassBottomBar 悬浮底栏与 首页/传输历史/设置 HorizontalPager。
     */
    @Serializable
    data object Main : AppRoute

    /**
     * 软件更新页：检查最新版本、展示 Release 说明、下载并安装 APK。
     */
    @Serializable
    data object Update : AppRoute
}
