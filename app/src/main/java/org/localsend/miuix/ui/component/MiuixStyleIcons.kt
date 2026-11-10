package org.localsend.miuix.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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
    private const val FILLED_VIEWPORT = 35f
    private const val ARCHIVE_VIEWPORT = 64f

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

    /** 容纳整幅填充型矢量，供设计方直接提供的图形使用。 */
    private fun filledIcon(
        name: String,
        viewport: Float = FILLED_VIEWPORT,
        pathFillType: PathFillType = PathFillType.NonZero,
        outlineWidth: Float = 0f,
        path: PathBuilder.() -> Unit,
    ): ImageVector {
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = viewport,
                viewportHeight = viewport,
            )
        builder.addPath(
            pathData = PathBuilder().apply(path).nodes,
            pathFillType = pathFillType,
            fill = SolidColor(Color.Black),
            stroke = if (outlineWidth > 0f) SolidColor(Color.Black) else null,
            strokeLineWidth = outlineWidth,
            strokeLineJoin = StrokeJoin.Round,
        )
        return builder.build()
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

    /** 安卓应用：圆顶机身、双天线与双眼，取自设计方提供的 35 单位网格矢量并等比缩至 0.95。 */
    val Android: ImageVector =
        filledIcon(name = "MiuixStyleIcons.Android") {
            moveTo(30.81f, 27.49f)
            horizontalLineToRelative(-26.6f)
            arcToRelative(3.012f, 3.012f, 0f, false, true, -2.242f, -1.035f)
            arcTo(3.448f, 3.448f, 0f, false, true, 1.141f, 23.785f)
            curveTo(2.281f, 14.912f, 9.311f, 8.224f, 17.5f, 8.224f)
            curveTo(25.689f, 8.224f, 32.7f, 14.874f, 33.859f, 23.785f)
            arcToRelative(3.448f, 3.448f, 0f, false, true, -0.807f, 2.698f)
            arcTo(3.012f, 3.012f, 0f, false, true, 30.81f, 27.49f)
            close()
            moveToRelative(-13.3f, -16.919f)
            curveToRelative(-7.001f, 0f, -13.015f, 5.805f, -14.003f, 13.49f)
            arcToRelative(1.064f, 1.064f, 0f, false, false, 0.238f, 0.827f)
            arcToRelative(0.608f, 0.608f, 0f, false, false, 0.456f, 0.228f)
            horizontalLineToRelative(26.6f)
            arcToRelative(0.608f, 0.608f, 0f, false, false, 0.456f, -0.228f)
            arcToRelative(1.064f, 1.064f, 0f, false, false, 0.238f, -0.826f)
            curveTo(30.515f, 16.403f, 24.502f, 10.599f, 17.5f, 10.599f)
            close()
            moveTo(26.886f, 12.993f)
            arcToRelative(1.112f, 1.112f, 0f, false, true, -0.57f, -0.152f)
            arcToRelative(1.178f, 1.178f, 0f, false, true, -0.475f, -1.605f)
            lineToRelative(1.682f, -3.106f)
            arcToRelative(1.188f, 1.188f, 0f, true, true, 2.09f, 1.14f)
            lineToRelative(-1.681f, 3.097f)
            arcTo(1.197f, 1.197f, 0f, false, true, 26.886f, 12.993f)
            close()
            moveTo(8.171f, 12.993f)
            arcToRelative(1.197f, 1.197f, 0f, false, true, -1.045f, -0.627f)
            lineTo(5.445f, 9.269f)
            arcTo(1.188f, 1.188f, 0f, true, true, 7.525f, 8.129f)
            lineToRelative(1.682f, 3.107f)
            arcToRelative(1.188f, 1.188f, 0f, false, true, -0.475f, 1.606f)
            arcTo(1.14f, 1.14f, 0f, false, true, 8.171f, 12.993f)
            close()
            moveTo(11.125f, 20.555f)
            arcToRelative(1.786f, 1.786f, 0f, false, false, 0f, -3.562f)
            arcToRelative(1.786f, 1.786f, 0f, false, false, 0f, 3.563f)
            close()
            moveTo(23.723f, 20.631f)
            arcToRelative(1.786f, 1.786f, 0f, false, false, 0f, -3.562f)
            arcToRelative(1.786f, 1.786f, 0f, false, false, 0f, 3.563f)
            close()
        }

    /** 压缩包归档：文件柜造型，取自设计方提供的 64 单位网格填充矢量并等比缩至 0.86。 */
    val Archive: ImageVector =
        filledIcon(
            name = "MiuixStyleIcons.Archive",
            viewport = ARCHIVE_VIEWPORT,
            pathFillType = PathFillType.EvenOdd,
            outlineWidth = 1.09f,
        ) {
            moveTo(29.42f, 13.94f)
            lineTo(34.58f, 13.94f)
            curveTo(35.53f, 13.94f, 36.3f, 13.17f, 36.3f, 12.22f)
            curveTo(36.3f, 11.27f, 35.53f, 10.5f, 34.58f, 10.5f)
            lineTo(29.42f, 10.5f)
            curveTo(28.47f, 10.5f, 27.7f, 11.27f, 27.7f, 12.22f)
            curveTo(27.7f, 13.17f, 28.47f, 13.94f, 29.42f, 13.94f)
            close()
            moveTo(29.42f, 38.02f)
            lineTo(34.58f, 38.02f)
            curveTo(35.53f, 38.02f, 36.3f, 37.25f, 36.3f, 36.3f)
            curveTo(36.3f, 35.35f, 35.53f, 34.58f, 34.58f, 34.58f)
            lineTo(29.42f, 34.58f)
            curveTo(28.47f, 34.58f, 27.7f, 35.35f, 27.7f, 36.3f)
            curveTo(27.7f, 37.25f, 28.47f, 38.02f, 29.42f, 38.02f)
            close()
            moveTo(23.4f, 56.94f)
            lineTo(40.6f, 56.94f)
            curveTo(40.6f, 57.89f, 41.37f, 58.66f, 42.32f, 58.66f)
            lineTo(47.48f, 58.66f)
            curveTo(48.43f, 58.66f, 49.2f, 57.89f, 49.2f, 56.94f)
            lineTo(49.21f, 56.94f)
            curveTo(50.16f, 56.93f, 50.92f, 56.17f, 50.92f, 55.22f)
            lineTo(50.92f, 7.06f)
            curveTo(50.92f, 6.11f, 50.15f, 5.34f, 49.2f, 5.34f)
            lineTo(14.8f, 5.34f)
            curveTo(13.85f, 5.34f, 13.08f, 6.11f, 13.08f, 7.06f)
            lineTo(13.08f, 55.22f)
            curveTo(13.08f, 56.17f, 13.84f, 56.93f, 14.79f, 56.94f)
            lineTo(14.8f, 56.94f)
            curveTo(14.8f, 57.89f, 15.57f, 58.66f, 16.52f, 58.66f)
            lineTo(21.68f, 58.66f)
            curveTo(22.63f, 58.66f, 23.4f, 57.89f, 23.4f, 56.94f)
            close()
            moveTo(47.48f, 32.86f)
            lineTo(47.48f, 53.5f)
            lineTo(16.52f, 53.5f)
            lineTo(16.52f, 32.86f)
            close()
            moveTo(47.48f, 29.42f)
            lineTo(16.52f, 29.42f)
            lineTo(16.52f, 8.78f)
            lineTo(47.48f, 8.78f)
            close()
        }

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
