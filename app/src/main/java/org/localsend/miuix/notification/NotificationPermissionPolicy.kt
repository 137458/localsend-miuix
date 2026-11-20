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

    /** 系统通知设置的落点。 */
    enum class SettingsTarget {
        /** Android 16.1+ 的「允许提升型通知」权限页，只用于开启实时通知。 */
        PROMOTION,

        /** 应用通知设置页，可查看/修改总开关、横幅、声音、震动与各渠道设置。 */
        APP,
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

    /**
     * 决定点击入口后进入哪个系统页面：只有系统确实提供提升型通知权限页、
     * 且实时通知尚未开启时，才直达该页；其余情况进入应用通知设置页，
     * 因为横幅、声音、震动都在那里调整。
     */
    fun settingsTarget(
        promotionAvailable: Boolean,
        liveUpdatesEnabled: Boolean,
    ): SettingsTarget = if (promotionAvailable && !liveUpdatesEnabled) SettingsTarget.PROMOTION else SettingsTarget.APP
}
