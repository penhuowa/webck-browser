package com.app.webcookies.ui.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * App 图标的 Compose [ImageVector] 版本 —— 与
 * `artwork/app_icon.svg` 及 `res/drawable/ic_launcher_foreground.xml`
 * 使用完全相同的坐标系（viewport 108 x 108），三份资源可以互相对照修改。
 *
 * 造型：白色地球（外圆 + 赤道 + 经线）叠加一块带咬痕的焦糖饼干（巧克力豆），
 * 底色是深蓝→亮蓝的对角渐变。
 */
private const val ICON_VIEWPORT = 108f

private val BgStart = Color(0xFF2B3A67)
private val BgMid = Color(0xFF1E4E9C)
private val BgEnd = Color(0xFF1565C0)
private val CookieStart = Color(0xFFFFD79A)
private val CookieEnd = Color(0xFFE09A38)
private val ChipBrown = Color(0xFF6B3E1D)

/** 地球三条线共用的白色描边。 */
private const val WHITE = 0xFFFFFFFF

/**
 * 完整图标：渐变圆角底 + 地球 + 饼干。
 * 用于"关于"页面的大图展示。
 */
val WebCookieIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "WebCookieIcon",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT
    ).apply {
        // ---------- 渐变底（圆角 24） ----------
        path(
            fill = Brush.linearGradient(
                colors = listOf(BgStart, BgMid, BgEnd),
                start = Offset(0f, 0f),
                end = Offset(ICON_VIEWPORT, ICON_VIEWPORT)
            )
        ) {
            moveTo(24f, 0f)
            lineTo(84f, 0f)
            arcTo(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 108f, y1 = 24f)
            lineTo(108f, 84f)
            arcTo(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 84f, y1 = 108f)
            lineTo(24f, 108f)
            arcTo(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 0f, y1 = 84f)
            lineTo(0f, 24f)
            arcTo(24f, 24f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 24f, y1 = 0f)
            close()
        }

        // ---------- 地球：外圆 ----------
        path(
            stroke = SolidColor(Color(WHITE)),
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 46f)
            arcTo(21f, 21f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 65f, y1 = 46f)
            arcTo(21f, 21f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 23f, y1 = 46f)
            close()
        }

        // ---------- 地球：赤道 ----------
        path(
            stroke = SolidColor(Color(WHITE)),
            strokeLineWidth = 4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(23f, 46f)
            lineTo(65f, 46f)
        }

        // ---------- 地球：经线椭圆 ----------
        path(
            stroke = SolidColor(Color(WHITE)),
            strokeLineWidth = 4f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(34f, 46f)
            curveTo(34f, 36.6f, 39f, 25f, 44f, 25f)
            curveTo(49f, 25f, 54f, 36.6f, 54f, 46f)
            curveTo(54f, 55.4f, 49f, 67f, 44f, 67f)
            curveTo(39f, 67f, 34f, 55.4f, 34f, 46f)
            close()
        }

        // ---------- 饼干主体：咬痕直接描进轮廓 ----------
        // 不要用「外圆 + 咬痕圆 + EvenOdd」：咬痕圆伸到饼干外的那一段
        // 只被 1 条子路径覆盖（奇数），会被填充成一块多余的凸起。
        // 下面这条路径不自交：先沿缺口圆弧凹进去，再沿饼干圆弧绕 305° 回来。
        path(
            fill = Brush.linearGradient(
                colors = listOf(CookieStart, CookieEnd),
                start = Offset(55f, 57f),
                end = Offset(83f, 85f)
            ),
            stroke = SolidColor(Color(WHITE)),
            strokeLineWidth = 3f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(76f, 58.88f)
            // 缺口：r=9，逆时针凹进饼干
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 82.95f, y1 = 69.78f)
            // 饼干外圈：r=14，顺时针绕 305° 回到起点
            arcTo(14f, 14f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 76f, y1 = 58.88f)
            close()
        }

        // ---------- 巧克力豆 ----------
        chip(63f, 66f)
        chip(74f, 68f)
        chip(62f, 78f)
        chip(73f, 77f)
    }.build()
}

private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.chip(cx: Float, cy: Float) {
    val r = 2.6f
    path(fill = SolidColor(ChipBrown)) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = cx - r, y1 = cy)
        close()
    }
}

/**
 * 仅"地球 + 饼干"的可着色标志（不带底色），用于顶栏 / 列表项。
 * 所有填充都是纯色，因此可以直接交给 [Icon] 按 `LocalContentColor` 着色。
 */
val WebCookieMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "WebCookieMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(23f, 46f)
            arcTo(21f, 21f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 65f, y1 = 46f)
            arcTo(21f, 21f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 23f, y1 = 46f)
            close()

            moveTo(23f, 46f)
            lineTo(65f, 46f)

            moveTo(34f, 46f)
            curveTo(34f, 36.6f, 39f, 25f, 44f, 25f)
            curveTo(49f, 25f, 54f, 36.6f, 54f, 46f)
            curveTo(54f, 55.4f, 49f, 67f, 44f, 67f)
            curveTo(39f, 67f, 34f, 55.4f, 34f, 46f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            // 与 WebCookieIcon 完全相同的轮廓（咬痕已描进路径，无需 EvenOdd）
            moveTo(76f, 58.88f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 82.95f, y1 = 69.78f)
            arcTo(14f, 14f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 76f, y1 = 58.88f)
            close()
        }
    }.build()
}

/** 顶栏用的小标志。 */
@Composable
fun WebCookieMarkIcon(size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Icon(imageVector = WebCookieMark, contentDescription = "WebCookieBrowser", modifier = modifier.size(size))
}

/** 关于页面用的大图标：自带渐变底，圆角裁切。 */
@Composable
fun WebCookieAppLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(Brush.linearGradient(listOf(BgStart, BgMid, BgEnd))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            imageVector = WebCookieMark,
            contentDescription = "WebCookieBrowser",
            modifier = Modifier.size(size * 0.72f)
        )
    }
}
