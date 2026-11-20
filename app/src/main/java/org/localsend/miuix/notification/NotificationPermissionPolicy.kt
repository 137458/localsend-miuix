package org.localsend.miuix.notification

/**
 * 通知权限申请策略：把「什么时候自动弹系统授权、什么时候直达系统设置」收敛成纯函数，
 * 便于脱离 Android 运行时做单元测试。
 */
object NotificationPermissionPolicy {
    private const val TIRAMISU = 33

    /** 设置页入口点击后的动作。 */
    enum class EntryAction {
        /** 再弹一次系统授权弹窗。 */
        REQUEST,

        /** 直达系统通知设置。 */
        OPEN_SETTINGS,
    }

    /**
     * 冷启动是否自动申请 POST_NOTIFICATIONS：只在从未询问过时询问一次。
     * 之后交给设置页入口，避免每次冷启动都弹窗（用户拒绝后系统会直接静默返回）。
     */
    fun shouldAskOnLaunch(
        sdkInt: Int,
        granted: Boolean,
        askRecorded: Boolean,
    ): Boolean = sdkInt >= TIRAMISU && !granted && !askRecorded

    /**
     * 设置页「通知与实时通知」入口点击后的动作：
     * 系统仍允许询问（从未问过或上次只是普通拒绝）就再弹一次，否则直达系统设置。
     */
    fun entryAction(
        sdkInt: Int,
        granted: Boolean,
        askRecorded: Boolean,
        showRationale: Boolean,
    ): EntryAction =
        when {
            sdkInt < TIRAMISU || granted -> EntryAction.OPEN_SETTINGS
            !askRecorded || showRationale -> EntryAction.REQUEST
            else -> EntryAction.OPEN_SETTINGS
        }
}
