// 根工程：只声明插件版本，不应用。
//
// 版本选择依据（2026-10 核对，并已用 AGP 9.4.1 实际构建验证）：
//   AGP 9.4.1      —— 最新稳定版；core-ktx 1.19 / Compose 1.12 要求 AGP >= 9.1，
//                     且要求 compileSdk 37
//   Kotlin 2.4.20  —— **不需要再声明 org.jetbrains.kotlin.android**：
//                     AGP 9.0 起 Kotlin 支持已内建，再显式应用会直接构建失败
//                     （"The 'org.jetbrains.kotlin.android' plugin is no longer
//                       required for Kotlin support since AGP 9.0"）。
//                     AGP 9.4.1 内建的 Kotlin 就是 2.4.20，
//                     正好 >= MCP Kotlin SDK 0.15.0 编译时用的 stdlib 2.4.0，
//                     能正常读取它的 metadata。
//   在 app 模块里用 `kotlin { compilerOptions { } }` 配置编译选项。
plugins {
    id("com.android.application") version "9.4.1" apply false
    // Kotlin 2.0 起 Compose 编译器随 Kotlin 版本发布，仍需显式应用
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    // CookieUtils / MCP 工具返回 JSON 需要 @Serializable
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
