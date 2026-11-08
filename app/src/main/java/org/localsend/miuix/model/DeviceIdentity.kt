package org.localsend.miuix.model

/**
 * 设备身份判定，与官方 LocalSend 保持一致：双方都有指纹时按指纹（忽略大小写）比较，
 * 任一侧指纹缺失时退回 ip + 端口。设备目录合并、收藏匹配与会话归并都走这套规则。
 */
fun isSameDevice(
    leftFingerprint: String?,
    leftIp: String,
    leftPort: Int,
    rightFingerprint: String?,
    rightIp: String,
    rightPort: Int,
): Boolean {
    val left = leftFingerprint?.trim().orEmpty()
    val right = rightFingerprint?.trim().orEmpty()
    return if (left.isNotEmpty() && right.isNotEmpty()) {
        left.equals(right, ignoreCase = true)
    } else {
        leftIp == rightIp && leftPort == rightPort
    }
}

/** 列表 key 使用的设备身份串，择优顺序与 [isSameDevice] 一致。 */
fun deviceIdentityKey(
    fingerprint: String?,
    ip: String,
    port: Int,
): String {
    val print = fingerprint?.trim().orEmpty()
    return if (print.isNotEmpty()) print else "$ip:$port"
}
