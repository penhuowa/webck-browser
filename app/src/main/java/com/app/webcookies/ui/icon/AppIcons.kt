package com.app.webcookies.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 项目自绘的小图标集。
 *
 * 为什么不直接用 `Icons.Default.ContentCopy` 这些？
 * 它们位于 `androidx.compose.material:material-icons-extended`，
 * 该库已经被官方弃用/冻结（版本长期停留在 1.7.x，也不再由 Compose BOM 管理），
 * 为一个 App 拖进一个几十 MB、且不再维护的图标库并不划算。
 * `material-icons-core`（随 material3 一起进来）里只有
 * Close / Refresh / Search / Menu / Info / Lock / Home / Add / Delete 等少量图标，
 * 所以这里把缺的 5 个手绘成 [ImageVector]。
 *
 * 所有图形都是 24x24 viewport 的纯描边/纯色，交给 `Icon()` 按
 * `LocalContentColor` 着色即可。
 */
object AppIcons {

    private const val V = 24f
    private val ink = SolidColor(Color.Black)

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = V,
        viewportHeight = V
    )

    /** 复制：两个叠放的圆角方框。 */
    val Copy: ImageVector by lazy {
        builder("AppIcons.Copy").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 前面那张纸
                moveTo(9f, 9f)
                lineTo(18f, 9f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 11f)
                lineTo(20f, 20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 22f)
                lineTo(9f, 22f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7f, y1 = 20f)
                lineTo(7f, 11f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 9f, y1 = 9f)
                close()

                // 后面那张纸（只画露出来的两条边）
                moveTo(5f, 15f)
                lineTo(4f, 15f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 2f, y1 = 13f)
                lineTo(2f, 4f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4f, y1 = 2f)
                lineTo(13f, 2f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15f, y1 = 4f)
                lineTo(15f, 5f)
            }
        }.build()
    }

    /** 下载 / 导出：向下箭头 + 托盘。 */
    val Download: ImageVector by lazy {
        builder("AppIcons.Download").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 托盘
                moveTo(4f, 15f)
                lineTo(4f, 19f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 6f, y1 = 21f)
                lineTo(18f, 21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 19f)
                lineTo(20f, 15f)

                // 箭杆
                moveTo(12f, 3f)
                lineTo(12f, 15f)

                // 箭头
                moveTo(7f, 10f)
                lineTo(12f, 15f)
                lineTo(17f, 10f)
            }
        }.build()
    }

    /** 显示明文：眼睛 + 瞳孔。 */
    val Eye: ImageVector by lazy {
        builder("AppIcons.Eye").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(1.5f, 12f)
                curveTo(4.5f, 6.5f, 8f, 4.5f, 12f, 4.5f)
                curveTo(16f, 4.5f, 19.5f, 6.5f, 22.5f, 12f)
                curveTo(19.5f, 17.5f, 16f, 19.5f, 12f, 19.5f)
                curveTo(8f, 19.5f, 4.5f, 17.5f, 1.5f, 12f)
                close()

                // 瞳孔
                moveTo(9f, 12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 15f, y1 = 12f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 9f, y1 = 12f)
                close()
            }
        }.build()
    }

    /** 隐藏明文：眼睛 + 斜杠。 */
    val EyeOff: ImageVector by lazy {
        builder("AppIcons.EyeOff").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(1.5f, 12f)
                curveTo(4.5f, 6.5f, 8f, 4.5f, 12f, 4.5f)
                curveTo(16f, 4.5f, 19.5f, 6.5f, 22.5f, 12f)
                curveTo(19.5f, 17.5f, 16f, 19.5f, 12f, 19.5f)
                curveTo(8f, 19.5f, 4.5f, 17.5f, 1.5f, 12f)
                close()
            }
            // 斜杠单独一条，避免与眼睛轮廓的 close() 连成奇怪的形状
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 20.5f)
            }
        }.build()
    }

    /** 浏览记录：时钟（圆形 + 指针）。 */
    val History: ImageVector by lazy {
        builder("AppIcons.History").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 21f, y1 = 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 3f, y1 = 12f)
                close()

                // 指针
                moveTo(12f, 6.5f)
                lineTo(12f, 12f)
                lineTo(16.5f, 12f)
            }
            // 逆时针回溯的小箭头，强调“历史”
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 8f)
                lineTo(3f, 3f)
                moveTo(3f, 3f)
                lineTo(8f, 3f)
            }
        }.build()
    }

    /** 端口 / 服务器：三格机架。 */
    val Server: ImageVector by lazy {
        builder("AppIcons.Server").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 4f)
                lineTo(22f, 4f)
                moveTo(2f, 12f)
                lineTo(22f, 12f)
                moveTo(2f, 20f)
                lineTo(22f, 20f)
            }
            path(fill = ink) {
                moveTo(3.2f, 1.5f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 5.8f, y1 = 1.5f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 3.2f, y1 = 1.5f)
                close()
                moveTo(3.2f, 9.5f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 5.8f, y1 = 9.5f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 3.2f, y1 = 9.5f)
                close()
                moveTo(3.2f, 17.5f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 5.8f, y1 = 17.5f)
                arcTo(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 3.2f, y1 = 17.5f)
                close()
            }
        }.build()
    }

    /**
     * 标签页：浏览器窗口的方块造型（外框 + 标签条分隔线 + 一个当前标签）。
     * 就是底部工具栏那个"标签页"按钮用的图标 —— 比起汉堡菜单，
     * 方块窗口一眼就能看出是"标签页"，也符合用户对浏览器的一贯认知。
     */
    val Tab: ImageVector by lazy {
        builder("AppIcons.Tab").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 浏览器窗口外框（圆角矩形）
                moveTo(4.5f, 3.5f)
                lineTo(19.5f, 3.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 22f, y1 = 6f)
                lineTo(22f, 18f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 19.5f, y1 = 20.5f)
                lineTo(4.5f, 20.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 2f, y1 = 18f)
                lineTo(2f, 6f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 4.5f, y1 = 3.5f)
                close()

                // 标签条底部那条横线
                moveTo(2f, 8.5f)
                lineTo(22f, 8.5f)

                // 当前标签的左右两条竖线（顶边就是窗口顶边，所以只画两条侧边）
                moveTo(6.5f, 3.5f)
                lineTo(6.5f, 8.5f)
                moveTo(13f, 3.5f)
                lineTo(13f, 8.5f)
            }
        }.build()
    }

    /**
     * 无痕模式：礼帽 + 圆框眼镜，国际通用的"隐身"符号。
     * 用于无痕页面的角标、标签页列表与菜单项。
     */
    val Incognito: ImageVector by lazy {
        builder("AppIcons.Incognito").apply {
            path(
                stroke = ink,
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 帽檐
                moveTo(3.5f, 8.6f)
                lineTo(20.5f, 8.6f)

                // 帽顶（一段圆弧收在帽檐上）
                moveTo(7.2f, 8.6f)
                curveTo(7.2f, 4.6f, 9.2f, 2.6f, 12f, 2.6f)
                curveTo(14.8f, 2.6f, 16.8f, 4.6f, 16.8f, 8.6f)

                // 左右两个镜片
                moveTo(4.6f, 13.4f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 11f, y1 = 13.4f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 4.6f, y1 = 13.4f)
                close()

                moveTo(13f, 13.4f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 19.4f, y1 = 13.4f)
                arcTo(3.2f, 3.2f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 13f, y1 = 13.4f)
                close()

                // 镜桥
                moveTo(11f, 13.4f)
                lineTo(13f, 13.4f)

                // 两条镜腿
                moveTo(4.6f, 12.4f)
                lineTo(2.2f, 10.8f)
                moveTo(19.4f, 12.4f)
                lineTo(21.8f, 10.8f)
            }
        }.build()
    }
}
