package org.localsend.miuix.model

/**
 * 界面皮肤：HyperOS 与 ColorOS 两套视图实现。
 *
 * 两套皮肤共用同一份业务状态、路由与设置持久化，只在 [org.localsend.miuix.ui.App] 根部按本枚举分派，
 * 因此切换皮肤等价于替换一层视图实现，不影响设备发现、传输与历史记录。
 */
enum class UiSkin(
    val label: String,
) {
    /** Miuix（HyperOS 风格），当前默认皮肤。 */
    HYPEROS("HyperOS"),

    /** coUI-Kit（ColorOS 风格）。 */
    COLOROS("ColorOS"),
    ;

    companion object {
        /** 读取持久化的皮肤索引；越界或旧版本缺键时回落到 [HYPEROS]。 */
        fun fromIndex(index: Int): UiSkin = entries.getOrElse(index) { HYPEROS }
    }
}
