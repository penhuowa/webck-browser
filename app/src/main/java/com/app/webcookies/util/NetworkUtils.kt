package com.app.webcookies.util

import java.net.Inet4Address
import java.net.NetworkInterface

/** 局域网地址工具：MCP 面板要展示 `http://192.168.x.x:port/sse`。 */
object NetworkUtils {

    /**
     * 返回本机所有可用的 IPv4 局域网地址。
     *
     * 排除回环地址；优先返回 192.168 / 10. / 172.16-31 这类私有网段，
     * 因为 MCP 客户端一般是在同一局域网的另一台设备上。
     */
    fun lanIpv4Addresses(): List<String> {
        val result = mutableListOf<String>()
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return result

        for (nif in interfaces) {
            val ok = runCatching { nif.isUp && !nif.isLoopback }.getOrDefault(false)
            if (!ok) continue
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("169.254.")) continue // link-local，通常不可用
                    if (!result.contains(host)) result.add(host)
                }
            }
        }

        return result.sortedByDescending { isPrivate(it) }
    }

    private fun isPrivate(ip: String): Boolean =
        ip.startsWith("192.168.") ||
            ip.startsWith("10.") ||
            Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(ip)
}
