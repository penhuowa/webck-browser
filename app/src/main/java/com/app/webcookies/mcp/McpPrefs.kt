package com.app.webcookies.mcp

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * SharedPreferences 持久化的 MCP 配置（端口 / 开关）。
 *
 * 这里**没有**安全 Token —— v1.01 起按需求去掉了鉴权。
 * 也就是说同一局域网内任何人只要知道地址就能读取本机 Cookie。
 * 面板里对此有明确提示，详见 README 的「安全提醒」。
 */
class McpPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户期望的开关状态。服务真实运行状态见 [McpServiceState]。 */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 监听端口；读写时都做范围钳制，避免脏数据导致 bind 失败。 */
    var port: Int
        get() = sp.getInt(KEY_PORT, DEFAULT_PORT).coerceIn(MIN_PORT, MAX_PORT)
        set(value) = sp.edit().putInt(KEY_PORT, value.coerceIn(MIN_PORT, MAX_PORT)).apply()

    /** 校验端口是否合法（1024 - 65535）。 */
    fun isValidPort(value: Int): Boolean = value in MIN_PORT..MAX_PORT

    companion object {
        const val PREFS_NAME = "webcookie_mcp"
        const val KEY_ENABLED = "mcp_enabled"
        const val KEY_PORT = "mcp_port"

        const val DEFAULT_PORT = 8080
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535

        // SSE 路径的唯一来源是 McpServerService.SSE_PATH（路由就在那里注册）。
        // 这里刻意不再重复定义一份，避免两个常量各自漂移。

        /** 端口合法性校验，供 UI 静态调用。 */
        fun portRangeError(value: Int?): String? = when {
            value == null -> "请输入端口号"
            value < MIN_PORT || value > MAX_PORT -> "端口需在 $MIN_PORT - $MAX_PORT 之间"
            else -> null
        }
    }
}

/**
 * MCP 服务的运行时状态。
 *
 * 单例 + StateFlow：前台服务写入，Compose UI 直接 collect，
 * 这样即使 Activity 重建、从最近任务回来，状态也不会丢。
 */
object McpServiceState {

    data class Status(
        val running: Boolean = false,
        val port: Int = McpPrefs.DEFAULT_PORT,
        /** 形如 `http://192.168.1.5:8080/sse`，可能有多张网卡所以是列表。 */
        val endpoints: List<String> = emptyList(),
        val error: String? = null
    ) {
        val primaryEndpoint: String? get() = endpoints.firstOrNull()
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    val current: Status get() = _status.value

    fun setRunning(port: Int, endpoints: List<String>) {
        _status.update {
            it.copy(
                running = true,
                port = port,
                endpoints = endpoints,
                error = null
            )
        }
    }

    fun setStopped(reason: String? = null) {
        _status.update {
            it.copy(
                running = false,
                endpoints = emptyList(),
                error = reason
            )
        }
    }

    fun setError(message: String) {
        _status.update { it.copy(running = false, endpoints = emptyList(), error = message) }
    }
}
