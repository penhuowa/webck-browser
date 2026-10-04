package com.app.webcookies

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.app.webcookies.ui.theme.WebCookieTheme

/**
 * 无痕窗口。
 *
 * 真正的隔离靠两件事配合，缺一不可：
 *
 *  1. Manifest 里给这个 Activity 声明了 `android:process=":incognito"` —— 它跑在**独立进程**；
 *  2. 进程启动时（**创建任何 WebView 之前**）调用
 *     `WebView.setDataDirectorySuffix("incognito")`，让这个进程改用**独立的 WebView 数据目录**：
 *     Cookie、HTTP 缓存、LocalStorage、IndexedDB 全部与普通页面分开。
 *
 * 只做其中一件都不够：
 *  · 只分进程不分数据目录 —— 两个进程会写同一份 Cookie 数据库，既隔离不了也有损坏风险；
 *  · 只分数据目录不分进程 —— `setDataDirectorySuffix` 是进程级的，同进程内改不了。
 *
 * 一个额外的好处：MCP 服务跑在主进程，读的是主进程的 Cookie 库，
 * 所以**它天然看不到无痕页面的任何数据** —— 无痕页面的 Cookie 不会被 Agent 读走。
 *
 * 局限：`setDataDirectorySuffix` 需要 **API 28（Android 9）**。
 * 在 26/27 上入口会置灰（见 [isSupported]），因为那种情况下两个进程会共用同一份
 * Cookie 库，并发写入可能损坏数据 —— 宁可少一个功能，也不要冒这个风险。
 */
class IncognitoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在任何 WebView 实例被创建之前调用，否则会抛 IllegalStateException。
        // 放在 super.onCreate() 之前，保证不会有人抢在前面初始化 WebView。
        if (isSupported) {
            runCatching { WebView.setDataDirectorySuffix(DATA_DIR_SUFFIX) }
                .onFailure {
                    Log.w(TAG, "setDataDirectorySuffix 失败，本次无痕隔离会退化", it)
                }
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val startUrl = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

        setContent {
            // incognito = true 会切到偏紫的配色，配合顶部的无痕提示条做视觉区分
            WebCookieTheme(incognito = true) {
                BrowserScreen(incomingUrl = startUrl, incognito = true)
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            // 关掉无痕窗口 = 抹掉这个进程数据目录里的全部痕迹。
            // 因为数据目录是独立的，这里只会清掉无痕自己的数据，碰不到普通页面的 Cookie。
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }.onFailure { Log.w(TAG, "清除无痕 Cookie 失败", it) }

            runCatching { WebStorage.getInstance().deleteAllData() }
                .onFailure { Log.w(TAG, "清除无痕站点数据失败", it) }

            Log.i(TAG, "无痕窗口已关闭，本地数据已清除")
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "IncognitoActivity"

        /** 独立 WebView 数据目录的名字，只在 :incognito 进程里用。 */
        const val DATA_DIR_SUFFIX = "incognito"

        /** `WebView.setDataDirectorySuffix` 需要 API 28；低版本菜单项会置灰。 */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}
