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

    /** 以左上角、宽高与圆角半径追加一个圆角矩形，与 SVG 的 rect rx/ry 等价。 */
    private fun PathBuilder.roundedRect(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
    ) {
        moveTo(left + radius, top)
        horizontalLineTo(left + width - radius)
        arcToRelative(radius, radius, 0f, false, true, radius, radius)
        verticalLineTo(top + height - radius)
        arcToRelative(radius, radius, 0f, false, true, -radius, radius)
        horizontalLineTo(left + radius)
        arcToRelative(radius, radius, 0f, false, true, -radius, -radius)
        verticalLineTo(top + radius)
        arcToRelative(radius, radius, 0f, false, true, radius, -radius)
        close()
    }

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

    /** 压缩包归档：带盖盒子与中央竖向拉链 */
    val Archive: ImageVector =
        icon(
            name = "MiuixStyleIcons.Archive",
            strokePath = {
                moveTo(4.6f, 5.4f)
                horizontalLineTo(19.4f)
                moveTo(4.6f, 5.4f)
                verticalLineTo(18.4f)
                arcToRelative(2.6f, 2.6f, 0f, false, false, 2.6f, 2.6f)
                horizontalLineToRelative(9.6f)
                arcToRelative(2.6f, 2.6f, 0f, false, false, 2.6f, -2.6f)
                verticalLineTo(5.4f)
                moveTo(4.6f, 8.6f)
                horizontalLineTo(19.4f)
                moveTo(12f, 8.6f)
                verticalLineTo(18.2f)
                moveTo(10.2f, 11f)
                horizontalLineTo(13.8f)
                moveTo(10.2f, 14f)
                horizontalLineTo(13.8f)
                moveTo(10.2f, 17f)
                horizontalLineTo(13.8f)
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

    /** 平板设备：竖屏机身与底部指示条 */
    val Tablet: ImageVector =
        icon(
            name = "MiuixStyleIcons.Tablet",
            strokePath = {
                roundedRect(5.4f, 2.6f, 13.2f, 18.8f, 2.8f)
                moveTo(9.9f, 18.3f)
                horizontalLineTo(14.1f)
            },
        )

    /** 桌面设备：宽屏显示器与底座 */
    val Computer: ImageVector =
        icon(
            name = "MiuixStyleIcons.Computer",
            strokePath = {
                roundedRect(2.6f, 3.4f, 18.8f, 11.6f, 2.2f)
                moveTo(12f, 15f)
                verticalLineTo(18.2f)
                moveTo(6.6f, 20.6f)
                horizontalLineTo(17.4f)
            },
        )

    /** 服务器与无界面设备 */
    val Storage: ImageVector =
        icon(
            name = "MiuixStyleIcons.Storage",
            strokePath = {
                roundedRect(4.2f, 3.6f, 15.6f, 5.8f, 2f)
                roundedRect(4.2f, 14.6f, 15.6f, 5.8f, 2f)
            },
            fillPath = {
                moveTo(6.35f, 6.5f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, 2.1f, 0f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, -2.1f, 0f)
                close()
                moveTo(6.35f, 17.5f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, 2.1f, 0f)
                arcToRelative(1.05f, 1.05f, 0f, false, true, -2.1f, 0f)
                close()
            },
        )
}
