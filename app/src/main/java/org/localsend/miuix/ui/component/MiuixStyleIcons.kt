package org.localsend.miuix.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * Miuix 官方图标库（miuix-icons 0.9.4，收录 156 项）未覆盖语义的自绘补充。
 *
 * 几何沿用官方线性图标的度量：24dp 网格、1.7dp 描边、圆头端点与圆角连接，
 * 与 [top.yukonga.miuix.kmp.icon.MiuixIcons] 中的图标并排时粗细与转角一致。
 * 描边和填充统一使用黑色占位，实际颜色由 Icon 的 tint（ColorFilter.SrcIn）覆盖。
 */
object MiuixStyleIcons {
    private const val STROKE_WIDTH = 1.7f

    private fun icon(
        name: String,
        strokePath: PathBuilder.() -> Unit,
        fillPath: (PathBuilder.() -> Unit)? = null,
    ): ImageVector {
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        builder.addPath(
            pathData = PathBuilder().apply(strokePath).nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        if (fillPath != null) {
            builder.addPath(
                pathData = PathBuilder().apply(fillPath).nodes,
                fill = SolidColor(Color.Black),
            )
        }
        return builder.build()
    }

    /** Android 设备与 APK 安装包 */
    val Android: ImageVector =
        icon(
            name = "MiuixStyleIcons.Android",
            strokePath = {
                moveTo(7f, 8.3f)
                lineTo(5f, 4.5f)
                moveTo(17f, 8.3f)
                lineTo(19f, 4.5f)
                moveTo(3.6f, 11.4f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
                horizontalLineToRelative(10.8f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
                verticalLineToRelative(4.8f)
                arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
                horizontalLineTo(6.6f)
                arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
                close()
            },
            fillPath = {
                moveTo(7.1f, 14.9f)
                arcToRelative(1.3f, 1.3f, 0f, false, true, 2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, false, true, -2.6f, 0f)
                close()
                moveTo(14.3f, 14.9f)
                arcToRelative(1.3f, 1.3f, 0f, false, true, 2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, false, true, -2.6f, 0f)
                close()
            },
        )

    /** 压缩包归档 */
    val Archive: ImageVector =
        icon(
            name = "MiuixStyleIcons.Archive",
            strokePath = {
                moveTo(3.2f, 6.6f)
                horizontalLineTo(20.8f)
                moveTo(5.2f, 6.6f)
                verticalLineTo(17.6f)
                arcToRelative(3f, 3f, 0f, false, false, 3f, 3f)
                horizontalLineToRelative(7.6f)
                arcToRelative(3f, 3f, 0f, false, false, 3f, -3f)
                verticalLineTo(6.6f)
                moveTo(11f, 9.7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3.8f)
                horizontalLineToRelative(-2f)
                close()
            },
        )

    /** 视频文件 */
    val Movie: ImageVector =
        icon(
            name = "MiuixStyleIcons.Movie",
            strokePath = {
                moveTo(3.6f, 7.6f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, -3f)
                horizontalLineToRelative(10.8f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
                verticalLineToRelative(8.8f)
                arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
                horizontalLineTo(6.6f)
                arcToRelative(3f, 3f, 0f, false, true, -3f, -3f)
                close()
            },
            fillPath = {
                moveTo(10.2f, 8.9f)
                lineTo(15.8f, 12f)
                lineTo(10.2f, 15.1f)
                close()
            },
        )

    /** 网页端与语言地球 */
    val Language: ImageVector =
        icon(
            name = "MiuixStyleIcons.Language",
            strokePath = {
                moveTo(3.5f, 12f)
                arcToRelative(8.5f, 8.5f, 0f, false, true, 17f, 0f)
                arcToRelative(8.5f, 8.5f, 0f, false, true, -17f, 0f)
                close()
                moveTo(3.6f, 12f)
                horizontalLineTo(20.4f)
                moveTo(12f, 3.5f)
                arcToRelative(4.3f, 8.5f, 0f, false, false, 0f, 17f)
                arcToRelative(4.3f, 8.5f, 0f, false, false, 0f, -17f)
                close()
            },
        )

    /** 平板设备 */
    val Tablet: ImageVector =
        icon(
            name = "MiuixStyleIcons.Tablet",
            strokePath = {
                moveTo(7f, 2.6f)
                arcToRelative(3f, 3f, 0f, false, false, -3f, 3f)
                verticalLineToRelative(12.8f)
                arcToRelative(3f, 3f, 0f, false, false, 3f, 3f)
                horizontalLineToRelative(10f)
                arcToRelative(3f, 3f, 0f, false, false, 3f, -3f)
                verticalLineTo(5.6f)
                arcToRelative(3f, 3f, 0f, false, false, -3f, -3f)
                close()
            },
            fillPath = {
                moveTo(10.9f, 16.8f)
                arcToRelative(1.1f, 1.1f, 0f, false, true, 2.2f, 0f)
                arcToRelative(1.1f, 1.1f, 0f, false, true, -2.2f, 0f)
                close()
            },
        )

    /** 桌面设备 */
    val Computer: ImageVector =
        icon(
            name = "MiuixStyleIcons.Computer",
            strokePath = {
                moveTo(2.6f, 5.2f)
                arcToRelative(2.6f, 2.6f, 0f, false, true, 2.6f, -2.6f)
                horizontalLineToRelative(13.6f)
                arcToRelative(2.6f, 2.6f, 0f, false, true, 2.6f, 2.6f)
                verticalLineToRelative(8.2f)
                arcToRelative(2.6f, 2.6f, 0f, false, true, -2.6f, 2.6f)
                horizontalLineTo(5.2f)
                arcToRelative(2.6f, 2.6f, 0f, false, true, -2.6f, -2.6f)
                close()
                moveTo(12f, 13.4f)
                verticalLineTo(16.8f)
                moveTo(7.8f, 20.4f)
                horizontalLineTo(16.2f)
            },
        )

    /** 服务器与无界面设备 */
    val Storage: ImageVector =
        icon(
            name = "MiuixStyleIcons.Storage",
            strokePath = {
                moveTo(4.2f, 5.6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineToRelative(11.6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineToRelative(1.8f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(6.2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
                moveTo(4.2f, 16.6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineToRelative(11.6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineToRelative(1.8f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(6.2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
            },
            fillPath = {
                moveTo(6.35f, 6.1f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, 2.1f, 0f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, -2.1f, 0f)
                close()
                moveTo(6.35f, 17.1f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, 2.1f, 0f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, -2.1f, 0f)
                close()
            },
        )
}
