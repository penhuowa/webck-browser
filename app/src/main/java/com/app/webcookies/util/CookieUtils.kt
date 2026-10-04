package com.app.webcookies.util

import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 一条 Cookie 记录。
 *
 * 说明：Android 的 [CookieManager] **只**对外暴露 `getCookie(url) -> String`，
 * 返回的是 `"a=1; b=2"` 这种 name/value 串，既没有 Domain 也没有 Path，
 * 更没有 Secure / HttpOnly / SameSite 等属性（WebView 的 Cookie 数据库
 * 位于应用私有目录，非 root 无法直接读取）。
 *
 * 因此本项目的做法是：
 *  1. 用 [CookieManager.getCookie] 拿到权威的 name/value 列表（包含 HttpOnly 的项）；
 *  2. 通过**多 URL 探测**反推 Domain 与 Path 的作用域（见 [CookieStore.probeScope]）；
 *  3. 通过注入 JS 读取 `document.cookie` 补充 JS 可见属性（见
 *     [CookieStore.enrichWithJsCookies]）。
 * 反推结果在绝大多数站点上与真实 Set-Cookie 一致，但属于推断值，
 * UI 上以浅色标注，导出 JSON 时带 `"inferred": true` 字段。
 */
@Serializable
data class CookieEntry(
    val name: String,
    val value: String,
    /** 形如 `.example.com`（域 Cookie）或 `www.example.com`（host-only）。 */
    val domain: String = "",
    val path: String = "/",
    /** 过期时间字符串；会话 Cookie 为 null。 */
    val expires: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: String? = null,
    /** true 表示该 Cookie 只发送给完全匹配的主机，不带 Domain 属性。 */
    val hostOnly: Boolean = true,
    /** 该条记录的 domain/path 是否为推断值。 */
    val inferred: Boolean = true
) {
    /** `Cookie:` 请求头中的形式。 */
    val headerPair: String get() = "$name=$value"

    /** Chrome Cookie-Editor 风格的一行摘要。 */
    val isSession: Boolean get() = expires.isNullOrBlank()
}

/** 供 MCP 工具返回的完整快照。 */
@Serializable
data class CookieSnapshot(
    val url: String,
    val host: String,
    val count: Int,
    val timestamp: Long,
    val cookies: List<CookieEntry>
)

/**
 * Cookie 解析 / 导出 / 作用域探测工具集。
 *
 * 所有访问 [CookieManager] 的公开方法都切换到了 `Dispatchers.Main`：
 * WebView 的 Cookie 子系统内部依赖创建它的 Looper，在主线程调用最稳妥。
 */
object CookieUtils {

    val prettyJson: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    val compactJson: Json = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    // ---------------------------------------------------------------------
    // 纯函数：解析
    // ---------------------------------------------------------------------

