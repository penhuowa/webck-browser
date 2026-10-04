# webck浏览器

![版本](https://img.shields.io/badge/version-1.01-blue)

一个基于系统 WebView 的轻量级浏览器，支持 Cookie 的查看 / 提取 / 复制，
内置**无痕模式**，并提供 **MCP (Model Context Protocol) Server** 让外部 AI Agent
通过 SSE 读取本机 Cookie。

- 包名：`com.app.webcookies`
- 版本：**v1.01**（versionCode 2）
- 语言：Kotlin + Jetpack Compose (Material 3)
- 架构：Compose 单界面；普通窗口在主进程，**无痕窗口跑在独立进程 `:incognito`**，MCP Server 跑在 `ForegroundService` 里
- 作者：等雨停 · <https://jdtool.cyou>

### v1.01 相对 v1.0 的变化

| 变更 | 说明 |
| --- | --- |
| 应用名 | `WebCookie浏览器` → **`webck浏览器`** |
| 新增 | **无痕页面**（独立进程 + 独立 WebView 数据目录，真正隔离 Cookie） |
| 新增 | 顶部无痕提示条 + 无痕专属紫色配色，一眼区分普通 / 无痕模式 |
| 改动 | 标签页按钮换成**浏览器窗口方块图标**，并显示标签页数量 |
| 移除 | MCP 的**安全 Token 鉴权**（见 §3.4 的安全提醒） |

---

## 1. 文件结构

```
webck-browser/
├── settings.gradle.kts
├── build.gradle.kts                     # 根工程：插件版本
├── gradlew / gradlew.bat                # 已包含，可直接 ./gradlew assembleDebug
├── gradle/wrapper/gradle-wrapper.{jar,properties}
├── gradle.properties
├── .gitignore
├── dist/
│   └── WebCookieBrowser-debug.apk       # 已编译好的 debug 包，可直接安装
├── artwork/
│   ├── app_icon.svg                     # ① App 图标矢量源文件（512px 渲染）
│   ├── app_icon_preview.png             #    渲染预览
│   └── render_icon.py                   #    图标几何自检脚本
└── app/
    ├── build.gradle.kts                 # ② Module 级依赖（Ktor + MCP SDK）
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml          # ③ 权限 / 前台服务 / 无痕独立进程
        ├── java/com/app/webcookies/
        │   ├── MainActivity.kt          # ④ 浏览器界面、地址栏、菜单、关于页、MCP 面板
        │   ├── IncognitoActivity.kt     #    无痕窗口：独立进程 + 独立 WebView 数据目录
        │   ├── browser/BrowserBridge.kt #    Activity ⇄ Service 的极简桥
        │   ├── mcp/
        │   │   ├── McpServerService.kt  # ⑤ Ktor + MCP Server 前台服务
        │   │   └── McpPrefs.kt          #    端口/开关 持久化 + 运行时状态 StateFlow
        │   ├── ui/
        │   │   ├── CookieEditorSheet.kt # ⑥ 仿 Cookie-Editor 的 Compose UI
        │   │   ├── icon/AppIcon.kt      # ⑦ 图标的 Compose ImageVector 版本
        │   │   ├── icon/AppIcons.kt     #    手绘小图标（复制/下载/眼睛/标签页/隐身…）
        │   │   └── theme/Theme.kt       #    普通 + 无痕 两套配色
        │   └── util/
        │       ├── CookieUtils.kt       # ⑧ Cookie 解析 / 作用域反推 / JSON 导出
        │       └── NetworkUtils.kt      #    局域网 IPv4 枚举
        └── res/
            ├── drawable/ic_launcher_background.xml    # 自适应图标背景层
            ├── drawable/ic_launcher_foreground.xml    # 自适应图标前景层（地球+饼干）
            ├── drawable/ic_launcher_monochrome.xml    # Android 13 主题图标（单色剪影）
            ├── drawable/ic_stat_cookie.xml            # 通知小图标
            ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml   # API 26-32：background+foreground
            ├── mipmap-anydpi-v33/ic_launcher{,_round}.xml   # API 33+ ：多一层 monochrome
            ├── values/{strings,themes}.xml
            ├── values-night/themes.xml
            └── xml/{backup_rules,data_extraction_rules}.xml
```

对应你要求的 8 项输出：①②③ 构建与清单、④ `MainActivity.kt`、⑤ `McpServerService.kt`、
⑥ `CookieEditorSheet.kt`、⑦ `CookieUtils.kt`、⑧ 图标（SVG + VectorDrawable + Compose ImageVector 三份等价实现）。

---

## 2. 构建环境

本项目的依赖版本是按 **2026-10** 的实际发布版本对齐的，不是随手写的：

| 组件 | 版本 | 为什么是这个版本 |
| --- | --- | --- |
| Gradle | 9.8.0 | AGP 9.4.x 要求 Gradle ≥ 9.6 |
| AGP | 9.4.1 | 最新稳定版 |
| Kotlin | 2.4.20 | **MCP SDK 0.15.0 用 stdlib 2.4.0 编译**，编译器必须 ≥ 2.4 才能读它的 metadata |
| compileSdk / targetSdk | 37 | `core-ktx 1.19` 与 Compose 1.12 都要求 compileSdk 37 |
| minSdk | 26 | Ktor CIO 引擎用到 `AsynchronousChannelGroup`（API 26+）；自适应图标从 26 起原生支持 |
| Compose BOM | 2026.09.00 | 统一 Compose 版本 |
| Ktor | 3.5.1 | **与 MCP SDK 0.15.0 的 POM 完全一致**，避免版本漂移 |
| MCP SDK | `io.modelcontextprotocol:kotlin-sdk-server:0.15.0` | 最新稳定版 |
| Java target | 17 | MCP SDK 字节码目标是 Java 11，17 ≥ 11 即可 |

**环境要求**：JDK 17 或更高（AGP 9 要求），Android SDK **Platform 37**。

### 关于 Gradle Wrapper

`gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` **已经包含在本工程里**，
由 Gradle 9.8.0 自己的 `wrapper` 任务生成，可以直接用：

```bash
./gradlew :app:assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug       # 直接装到已连接的设备
```

（首次运行 `gradlew` 会去 `services.gradle.org` 下载 Gradle 9.8.0 发行版，需要联网。
本机已装 Gradle 的话也可以直接 `gradle :app:assembleDebug`。）

---

## 构建验证状态：**已实际编译并打包成功**

本工程用 **Android Studio 自带的 JBR (JDK 25.0.3) + Gradle 9.8.0 + AGP 9.4.1 + compileSdk 37**
真实跑通了 `:app:assembleDebug`：

```
BUILD SUCCESSFUL
app/build/outputs/apk/debug/app-debug.apk   15,332,796 bytes
0 errors, 0 warnings
```

APK 用 `aapt2 dump badging` 核对过：

```
package: name='com.app.webcookies' versionCode='1' versionName='1.0.0'
compileSdkVersion='37'  minSdkVersion:'26'  targetSdkVersion:'37'
uses-permission: INTERNET / ACCESS_NETWORK_STATE / FOREGROUND_SERVICE
                 FOREGROUND_SERVICE_DATA_SYNC / POST_NOTIFICATIONS / WAKE_LOCK
application-label:'WebCookie浏览器'
application: icon='res/mipmap-anydpi-v33/ic_launcher.xml'
launchable-activity: name='com.app.webcookies.MainActivity'
```

### 编译真实抓出来的 4 个 bug

这些靠读文档是发现不了的，只有编译器会告诉你：

| # | 错误 | 真相与修法 |
| --- | --- | --- |
| 1 | `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0` | **AGP 9.0 起 Kotlin 支持已内建**，再显式应用该插件直接构建失败。已从根工程与 app 模块移除；`kotlin { compilerOptions { } }` 仍然可用。顺带确认 AGP 9.4.1 内建的 Kotlin 就是 **2.4.20**，正好满足 MCP SDK 对 stdlib ≥ 2.4.0 的要求 |
| 2 | `Unresolved reference 'call'`（7 处） | Ktor 3 的 `PipelineContext` **只有 `getContext()`，没有 `getCall()`**（javap 验证过 3.5.1 的字节码）。管道拦截里必须写 `context`，`call` 已被移除 |
| 3 | `Unresolved reference 'httpMethod' on receiver of type 'PipelineRequest'` | Ktor 3.5 的 `ApplicationRequest` 接口本身没有 `httpMethod`，它是 `io.ktor.server.request` 包里的扩展（`ApplicationRequestPropertiesKt.getHttpMethod`），**必须显式 import** |
| 4 | `Unresolved reference 'setInitialScale'` | `WebSettings.setInitialScale` 在 **API 37 已被移除**。而它本来是默认值 0，删掉不影响行为 —— 真正决定"按屏宽自适应 + 可缩放"的是 `useWideViewPort` 与 `loadWithOverviewMode` |

另外还顺手清掉了 6 条编译警告：`CookieUtils` 里两处恒真判断、`WebViewDatabase.clearFormData()`
与 `WebSettings.setDatabaseEnabled` 的废弃调用、`window.statusBarColor/navigationBarColor`
（API 35 起废弃，改由 themes.xml 负责）。

### 编译之外，还用源码与元数据核对过的事实

| 核对对象 | 依据 | 结论 |
| --- | --- | --- |
| `Server(...)` / `addTool(...)` / `ToolSchema` / `CallToolResult` | `kotlin-sdk-server-jvm-0.15.0-sources.jar` 的 `Server.kt` | 签名完全一致 |
| `Route.mcp(...)`、SSE 端点与 POST 端点 | 同上 `KtorServer.kt`（第 93-122、423-440 行） | 一致；`Route.mcp` 只要求 `install(SSE)`，**不需要** ContentNegotiation |
| SSE 广播的 endpoint 事件格式 | 同上 `SSEServerTransport.kt`（第 65 行） | `"?sessionId=<uuid>"` —— 由此发现并修掉了 Token 校验的一个致命问题（见 §3.4） |
| `EmbeddedServer.stop(...)` | `ktor-server-core-jvm-3.5.1-sources.jar` 的 `EmbeddedServer.kt` | `stop(gracePeriodMillis: Long, timeoutMillis: Long)`，**非 suspend** |
| `startForeground(int, Notification, int)` 的 API 等级 | SDK `platforms/android-37.0/data/api-versions.xml` | **`since="29"`**（不是 34），所以 `>= Q` 的判断是对的 |
| `stopForeground(int)` / `onTimeout` | 同上 | `stopForeground(I)` since 24；`onTimeout(I)` since 34、`onTimeout(II)` since 35 |
| `material-icons-core` 是否由 BOM 管理 | `compose-bom-2026.09.00.pom` | **是**，锁定 1.7.8，所以依赖可以不写版本号 |
| MCP SDK 在 Android 上的变体解析 | 实际构建 | 根坐标 `io.modelcontextprotocol:kotlin-sdk-server:0.15.0` **直接可用**，不需要 `-jvm` 后缀 |
| 图标的路径几何 | `artwork/render_icon.py` 真实采样 SVG 后栅格化 | 包围盒 23..83 / 25..85，全部落在安全区内；咬痕填充判定正确 |

### 仍未验证的部分

- **release 构建 + R8 混淆**：`isMinifyEnabled` 目前是 `false`。
  `proguard-rules.pro` 里的 keep 规则是按 Ktor / kotlinx.serialization / MCP SDK 的需要写的，
  但**没有实际跑过 `assembleRelease`**，开启混淆前请自行确认。
- **运行时行为**：编译通过不等于跑起来没问题。WebView 的 Cookie 反推、MCP 客户端握手、
  前台服务在不同 ROM 上的表现都需要真机验证。
- **`build-tools`**：构建用的是 **36.0.0**（AGP 9.4.1 接受它），不需要额外装 37.x。
- **无痕模式需要 Android 9+**：`WebView.setDataDirectorySuffix` 是 API 28 才引入的。
  `minSdk` 仍然是 26，所以 Android 8.0/8.1 上无痕入口置灰（原因见 §3.5）。

---

## 3. 功能与实现要点

### 3.1 浏览器与 WebView 适配

`MainActivity.kt` 里的 `BrowserController.configure()` 做了两件容易被忽略的事：

```kotlin
// 网页缩放
setSupportZoom(true)
builtInZoomControls = true
displayZoomControls = false

// 手机屏幕适配 —— 这两项缺一不可
useWideViewPort = true        // 允许页面声明 viewport，内容按屏宽布局
loadWithOverviewMode = true   // 首次加载按屏幕宽度缩放
```

「页面大小不能调整」这个经典问题，根因就是 `useWideViewPort` / `loadWithOverviewMode`
没开、或者被某个固定 `initial-scale` 卡死。这两项一起设置后，双指缩放与页面自适应都正常。

> 老代码里常见的那句 `setInitialScale(0)`（表示"不做额外固定缩放"）**已经删掉了**：
> `WebSettings.setInitialScale` 在 API 37 已被移除，写了会直接编译失败；
> 而且 0 本来就是默认值，删掉不改变行为。

另外开启：`javaScriptEnabled`、`domStorageEnabled`、
`CookieManager.setAcceptThirdPartyCookies(webView, true)`；
`onPageFinished` 里执行 `CookieManager.getInstance().flush()` 把 Cookie 落盘。

（`WebSettings.setDatabaseEnabled` 没有开 —— 它已废弃，WebSQL 早已从 Chromium 移除，
现代站点用 `domStorageEnabled` 即可。）

多标签页：所有 WebView 挂在同一个 `FrameLayout` 容器里，切换标签只是替换子 View，
每个页面的滚动位置 / 表单 / history 都完整保留。底部工具栏第二个按钮就是标签页列表。

### 3.2 右上角菜单

**普通窗口**（6 项）：

| # | 菜单项 | 实现 |
| --- | --- | --- |
| 1 | 新建页面 | `controller.newTab(HOME_URL)` |
| 2 | **新建无痕页面** | 拉起独立进程的 `IncognitoActivity`（见 §3.5） |
| 3 | 清除浏览记录 | 仅 `webView.clearHistory()`（前进/后退栈） |
| 4 | 将浏览器记录数据全部删除 | 二次确认后 `removeAllCookies()` + `clearCache(true)` + `clearFormData()` + `WebStorage.deleteAllData()` |
| 5 | MCP 服务 | 打开设置面板（开关 / 端口 / 运行地址） |
| 6 | 关于 | 简洁居中弹窗 |

**无痕窗口**（4 项）：新建无痕页面 / 清除浏览记录 / 将浏览器记录数据全部删除 / 关于。
无痕窗口里**不显示 MCP 入口** —— MCP 只读主进程（普通页面）的 Cookie，
在无痕窗口里给出入口容易让人误以为它也能读无痕数据。

Cookie 管理是**独立入口**（底部工具栏的 `Cookie` 按钮），不占用菜单项。
底部工具栏那个标签页按钮用的是**浏览器窗口方块图标**（`AppIcons.Tab`），旁边显示标签页数量。

### 3.3 Cookie 的真实性与局限性

Android 的 `CookieManager` **只暴露 `getCookie(url) -> String`**，返回的是 `"a=1; b=2"`，
既没有 Domain / Path，也没有 Secure / HttpOnly / SameSite —— WebView 的 Cookie 数据库
在应用私有目录，非 root 读不了。

所以 `CookieUtils` 的做法是：

1. **权威取值**：`CookieManager.getCookie(url)`，包含 HttpOnly 的那些；
2. **反推作用域**（`probeScope`）：用「父域 / 站点根」等候选 URL 去问 `CookieManager`，
   如果某条 Cookie 在父域上仍可见 → 它是域 Cookie（`Domain=.parent`），否则是 host-only；
   如果它在 `/` 上仍可见 → `Path=/`，否则落在更深一级目录；
3. **补充 JS 可见性**：注入 `document.cookie`，没出现在里面的就标记 `httpOnly = true`。

反推结果在绝大多数站点上与真实 `Set-Cookie` 一致，但**属于推断值**：
`Domain` / `Path` / `Secure` / `HttpOnly` 里，`HttpOnly` 比较可靠，
`Secure` 只是按「当前站点是否 https」猜的（一个没有 `Secure` 属性的 Cookie 在 https 页面上
同样会被返回，所以这里其实没有真实信号）。UI 上会浅色标注「Domain/Path 为推断值」，
导出 JSON 时带 `"inferred": true`。

**`SameSite` 一律留空** —— 它没有任何公开 API 可读，与其编一个看起来像真的值，
不如不写。如果你需要 100% 精确的属性，正确做法是在 `WebViewClient` 里接管网络层
自己解析 `Set-Cookie` 响应头 —— `CookieUtils.parseSetCookieHeader()`
已经为此准备好了（它会解析 `Path` / `Domain` / `Expires` / `Secure` / `HttpOnly` / `SameSite`）。

导出的格式：JSON（Cookie-Editor 同款）、`Cookie:` 请求头、`name=value` 列表、
Netscape `cookies.txt`（curl / yt-dlp 可直接用）。

### 3.4 MCP 服务

```
GET  /sse                    → SSE 长连接（MCP 传输通道）
POST /sse?sessionId=<uuid>   → 客户端上行 JSON-RPC
GET  /health                 → 健康检查（返回版本号与 SSE 路径）
```

> ### ⚠️ 安全提醒：v1.01 起**没有任何鉴权**
>
> 按需求，v1.01 移除了 MCP 的安全 Token。这意味着：
>
> - 只要知道 `http://<你的IP>:8080/sse`，**同一局域网内的任何人**（包括同一 WiFi 下的
>   其他设备、被入侵的智能家居设备）都能调用 `get_current_cookies` 读到你的 Cookie，
>   而这些 Cookie 往往就是各网站的登录凭证。
> - App 没有任何办法区分请求来自"你的 Agent"还是别人。
>
> **请只在可信网络下开启，用完就关。** 不要在公司 / 咖啡厅 / 宿舍等共享网络上常开。
> 面板里也用红色提示块写明了这一点。
>
> 如果想加回鉴权，有两个已知的坑（代码注释里也留了）：
>
> 1. Ktor 3 的 `PipelineContext` **只有 `context`，没有 `call`**（已用 javap 核对 3.5.1 字节码），
>    而且 `httpMethod` 是 `io.ktor.server.request` 包里的扩展，必须显式 import；
> 2. MCP SDK 广播的 POST 地址是**只有 query 的相对引用** `?sessionId=<uuid>`
>    （`SseServerTransport.start()` 第 65 行：
>    `data = "${endpoint.encodeURLPath()}?$SESSION_ID_PARAM=$sessionId"`）。
>    客户端按 RFC 3986 §5.3 把它解析到当前 URL 时，**基 URL 的 query 会被整体替换**，
>    所以 `?token=` 到了 POST 那一步就没了 —— 只用 URL 传 token 会让第二步必然 401。
>    加回鉴权时得让 POST 也能通过（例如把 token 放进路径，或改用请求头）。
>
- **端口**：默认 8080，可改，校验范围 1024–65535；点「应用」会先停再起（`ACTION_RESTART`）。
  启动前会先用 `ServerSocket` 试绑一次，占用时给出「端口 xxxx 已被占用」的可读提示，
  而不是抛一个 `BindException` 堆栈。
- **彻底释放端口**：停止时严格按 `Server.close()` → `engine.stop(1000, 2000)` 的顺序执行。
  `stop` 的签名已核对（`ktor-server-core-jvm-3.5.1` 的 `EmbeddedServer.kt`）：
  `stop(gracePeriodMillis: Long, timeoutMillis: Long)`，非 suspend；1 秒优雅期 + 2 秒强制结束
  会确保端口被 OS 回收，避免「下次启动端口被占用」。
  这条清理路径由 `onDestroy` 统一负责 —— 用一个**独立的、不随 serviceScope 取消的**
  协程作用域去跑，所以即使是被 `stopService()` 直接杀掉，端口也一定会被释放。
- **Android 15 的前台服务时长上限**：`dataSync` 类型在 Android 15 起有运行时长限制，
  因此覆写了 `onTimeout(startId)`（API 34+）与 `onTimeout(startId, fgsType)`（API 35+），
  超时时主动关服务、释放端口，并把原因写到 MCP 面板的状态里，而不是被系统强杀。
- **一个刻意的选择**：`Route.mcp(enableDnsRebindingProtection = false)`。
  SDK 默认开启 DNS-rebinding 保护，而它的默认白名单只有 `localhost` / `127.0.0.1`，
  局域网里的 Agent 会被 403 拒绝。既然本服务本来就是给局域网用的，这里必须关掉，
  安全边界改由「只在你自己的可信网络里开着」承担。
- **引擎选 CIO**（不是 Netty）：CIO 是纯 Kotlin + NIO，`ktor-server-cio` 在 Android 上能跑；
  Netty 依赖 `java.lang.management` 等桌面 JVM 专有 API，在 Android 上基本起不来。
- **工具**：只注册 `get_current_cookies` 一个，入参都可选：

  | 参数 | 说明 |
  | --- | --- |
  | `url` | 目标网址；省略时用浏览器当前正在浏览的页面 |
  | `format` | `json`（默认，完整字段）/ `header`（Cookie 请求头）/ `netscape`（cookies.txt） |

  返回的 JSON 形如：

  ```json
  {
    "url": "https://example.com/",
    "host": "example.com",
    "count": 2,
    "timestamp": 1767225600000,
    "cookies": [
      { "name": "session", "value": "abc123", "domain": "example.com",
        "path": "/", "secure": true, "httpOnly": true,
        "hostOnly": true, "inferred": true }
    ]
  }
  ```

### 3.5 无痕模式

菜单里的「新建无痕页面」会打开 `IncognitoActivity`。
它的隔离靠两件事配合，**缺一不可**：

| 机制 | 作用 |
| --- | --- |
| `android:process=":incognito"` | Activity 跑在**独立进程**里 |
| `WebView.setDataDirectorySuffix("incognito")` | 该进程改用**独立的 WebView 数据目录** —— Cookie、HTTP 缓存、LocalStorage、IndexedDB 全部与普通页面分开 |

只做其中一件都不够：

- 只分进程、不分数据目录 —— 两个进程会写同一份 Cookie 数据库，**既隔离不了，还有并发写入损坏的风险**；
- 只分数据目录、不分进程 —— `setDataDirectorySuffix` 本身是进程级的，同一个进程里改不了。

**关闭无痕窗口时**（`onDestroy` 且 `isFinishing`）会清掉这个进程数据目录里的
`CookieManager.removeAllCookies()` 与 `WebStorage.deleteAllData()`，也就是「退出即清除」。

**顺带一个好处**：MCP 服务跑在主进程，读的是主进程那份 Cookie 库，
所以它**天然读不到无痕页面的任何数据** —— 无痕页面里的 Cookie 不会被 Agent 取走。

**视觉区分**（需求明确要求的部分）：

- 顶部一条紫底提示条：`无痕模式 · 不保存 Cookie，关闭窗口后自动清除`；
- 整套配色换成偏紫（`WebCookieTheme(incognito = true)` -> `IncognitoLight` / `IncognitoDark`）；
- 窗口在「最近任务」里的底色也是紫的（`Theme.WebCookieBrowser.Incognito`）；
- 标签页列表里的无痕标签用「隐身」图标（`AppIcons.Incognito`）；
- 新标签页默认标题是「无痕页面」。

**局限（重要）**：`WebView.setDataDirectorySuffix` 需要 **API 28（Android 9）**。

在 API 26/27 上两个进程会共用同一份 Cookie 库 —— 那不但是「假无痕」，
还有并发写入损坏数据的风险。所以这种情况**无痕入口会置灰**并提示「需 Android 9+」，
而不是给一个看起来能用、实际不安全的功能。

如果你希望覆盖 Android 8.x，两条路：把 `minSdk` 提到 28（推荐，8.x 在 2026 年占比已很低），
或者接受一个「同进程 + 全局关闭 Cookie」的弱化实现（隔离度差很多，而且会影响普通标签页）。

### 3.6 在 MCP 客户端里配置

支持 SSE 的客户端（Claude Desktop、Cursor、Cline 等）直接填 URL：

```json
{
  "mcpServers": {
    "webcookie": {
      "url": "http://192.168.1.5:8080/sse"
    }
  }
}
```

只支持 stdio 的老客户端，用 `mcp-remote` 做一层桥接：

```json
{
  "mcpServers": {
    "webcookie": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://192.168.1.5:8080/sse"]
    }
  }
}
```

调试时可以直接敲：

```bash
curl http://192.168.1.5:8080/health
curl -N "http://192.168.1.5:8080/sse"                  # -N 关掉缓冲，能看到 endpoint 事件
```

---

## 4. 权限说明

Manifest 里声明了：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

两处**必须**额外加上、否则一定崩：

1. `FOREGROUND_SERVICE_DATA_SYNC` —— targetSdk ≥ 34 时，服务的
   `foregroundServiceType="dataSync"` 必须配套这个权限，
   否则 `startForeground()` 抛 `SecurityException` / `MissingForegroundServiceTypeException`。
2. `POST_NOTIFICATIONS` —— Android 13+ 展示前台服务通知需要它。
   代码里在**打开 MCP 开关的那一刻**才申请（`rememberLauncherForActivityResult`），
   用户拒绝也不影响服务运行，只是状态栏没通知。

`android:usesCleartextTraffic="true"` 是必需的：MCP 走的是局域网明文 HTTP
（`http://192.168.x.x:8080`）。

---

## 5. App 图标

三份**坐标完全一致**的实现（viewport 都是 108×108），改一份可以照抄到另外两份：

| 文件 | 用途 |
| --- | --- |
| `artwork/app_icon.svg` | 矢量源文件，带渐变底色，512×512 渲染，可直接导出 PNG / 应用商店素材 |
| `artwork/render_icon.py` | 自检脚本：真实采样 SVG 的 path 数据并栅格化成 PNG，顺带校验安全区与咬痕填充 |
| `artwork/app_icon_preview.png` | 上面脚本的输出（512×512 预览图） |
| `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` | Android 自适应图标三层 |
| `ui/icon/AppIcon.kt` | Compose `ImageVector`（`WebCookieIcon` 完整版 / `WebCookieMark` 可着色版） |

![图标预览](artwork/app_icon_preview.png)

造型：**深蓝渐变底 + 白色地球（外圆 + 赤道 + 经线）+ 一块带咬痕的焦糖饼干（4 颗巧克力豆）**。
所有元素都落在自适应图标的安全区内（中心 66×66，即坐标 21–87），不会被系统裁掉 ——
`render_icon.py` 会打印实测包围盒与越界点数量来验证这一点。

> **咬痕为什么不用 `evenOdd` 挖空？**
> 直觉写法是「画一个饼干圆 + 再画一个咬痕圆，用 `evenOdd` 让重叠部分变透明」。
> 但 `evenOdd` 的规则是「被奇数条子路径覆盖就填充」，于是**咬痕圆伸出饼干之外的那一段
> 只被 1 条子路径覆盖，反而会被填充成一块多出来的凸起**，而且描边也会跟着画出去。
> 正确做法是用一条不自交的路径把缺口直接描进轮廓：先从 `P1(76,58.88)` 沿 `r=9`
> 的圆弧凹进饼干走到 `P2(82.95,69.78)`，再沿饼干自身 `r=14` 的圆弧顺时针绕 305° 回到 `P1`。
> 缺口圆弧的 `large-arc=0 / sweep=0`、外圈圆弧的 `large-arc=1 / sweep=1` 都是按这个绕向推出来的。
> （巧克力豆则确实适合 `evenOdd`：豆子完全在饼干内部，偶数覆盖 = 挖成孔。）

> `res/mipmap-anydpi-v26/` 与 `res/mipmap-anydpi-v33/` 各有一份自适应图标：
> `-v26` 只含 `background` + `foreground`，`-v33` 多一个 `<monochrome>`。
> 因为 `minSdk = 26`，所有设备都原生支持自适应图标，**不需要**再准备
> `mipmap-hdpi` 之类的 PNG 兜底。
> 之所以要拆成两个目录而不是把 `<monochrome>` 直接写进 `-v26`：后者虽然也是常见做法
> （旧框架一般会忽略不认识的子元素），但那是**依赖框架的容错行为**；
> 拆开之后 API 26–32 连这个元素都看不到，资源限定符会保证解析到 `-v26` 那份，
> 不存在任何"旧版本会怎样"的疑问。

---

## 6. 已知取舍

- **`material-icons-extended` 没有引入**。该库自 2025-02 起冻结在 `1.7.8` 且不再维护，
  官方已弃用。`Icons.Default.*` 里稳定可用的图标（Close / Refresh / Search / Menu / Info /
  Lock / Home / Add / Delete / Check / MoreVert）照常用，缺的 4 个
  （复制 / 下载 / 显示 / 隐藏）在 `ui/icon/AppIcons.kt` 里手绘成 `ImageVector`。
- **release 构建默认关闭 R8**（`isMinifyEnabled = false`）。`proguard-rules.pro` 里已经写好
  Ktor / kotlinx.serialization / MCP SDK 的 keep 规则，确认功能正常后把两行改成 `true` 即可。
- **`setSupportMultipleWindows(true)`** 配合 `onCreateWindow` 把 `target=_blank`
  转成新标签页；如果你更希望弹窗在当前页打开，把它改成 `false`。
- **主页是 `https://www.bing.com/`**，`normalizeInput()` 会把不像网址的输入交给 Bing 搜索。
  改 `MainActivity.kt` 顶部的 `HOME_URL` / `SEARCH_URL` 即可换成别的搜索引擎。
- **不做 SSL 错误绕过**：`onReceivedSslError` 没有覆写，证书错误就按系统的默认行为拒绝。
