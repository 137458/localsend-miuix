package org.localsend.miuix.util

import org.localsend.miuix.model.DeviceType

/** 接收侧自动接受判定：快速保存与 Web Share 回传共用，供服务端与单元测试复用。 */
object AcceptPolicy {
    /**
     * 快速保存判定：
     * 关闭快速保存时不自动接受；开启且未限定收藏时全部自动接受；
     * 开启且限定收藏时，仅发送方在收藏列表内才自动接受。
     */
    fun shouldAutoAccept(
        quickSave: Boolean,
        favoritesOnly: Boolean,
        isFavorite: Boolean,
    ): Boolean = quickSave && (!favoritesOnly || isFavorite)

    /** Web Share 回传：仅在设置开启且发送方为浏览器（web 类型）时自动接受。 */
    fun shouldAutoAcceptWebShare(
        webShareAutoAccept: Boolean,
        senderType: DeviceType,
    ): Boolean = webShareAutoAccept && senderType == DeviceType.web
}
