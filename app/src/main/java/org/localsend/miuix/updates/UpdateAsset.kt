package org.localsend.miuix.updates

/** 远端 release 里挑出来的 APK 资产，下载与校验都只看这一份快照。 */
data class UpdateAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val digest: String? = null,
)
