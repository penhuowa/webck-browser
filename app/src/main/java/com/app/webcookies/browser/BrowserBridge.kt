package com.app.webcookies.browser

/**
 * Activity ⇄ 前台服务 之间的极简桥。
 *
 * MCP 服务与 Activity 运行在**同一个进程**里，所以这里直接用单例传值即可，
 * 不需要 Binder / AIDL。服务端读取 Cookie 走的是系统级
 * [android.webkit.CookieManager]，即使 Activity 已经退到后台也能工作；
 * 只有"当前页面 URL"和"document.cookie"这两项需要 Activity 来提供。
 */
object BrowserBridge {

    /** 当前活动标签页的 URL；MCP 工具未显式传 url 时使用。 */
    @Volatile
    var currentUrl: String? = null

    /** 当前活动标签页的标题，方便 Agent 判断页面。 */
    @Volatile
    var currentTitle: String? = null

    /**
     * 由 Activity 注入：在当前页面执行 `document.cookie`，返回原始串。
     *
     * 必须是 suspend：`WebView.evaluateJavascript` 是回调式的，
     * Activity 内部用 `suspendCancellableCoroutine` 包了一层，并且会切到主线程。
     * 页面为空或 WebView 已销毁时返回 null。
     */
    @Volatile
    var documentCookieProvider: (suspend () -> String?)? = null

    /** 注入的脚本：把 document.cookie 原样取出。 */
    const val JS_READ_DOCUMENT_COOKIE: String = "javascript:(function(){return document.cookie;})()"

    fun clear() {
        currentUrl = null
        currentTitle = null
        documentCookieProvider = null
    }
}
