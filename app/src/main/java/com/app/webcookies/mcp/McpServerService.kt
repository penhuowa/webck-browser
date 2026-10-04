package com.app.webcookies.mcp

import com.app.webcookies.BuildConfig

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.app.webcookies.MainActivity
import com.app.webcookies.R
import com.app.webcookies.browser.BrowserBridge
import com.app.webcookies.util.CookieUtils
import com.app.webcookies.util.NetworkUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.net.ServerSocket

/**
 * MCP Server 前台服务。
 *
 * 结构：Ktor(CIO) 内嵌服务器 + MCP Kotlin SDK
 * ```
 *   GET  /sse                      -> SSE 长连接（MCP 传输通道）
 *   POST /sse?sessionId=<uuid>     -> 客户端上行 JSON-RPC
 *   GET  /health                   -> 免鉴权健康检查
 * ```
 * v1.01 起**没有任何鉴权**：只要知道地址，同一局域网内任何人都能读取本机 Cookie。
 *
 * 依赖版本对齐（与 MCP SDK 0.15.0 的 POM 保持一致，避免版本漂移）：
 *   MCP SDK 0.15.0 / Ktor 3.5.1 / kotlinx-serialization 1.11.0
 */
class McpServerService : Service() {

    /** 服务生命周期内的业务协程。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 专门的收尾协程作用域。
     *
     * 它**不**随 [serviceScope] 一起取消：`onDestroy()` 里必须把 Ktor 引擎
     * 停干净（否则端口会被一直占用），而 Service 销毁时我们无法在主线程上
     * 阻塞等待，所以把收尾工作丢到这个独立作用域里跑完。
     */
    private val shutdownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var engine: EmbeddedServer<*, *>? = null

    @Volatile
    private var mcpServer: Server? = null

    private lateinit var prefs: McpPrefs

    override fun onCreate() {
        super.onCreate()
        prefs = McpPrefs(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    shutdownServer()
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            ACTION_START, ACTION_RESTART, null -> {
                val port = intent?.getIntExtra(EXTRA_PORT, prefs.port) ?: prefs.port
                startAsForeground(port)
                serviceScope.launch { launchServer(port) }
                return START_STICKY
            }

            else -> return START_NOT_STICKY
        }
    }

