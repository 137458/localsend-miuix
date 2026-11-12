package org.localsend.miuix.ui.coui.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import com.itos.coui.dialog.CouiDialogActionColors
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.dialog.CouiDialogMaterial
import com.itos.coui.dialog.CouiDialogPlacement
import com.itos.coui.theme.CouiTheme

/**
 * 标记窗口根是否已挂入 `ProvideCouiMenuBackdrop`：库的 `LocalCouiMenuBackdrop` 为 `internal`，
 * 由根宿主在 `ProvideCouiMenuBackdrop` 作用域内将本标记置为 `true`，供弹窗安全判定玻璃材质。
 */
internal val LocalCouiWindowBackdropAvailable = staticCompositionLocalOf { false }

/**
 * 大屏居中、手机贴底：按 600dp 宽度阈值决定 coUI 弹窗停靠位置（与项目平板判定口径一致）。
 */
@Composable
internal fun rememberCouiDialogPlacement(): CouiDialogPlacement =
    if (LocalConfiguration.current.screenWidthDp >= 600) {
        CouiDialogPlacement.Center
    } else {
        CouiDialogPlacement.Bottom
    }

/**
 * 弹窗玻璃材质：仅在 API 33+ 且窗口根已挂入 `ProvideCouiMenuBackdrop` 时启用，
 * 避免违反 `CouiDialogWindow.kt` 对 `LocalCouiMenuBackdrop` 非空的前置检查导致崩溃。
 */
@Composable
internal fun rememberCouiDialogMaterial(): CouiDialogMaterial? {
    val dark = CouiTheme.isDark
    val hasBackdrop = LocalCouiWindowBackdropAvailable.current
    return if (CouiDialogDefaults.isMaterialSupported() && hasBackdrop) {
        CouiDialogDefaults.material(dark)
    } else {
        null
    }
}

/**
 * 统一主操作与破坏性主操作配色，供全皮肤弹窗共用。
 */
@Composable
internal fun couiPrimaryActionColors(danger: Boolean = false): CouiDialogActionColors {
    val accent =
        if (danger) {
            CouiDialogDefaults.colors(CouiTheme.isDark).destructive
        } else {
            CouiTheme.colorScheme.accent
        }
    return CouiDialogDefaults.primaryActionColors(accent)
}
