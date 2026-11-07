package org.localsend.miuix.updates

/** 远端 size 为 0/-1 表示未知；已知时必须与落盘字节数严格一致。 */
internal fun downloadedSizeMatches(
    expected: Long,
    actual: Long,
): Boolean = expected <= 0L || expected == actual
