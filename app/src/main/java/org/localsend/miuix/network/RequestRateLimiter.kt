package org.localsend.miuix.network

import java.util.concurrent.ConcurrentHashMap

/**
 * 429 限流：按来源 IP 统计 prepare-upload 请求频率，滑动窗口内超限即拒绝。
 */
internal class RequestRateLimiter {
    private val requestHits = ConcurrentHashMap<String, MutableList<Long>>()

    /** 429 限流：单 IP 在滑动窗口时间内 prepare-upload 请求过多时返回 true。 */
    internal fun tooFrequent(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = 1_000L
        val maxHits = 10
        val list = requestHits.computeIfAbsent(ip) { mutableListOf() }
        synchronized(list) {
            list.removeAll { now - it > windowMs }
            if (list.size >= maxHits) return true
            list.add(now)
            return false
        }
    }

    /** 释放限流计数（服务停止时调用）。 */
    internal fun clear() {
        requestHits.clear()
    }
}