    /**
     * API 34+：`shortService` 类型的前台服务超时回调。
     * 覆盖它（而不是调用 super）就必须自己把服务停干净。
     */
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "前台服务超时（startId=$startId），主动停止")
        handleForegroundTimeout()
    }

    /**
     * API 35+：`dataSync` / `mediaProcessing` 类型有运行时长上限
     * （Android 15 起 dataSync 在 24 小时窗口内累计约 6 小时）。
     * 超时后系统会回调这里；不处理的话服务会被强杀，端口可能在下次启动时冲突。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "前台服务超时（startId=$startId, fgsType=$fgsType），主动停止并释放端口")
        handleForegroundTimeout()
    }

    private fun handleForegroundTimeout() {
        McpServiceState.setError("系统前台服务时长已达上限，MCP 服务已被停止；重新打开该页面即可再次启动")
        serviceScope.launch {
            shutdownServer()
            withContext(Dispatchers.Main) { stopSelf() }
        }
    }

    override fun onDestroy() {
        val oldEngine = engine
        val oldServer = mcpServer
        engine = null
        mcpServer = null

        // 关键：用独立作用域保证「关 Server -> 停 Ktor 引擎」一定执行完，
        // stop(1000, 2000) 会等待 1s 优雅期 + 最多 2s 强制结束，然后释放端口。
        shutdownScope.launch {
            runCatching { oldServer?.close() }
                .onFailure { Log.w(TAG, "关闭 MCP Server 失败", it) }
            runCatching { oldEngine?.stop(1000, 2000) }
                .onFailure { Log.w(TAG, "停止 Ktor 引擎失败", it) }
            Log.i(TAG, "MCP 服务已停止，端口已释放")
            McpServiceState.setStopped()
        }

        serviceScope.cancel()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // 启动 / 停止
    // ------------------------------------------------------------------

    private suspend fun launchServer(port: Int) {
        shutdownServer() // 幂等：先确保没有残留实例

        // 端口被占用时给出可读的提示，而不是抛一个 BindException 堆栈
        if (!isPortAvailable(port)) {
            val msg = "端口 $port 已被占用，请换一个端口"
            Log.e(TAG, msg)
            McpServiceState.setError(msg)
            notifyStatus("启动失败：$msg")
            stopSelf()
            return
        }

        try {
            val mcpInstance = buildMcpServer()
            mcpServer = mcpInstance

            val newEngine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
                install(CORS) {
                    anyHost()
                    allowNonSimpleContentTypes = true
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Delete)
                    allowMethod(HttpMethod.Options)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(HttpHeaders.Accept)
                    allowHeader("Mcp-Session-Id")
                    allowHeader("Mcp-Protocol-Version")
                    allowHeader("Last-Event-ID")
                    exposeHeader("Mcp-Session-Id")
                }

                // SSE 插件必须先装，Route.mcp 才能注册路由
                install(SSE)

                // ---- v1.01 起不再有 Token 鉴权 ----
                // 这里原先有一段 intercept(ApplicationCallPipeline.Plugins) 做 Token 校验，
                // 已按需求整体移除：SSE 端点现在对外开放，局域网内任何人拿到地址即可读取 Cookie。
                // 面板与 README 里都写明了这一点。
                //
                // （如果以后想加回鉴权，注意 Ktor 3 的 PipelineContext 只有 `context`
                //   没有 `call`，且 httpMethod 需要 import io.ktor.server.request.httpMethod。
                //   另一个坑：SDK 广播的 POST 地址是只有 query 的相对引用 `?sessionId=`，
                //   会把基 URL 的 query 覆盖掉，所以 POST 那一步拿不到 query 里的 token。）

                routing {
                    // SSE 端点：GET /sse 建流，POST /sse?sessionId=xxx 上行消息
                    route(SSE_PATH) {
                        mcp(
                            // 默认的 DNS-rebinding 保护只放行 localhost，
                            // 会导致局域网里的 Agent 连不上，这里必须关掉。
                            enableDnsRebindingProtection = false
                        ) {
                            // 同一个 Server 实例服务所有会话（SDK 内部用 sessionId 区分）
                            mcpInstance
                        }
                    }

                    // 健康检查
                    route(HEALTH_PATH) {
                        get {
                            call.respondText(
                                text = """{"status":"ok","service":"webck","version":"${BuildConfig.VERSION_NAME}","sse":"$SSE_PATH"}""",
                                contentType = ContentType.Application.Json
                            )
                        }
                    }
                }
            }

            newEngine.start(wait = false)
            engine = newEngine

            val endpoints = NetworkUtils.lanIpv4Addresses().map { "http://$it:$port$SSE_PATH" }
            McpServiceState.setRunning(port = port, endpoints = endpoints)
            notifyStatus(endpoints.firstOrNull() ?: "0.0.0.0:$port$SSE_PATH")

            Log.i(TAG, "MCP 服务已启动，监听端口 $port，端点 $endpoints")
        } catch (t: Throwable) {
            val msg = "启动 MCP 服务失败：${t.message ?: t::class.java.simpleName}"
            Log.e(TAG, msg, t)
            McpServiceState.setError(msg)
            notifyStatus("启动失败：${t.message ?: "未知错误"}")
            stopSelf()
        }
    }

    /** 关闭当前实例并释放端口。可重复调用。 */
    private suspend fun shutdownServer() {
        val oldServer = mcpServer
        val oldEngine = engine
        mcpServer = null
        engine = null

        runCatching { oldServer?.close() }
            .onFailure { Log.w(TAG, "关闭 MCP Server 失败", it) }

        // 用户明确要求：必须用 stop(1000, 2000) 彻底释放端口，防止端口占用报错。
        runCatching { oldEngine?.stop(1000, 2000) }
            .onFailure { Log.w(TAG, "停止 Ktor 引擎失败", it) }

        McpServiceState.setStopped()
    }

    private fun isPortAvailable(port: Int): Boolean = runCatching {
        ServerSocket(port).use { true }
    }.getOrElse { false }

    // ------------------------------------------------------------------
    // MCP Server 定义
    // ------------------------------------------------------------------

    private fun buildMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = "webck-browser", version = BuildConfig.VERSION_NAME),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            ),
            instructions = """
                WebCookieBrowser 的 Cookie 读取服务。
                使用 get_current_cookies 工具读取本机 WebView 中指定网址（或当前正在浏览的页面）
                的 Cookie。返回值是 JSON，包含 name / value / domain / path 等字段。
                """.trimIndent()
        )

        server.addTool(
            name = "get_current_cookies",
            description = """
                读取 WebCookieBrowser 中当前网页（或指定 URL）的 Cookie。
                返回 JSON：{ url, host, count, timestamp, cookies: [{ name, value, domain, path,
                expires, secure, httpOnly, sameSite, hostOnly, inferred }] }。
                注意 domain / path / httpOnly 是根据 Cookie 可见性推断的（WebView 不公开真实属性），
                对应字段 inferred=true。
            """.trimIndent(),
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put(
                        "url",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "目标网址，如 https://example.com/。省略时使用浏览器当前正在浏览的页面。"
                            )
                        }
                    )
                    put(
                        "format",
                        buildJsonObject {
                            put("type", "string")
                            put(
                                "description",
                                "返回格式：json（默认，完整字段）、header（Cookie 请求头字符串）、" +
                                    "netscape（cookies.txt 文本）。"
                            )
                            put(
                                "enum",
                                buildJsonArray {
                                    add(JsonPrimitive("json"))
                                    add(JsonPrimitive("header"))
                                    add(JsonPrimitive("netscape"))
                                }
                            )
                        }
                    )
                }
                // url 与 format 都是可选的，因此不声明 required
            )
        ) { request ->
            val args = request.arguments
            val requestedUrl = args?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
            val format = args?.get("format")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "json"

            val targetUrl = requestedUrl?.takeIf { it.isNotBlank() }
                ?: BrowserBridge.currentUrl?.takeIf { it.isNotBlank() }

            if (targetUrl.isNullOrBlank()) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "当前没有打开任何网页，也没有提供 url 参数。请在浏览器里打开一个页面，" +
                                "或在调用时传入 url。"
                        )
                    ),
                    isError = true
                )
            }

            try {
                // 补充 JS 可见性：Activity 侧会切到主线程执行 document.cookie
                val jsCookie = runCatching { BrowserBridge.documentCookieProvider?.invoke() }.getOrNull()
                val cookies = CookieUtils.readCookies(targetUrl, jsCookie)

                val payload = when (format.lowercase()) {
                    "header" -> CookieUtils.toRequestHeader(cookies)
                    "netscape" -> CookieUtils.toNetscape(cookies)
                    else -> CookieUtils.toSnapshotJson(targetUrl, cookies)
                }
                Log.i(TAG, "get_current_cookies -> $targetUrl, ${cookies.size} 条")
                CallToolResult(content = listOf(TextContent(payload)))
            } catch (t: Throwable) {
                Log.e(TAG, "读取 Cookie 失败", t)
                CallToolResult(
                    content = listOf(TextContent("读取 Cookie 失败：${t.message}")),
                    isError = true
                )
            }
        }

        return server
    }

    // ------------------------------------------------------------------
    // 前台通知
    // ------------------------------------------------------------------

    private fun startAsForeground(port: Int) {
        val notification = buildNotification(
            title = getString(R.string.notif_title),
            text = "端口 $port · 正在启动…"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 必须与 Manifest 中的 android:foregroundServiceType="dataSync" 一致，
            // 否则 Android 14+ 会抛 SecurityException / MissingForegroundServiceTypeException。
            //
            // 关于这里的 API 等级：三参数的 startForeground(int, Notification, int)
            // 是 **API 29 (Android 10)** 加入的，不是 34。
            // 依据：Android SDK platform 37 的 data/api-versions.xml 中
            //   <method name="startForeground(ILandroid/app/Notification;I)V" since="29"/>
            // 所以用 >= Q 判断是正确的，请不要"修正"成 UPSIDE_DOWN_CAKE。
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyStatus(text: String) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(
                NOTIFICATION_ID,
                buildNotification(title = getString(R.string.notif_title), text = text)
            )
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_cookie)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------

    companion object {
        private const val TAG = "McpServerService"

        const val ACTION_START = "com.app.webcookies.action.MCP_START"
        const val ACTION_STOP = "com.app.webcookies.action.MCP_STOP"
        const val ACTION_RESTART = "com.app.webcookies.action.MCP_RESTART"
        const val EXTRA_PORT = "extra_mcp_port"

        /** MCP 的 SSE 路径：最终对外地址形如 http://192.168.1.5:8080/sse */
        const val SSE_PATH = "/sse"
        const val HEALTH_PATH = "/health"

        private const val CHANNEL_ID = "mcp_server_channel"
        private const val NOTIFICATION_ID = 1001

        /** 启动（或重启）前台服务。 */
        fun start(context: Context, port: Int) {
            val intent = Intent(context, McpServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 应用新端口：先停再起。 */
        fun restart(context: Context, port: Int) {
            val intent = Intent(context, McpServerService::class.java).apply {
                action = ACTION_RESTART
                putExtra(EXTRA_PORT, port)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 优雅停止：交给服务自己关 Server + 停引擎 + 释放端口。 */
        fun stop(context: Context) {
            val intent = Intent(context, McpServerService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
                .onFailure { context.stopService(Intent(context, McpServerService::class.java)) }
        }
    }
}
