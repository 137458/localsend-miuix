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

    /**
     * Web Share 回传：仅在设置开启且发送方为浏览器（web 类型）时自动接受。
     * 信任模型：deviceType 为发送方自报，局域网内程序可伪装成 web 类型；开启即等同向全网
     * 开放自动接收（PIN 校验仍生效）。官方以接收端本地 Web 会话态为门槛，本实现以设置摘要
     * 明示该差异，仅在可信网络开启。
     */
    fun shouldAutoAcceptWebShare(
        webShareAutoAccept: Boolean,
        senderType: DeviceType,
    ): Boolean = webShareAutoAccept && senderType == DeviceType.web
}
