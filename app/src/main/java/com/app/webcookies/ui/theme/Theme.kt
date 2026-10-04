package com.app.webcookies.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Via 风格：低饱和、蓝灰主色调、几乎无阴影。
private val BrandBlue = Color(0xFF1565C0)
private val BrandBlueDark = Color(0xFF90CAF9)
private val CookieTan = Color(0xFFE09A38)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0A2F5C),
    secondary = Color(0xFF4A5B7A),
    onSecondary = Color.White,
    tertiary = CookieTan,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC3C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF0B4A85),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB6C4DE),
    onSecondary = Color(0xFF1E2B41),
    tertiary = Color(0xFFFFD79A),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF171B1F),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF23272C),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/**
 * 无痕模式的配色：整体换成偏紫的灰调。
 *
 * 这是"区分无痕模式"的一部分 —— 除了文案与图标，
 * 整个界面的主色也换掉，用户瞟一眼就知道自己在无痕窗口里。
 * 用 `copy` 从普通配色派生，只覆盖需要变的几项，避免维护两套完整配色。
 */
private val IncognitoLight = LightColors.copy(
    primary = Color(0xFF6A4FA3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF25005A),
    secondary = Color(0xFF625B70),
    background = Color(0xFFF7F5FB),
    surfaceVariant = Color(0xFFEAE4F3),
    onSurfaceVariant = Color(0xFF494154),
    outline = Color(0xFFCAC4D6)
)

private val IncognitoDark = DarkColors.copy(
    primary = Color(0xFFCDBDF5),
    onPrimary = Color(0xFF37265F),
    primaryContainer = Color(0xFF4E3C78),
    onPrimaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF141118),
    surface = Color(0xFF1B1722),
    surfaceVariant = Color(0xFF2A2434),
    onSurfaceVariant = Color(0xFFCBC4D6),
    outline = Color(0xFF948E9F)
)

/** 等宽字体：Cookie 的 Name / Value 用它显示，方便肉眼比对。 */
val MonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun WebCookieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** 无痕窗口走另一套偏紫配色，用于在视觉上区分普通 / 无痕模式。 */
    incognito: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        incognito && darkTheme -> IncognitoDark
        incognito -> IncognitoLight
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 不再设置 window.statusBarColor / navigationBarColor：
            // 这两个属性从 API 35 起已废弃（系统强制 edge-to-edge）。
            // 状态栏/导航栏的透明底色由 themes.xml 里的
            //   <item name="android:statusBarColor">@android:color/transparent</item>
            // 负责，这里只管图标亮暗。
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
