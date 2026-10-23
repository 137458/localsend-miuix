package org.localsend.miuix.util

/** 自动收尾判定：传输完成后会话是否应立即从会话列表收起。 */
object AutoFinishPolicy {
    /** 仅当传输已完成且用户开启 autoFinish 时立即收起；失败/取消会话保留原有结果展示期。 */
    fun shouldAutoDismiss(
        finished: Boolean,
        autoFinish: Boolean,
    ): Boolean = finished && autoFinish
}