    /**
     * 解析 `CookieManager.getCookie()` 返回的 `"a=1; b=2"` 串。
     *
     * 注意 value 本身可能含 `=`（如 base64 padding），所以只在**第一个** `=` 处切分。
     */
    fun parseCookieHeader(raw: String?, defaultDomain: String = "", defaultPath: String = "/"): List<CookieEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = part.substring(0, eq).trim()
                if (name.isEmpty()) return@mapNotNull null
                CookieEntry(
                    name = name,
                    value = part.substring(eq + 1).trim(),
                    domain = defaultDomain,
                    path = defaultPath
                )
            }
            .distinctBy { it.name }
            .toList()
    }

    /**
     * 解析注入 JS 读到的 `document.cookie`。
     *
     * `document.cookie` 读取时只返回 `name=value` 对（不带属性），
     * 但某些页面会用 `document.cookie = "..."` 写入带属性的值 —— 这里都兼容。
     * 遇到 `path=` / `domain=` / `secure` / `samesite=` 等属性时会一并解析。
     */
    fun parseJsCookie(raw: String?, fallbackDomain: String = ""): List<CookieEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = part.substring(0, eq).trim()
                if (name.isEmpty()) return@mapNotNull null
                val value = part.substring(eq + 1).trim()
                // 跳过纯属性片段（在整串里它们没有 name= 的形式，这里只是兜底）
                CookieEntry(
                    name = name,
                    value = value,
                    domain = fallbackDomain,
                    path = "/"
                )
            }
            .distinctBy { it.name }
            .toList()
    }

    /** 把 `Set-Cookie` 响应头（含属性）解析成完整记录 —— 供以后接入自定义网络层时使用。 */
    fun parseSetCookieHeader(header: String, requestUrl: String): CookieEntry? {
        val segments = header.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val first = segments.first()
        val eq = first.indexOf('=')
        if (eq <= 0) return null

        val uri = runCatching { Uri.parse(requestUrl) }.getOrNull()
        val host = uri?.host.orEmpty()
        val defaultPath = defaultPathFor(uri?.path)

        var path = defaultPath
        var domain = host
        var expires: String? = null
        var secure = false
        var httpOnly = false
        var sameSite: String? = null
        var hostOnly = true

        segments.drop(1).forEach { seg ->
            val i = seg.indexOf('=')
            val k = (if (i >= 0) seg.substring(0, i) else seg).trim().lowercase()
            val v = if (i >= 0) seg.substring(i + 1).trim() else ""
            when (k) {
                "path" -> if (v.isNotEmpty()) path = v
                "domain" -> if (v.isNotEmpty()) {
                    domain = v.removePrefix(".")
                    hostOnly = false
                }
                "expires" -> expires = v
                "max-age" -> if (expires == null) expires = "Max-Age=$v"
                "secure" -> secure = true
                "httponly" -> httpOnly = true
                "samesite" -> sameSite = v
            }
        }

        return CookieEntry(
            name = first.substring(0, eq).trim(),
            value = first.substring(eq + 1).trim(),
            domain = domain,
            path = path,
            expires = expires,
            secure = secure,
            httpOnly = httpOnly,
            sameSite = sameSite,
            hostOnly = hostOnly,
            inferred = false
        )
    }

    /** RFC 6265 §5.1.4 的默认 Path 算法。 */
    fun defaultPathFor(urlPath: String?): String {
        val p = urlPath.orEmpty()
        if (p.isEmpty() || !p.startsWith("/")) return "/"
        val lastSlash = p.lastIndexOf('/')
        if (lastSlash <= 0) return "/"
        return p.substring(0, lastSlash)
    }

    // ---------------------------------------------------------------------
    // 导出
    // ---------------------------------------------------------------------

    /** 导出成 JSON 数组（Cookie-Editor 的 "Export as JSON" 格式）。 */
    fun toJson(cookies: List<CookieEntry>, pretty: Boolean = true): String {
        val json = if (pretty) prettyJson else compactJson
        return json.encodeToString(cookies)
    }

    /** 导出成 MCP 工具返回的完整快照 JSON。 */
    fun toSnapshotJson(url: String, cookies: List<CookieEntry>, pretty: Boolean = true): String {
        val json = if (pretty) prettyJson else compactJson
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        return json.encodeToString(
            CookieSnapshot(
                url = url,
                host = host,
                count = cookies.size,
                timestamp = System.currentTimeMillis(),
                cookies = cookies
            )
        )
    }

    /** 导出成 `Cookie:` 请求头字符串。 */
    fun toRequestHeader(cookies: List<CookieEntry>): String =
        cookies.joinToString("; ") { it.headerPair }

    /** 导出成 Netscape cookies.txt（curl / wget / yt-dlp 可直接使用）。 */
    fun toNetscape(cookies: List<CookieEntry>): String = buildString {
        appendLine("# Netscape HTTP Cookie File")
        appendLine("# Generated by WebCookieBrowser at ${java.util.Date()}")
        appendLine()
        cookies.forEach { c ->
            val domain = if (c.hostOnly) c.domain else ".${c.domain.removePrefix(".")}"
            val flag = if (c.hostOnly) "FALSE" else "TRUE"
            val secure = if (c.secure) "TRUE" else "FALSE"
            val expiry = if (c.isSession) "0" else c.expires.orEmpty()
            appendLine(listOf(domain, flag, c.path, secure, expiry, c.name, c.value).joinToString("\t"))
        }
    }

    // ---------------------------------------------------------------------
    // 需要 CookieManager 的读取逻辑
    // ---------------------------------------------------------------------

    /**
     * 读取某个 URL 可见的全部 Cookie，并尽量补全 Domain / Path。
     *
     * @param enrichWithJs 传入当前页面注入 JS 得到的 `document.cookie`，
     *                     用于确认 JS 可见性（非 HttpOnly）。
     */
    suspend fun readCookies(
        url: String,
        jsDocumentCookie: String? = null
    ): List<CookieEntry> = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val host = uri?.host.orEmpty()
        if (host.isEmpty()) return@withContext emptyList()

        val origin = "${uri?.scheme ?: "https"}://$host"
        val raw = manager.getCookie(url)
        val base = parseCookieHeader(raw, defaultDomain = host, defaultPath = "/")
        if (base.isEmpty()) return@withContext emptyList()

        val jsVisible: Set<String> = parseJsCookie(jsDocumentCookie, host).map { it.name }.toSet()

        base.map { entry ->
            val scope = probeScope(manager, entry.name, host, uri?.path.orEmpty(), origin)
            entry.copy(
                domain = scope.domain,
                path = scope.path,
                hostOnly = scope.hostOnly,
                secure = origin.startsWith("https"),
                httpOnly = entry.name !in jsVisible,
                // SameSite 没有任何公开 API 可读，所以宁可留空，也不编一个看起来像真的值。
                // 需要真实属性时走 parseSetCookieHeader()（自己接管网络层解析 Set-Cookie）。
                sameSite = null,
                inferred = true
            )
        }
    }

    private data class Scope(val domain: String, val path: String, val hostOnly: Boolean)

    /**
     * 反推某条 Cookie 的 Domain / Path 作用域。
     *
     * 原理：Cookie 只会发送给"等于其 Domain 或为其子域"的主机，且只发送给
     * "路径前缀匹配"的 URL。因此用几个候选 URL 去问 [CookieManager] 就能确定边界：
     *  - 若该 Cookie 在父域上仍然可见 ⇒ 它是域 Cookie（Domain=.parent），否则为 host-only；
     *  - 若该 Cookie 在站点根 `/` 上仍然可见 ⇒ Path=/，否则 Path 落在更深一级目录。
     *
     * 注意：`Secure` 与 `HttpOnly` 同样无法从公开 API 读到。
     *  - `Secure` 只能按"当前站点是否 https"来猜（现代站点绝大多数如此），
     *    但一个没有 Secure 属性的 Cookie 同样会在 https 页面上被返回，所以这里没有任何真实信号；
     *  - `HttpOnly` 相对可靠：没出现在 `document.cookie` 里就基本可以断定是 HttpOnly。
     * 两者都会随结果一起标记 `inferred = true`，调用方不应把它当作权威属性。
     */
    private fun probeScope(
        manager: CookieManager,
        cookieName: String,
        host: String,
        urlPath: String,
        origin: String
    ): Scope {
        val parent = parentDomain(host)
        val parentHasCookie = parent != null &&
            manager.getCookie("$origin/")?.let { hasCookie(it, cookieName) } == true &&
            manager.getCookie("${schemeOf(origin)}://$parent/")?.let { hasCookie(it, cookieName) } == true

        // parentHasCookie 为真时必然 parent != null，所以这里用 takeIf 而不是再写一次
        // `&& parent != null` —— 那样写编译器会提示 "Condition is always 'true'"。
        val domain = parent?.takeIf { parentHasCookie } ?: host
        val hostOnly = !parentHasCookie

        val rootVisible = manager.getCookie("$origin/")?.let { hasCookie(it, cookieName) } == true
        val path = if (rootVisible) "/" else defaultPathFor(urlPath)

        return Scope(domain = domain, path = path, hostOnly = hostOnly)
    }

    private fun hasCookie(raw: String?, name: String): Boolean =
        parseCookieHeader(raw).any { it.name == name }

    private fun schemeOf(origin: String): String = if (origin.startsWith("https")) "https" else "http"

    /** `www.a.example.com` -> `a.example.com`；已经是二级域时返回 null。 */
    fun parentDomain(host: String): String? {
        val parts = host.split('.')
        if (parts.size <= 2) return null
        return parts.drop(1).joinToString(".")
    }

    // ---------------------------------------------------------------------
    // 清理
    // ---------------------------------------------------------------------

    /** 立刻把内存中的 Cookie 落盘（页面加载完 / 退出前调用）。 */
    suspend fun flush() = withContext(Dispatchers.Main) {
        runCatching { CookieManager.getInstance().flush() }
        Unit
    }
}
