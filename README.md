# webck浏览器

轻量级 Android 浏览器，基于系统 WebView。核心是 Cookie —— 看得到、复制得出、导得走，也能让 AI Agent 通过 MCP 直接读。

[![Release](https://img.shields.io/github/v/release/penhuowa/webck-browser?label=release)](https://github.com/penhuowa/webck-browser/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

<img src="artwork/app_icon_preview.png" width="140">

## 下载

**[下载最新版 APK](https://github.com/penhuowa/webck-browser/releases/latest)**

debug 签名，自己装着用没问题；要上架应用商店得换正式签名。支持 Android 8.0+（minSdk 26，targetSdk 37）。

## 功能

### 浏览

- 地址栏输网址或关键词，回车即走；不像网址就交给搜索引擎
- 多标签页，底部标签按钮查看和切换
- 双指缩放，网页按屏幕宽度自适应
- 其他 App 里点 http/https 链接可以直接用本应用打开
- 底部工具栏：后退 / 前进 / 主页 / Cookie / 标签页

### Cookie 管理

- 仿 Cookie-Editor 的面板，列出当前网页所有 Cookie：Name、Value、Domain、Path、Secure、HttpOnly
- 单条复制 Value，或一键复制全部
- 导出 JSON、Cookie 请求头、name=value 列表、Netscape cookies.txt 四种格式
- 点眼睛图标隐藏敏感值

> WebView 不对外暴露 Cookie 的属性。Domain / Path 靠多 URL 探测反推，
> Secure / HttpOnly 是推断值，导出的 JSON 里带 `inferred: true`。

### 无痕模式

菜单里选「新建无痕页面」。无痕窗口跑在独立进程，并使用独立的 WebView 数据目录，
Cookie、缓存、LocalStorage 与普通页面完全隔离，关掉窗口自动清除，MCP 也读不到。
需要 Android 9 及以上（低版本入口会置灰）。

### MCP 服务

内置 MCP Server，菜单里可以开关和改端口，跑在前台服务里。注册了一个
`get_current_cookies` 工具，参数 `url`（省略则用当前网页）和 `format`（`json` / `header` / `netscape`）。

支持 SSE 的客户端直接填地址：

```json
{
  "mcpServers": {
    "webck": { "url": "http://192.168.1.5:8080/sse" }
  }
}
```

只支持 stdio 的客户端用 `mcp-remote` 桥接：

```json
{
  "mcpServers": {
    "webck": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://192.168.1.5:8080/sse"]
    }
  }
}
```

> ⚠️ **MCP 没有鉴权。** 局域网里任何人知道地址就能读到你的 Cookie，
> 那通常就是各网站的登录凭证。只在可信网络下开，用完关掉。

## 构建

需要 JDK 17+ 和 Android SDK Platform 37。

```bash
./gradlew :app:assembleDebug
```

Kotlin + Jetpack Compose (Material 3) + Ktor Server + MCP Kotlin SDK，
已用 AGP 9.4.1 / Gradle 9.8.0 实际编译打包通过。

## 权限

`INTERNET`、`ACCESS_NETWORK_STATE`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、
`POST_NOTIFICATIONS`、`WAKE_LOCK`。

## 协议

[MIT](LICENSE)
