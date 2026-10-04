package com.app.webcookies

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.app.webcookies.browser.BrowserBridge
import com.app.webcookies.mcp.McpPrefs
import com.app.webcookies.mcp.McpServerService
import com.app.webcookies.mcp.McpServiceState
import com.app.webcookies.ui.CookieEditorSheet
import com.app.webcookies.ui.icon.AppIcons
import com.app.webcookies.ui.icon.WebCookieAppLogo
import com.app.webcookies.ui.icon.WebCookieMarkIcon
import com.app.webcookies.ui.theme.MonoTextStyle
import com.app.webcookies.ui.theme.WebCookieTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.util.UUID
import kotlin.coroutines.resume

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------

private const val HOME_URL = "https://www.bing.com/"
private const val SEARCH_URL = "https://www.bing.com/search?q="
private const val ABOUT_URL = "https://jdtool.cyou"
private const val ABOUT_AUTHOR = "等雨停"
private const val ABOUT_SITE = "jdtool.cyou"
private const val ABOUT_DESC = "一个支持 Cookie 管理与 MCP 服务的轻量级浏览器。"

/**
 * 单 Activity + Compose。
 *
 * 旋转 / 分屏不做重建：Manifest 里声明了 configChanges，
 * 这样 WebView 实例和已登录的页面状态不会被销毁。
 */
class MainActivity : ComponentActivity() {

    /** 外部应用通过 VIEW intent 传来的地址（可能为 null）。 */
    private var incomingUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        incomingUrl = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

        setContent {
            WebCookieTheme {
                BrowserScreen(incomingUrl = incomingUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            incomingUrl = intent.dataString
        }
    }
}

// ---------------------------------------------------------------------------
// 浏览器主界面
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    incomingUrl: String?,
    /** 无痕模式：换配色、顶部显示提示条、菜单项也不同。 */
    incognito: Boolean = false
) {
    val context = LocalContext.current
    val controller = remember { BrowserController(context, incognito) }

    // 首个标签页 & 外部链接；用 incomingUrl 当 key，天然只在变化时触发
    LaunchedEffect(incomingUrl) {
        if (controller.tabs.isEmpty()) {
            controller.newTab(incomingUrl ?: HOME_URL)
        } else if (!incomingUrl.isNullOrBlank()) {
            controller.newTab(incomingUrl)
        }
    }

    // Activity 销毁时释放 WebView，避免内存泄漏
    DisposableEffect(Unit) {
        onDispose { controller.destroyAll() }
    }

    var addressText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showCookieSheet by remember { mutableStateOf(false) }
    var showMcpPanel by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    val tab = controller.activeTab
    val webView = tab?.webView

    // 页面导航完成后回填地址栏；用户正在输入时 tab.url 不变，所以不会被打断
    LaunchedEffect(tab?.id, tab?.url) {
        addressText = tab?.url.orEmpty()
    }

    // 系统返回键优先用于网页后退
    BackHandler(enabled = tab?.canGoBack == true) { webView?.goBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        // ---------------- 顶部：地址栏 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AddressField(
                value = addressText,
                onValueChange = { addressText = it },
                secure = addressText.startsWith("https://"),
                onSubmit = {
                    val target = normalizeInput(addressText)
                    addressText = target
                    val current = webView
                    if (current != null) current.loadUrl(target) else controller.newTab(target)
                },
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { webView?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                }
                BrowserMenu(
                    expanded = showMenu,
                    incognito = incognito,
                    onDismiss = { showMenu = false },
                    onNewTab = {
                        showMenu = false
                        controller.newTab(HOME_URL)
                        addressText = HOME_URL
                    },
                    onNewIncognitoTab = {
                        showMenu = false
                        if (incognito) {
                            // 已经在无痕窗口里了，再开一个无痕标签页即可
                            controller.newTab(HOME_URL)
                            addressText = HOME_URL
                        } else {
                            // 普通窗口 -> 拉起独立进程的无痕窗口
                            openIncognito(context)
                        }
                    },
                    onClearHistory = {
                        showMenu = false
                        // 只清除当前 WebView 的浏览历史（前进/后退栈）
                        webView?.clearHistory()
                        tab?.let { it.canGoBack = false; it.canGoForward = false }
                        toast(context, "已清除浏览记录")
                    },
                    onWipeAll = {
                        showMenu = false
                        confirmWipe = true
                    },
                    onMcp = {
                        showMenu = false
                        showMcpPanel = true
                    },
                    onAbout = {
                        showMenu = false
                        showAbout = true
                    }
                )
            }
        }

        // ---------------- 无痕模式提示条 ----------------
        if (incognito) {
            IncognitoBanner()
        }

        // ---------------- 加载进度 ----------------
        LoadProgressBar(tab)

        // ---------------- WebView ----------------
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx -> FrameLayout(ctx).also { controller.bindContainer(it) } },
                update = { controller.syncActive() },
                modifier = Modifier.fillMaxSize()
            )

            // 首个标签页还没拿到 URL 时显示一个转圈
            if (tab == null || tab.url.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
        }

        // ---------------- 底部工具栏 ----------------
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = tab?.canGoBack == true) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "后退")
            }
            IconButton(onClick = { webView?.goForward() }, enabled = tab?.canGoForward == true) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "前进")
            }
            IconButton(onClick = { webView?.loadUrl(HOME_URL) }) {
                Icon(Icons.Default.Home, contentDescription = "主页")
            }

            Spacer(Modifier.weight(1f))

            // Cookie 管理：独立的快捷入口，不占用菜单项
            TextButton(onClick = { showCookieSheet = true }) {
                WebCookieMarkIcon(size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Cookie")
            }

            // 标签页：用浏览器窗口方块图标（而不是汉堡菜单），一眼就知道是标签页
            TextButton(onClick = { showTabs = true }) {
                Icon(AppIcons.Tab, contentDescription = "标签页", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(controller.tabs.size.toString())
            }
        }
    }

    // ================= 弹层 =================

    if (showCookieSheet) {
        CookieEditorSheet(
            url = tab?.url,
            onDismiss = { showCookieSheet = false },
            onMessage = { toast(context, it) }
        )
    }

    if (showTabs) {
        TabsSheet(
            controller = controller,
            incognito = incognito,
            onDismiss = { showTabs = false }
        )
    }

    if (showMcpPanel) {
        McpPanelDialog(
            onDismiss = { showMcpPanel = false },
            onMessage = { toast(context, it) }
        )
    }

    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onOpenSite = {
                showAbout = false
                controller.newTab(ABOUT_URL)
            }
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("将浏览器记录数据全部删除？") },
            text = {
                Text(
                    "会清空全部 Cookie、缓存、表单数据与本地存储，" +
                        "所有网站的登录状态都会丢失。此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    controller.wipeAll()
                    toast(context, "已删除全部浏览器记录数据")
                }) {
                    Text("全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("取消") }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// 地址栏
// ---------------------------------------------------------------------------

/**
 * 加载进度条。
 *
 * 特意抽成一个独立 composable：`tab.progress` 会被 `onProgressChanged` 频繁改写，
 * 如果在 `BrowserScreen` 顶层直接读它，每一 tick 都会让整个浏览器界面重组
 * （包括地址栏、工具栏、AndroidView 的 update 回调）。把读取关在这里，
 * 重组范围就只有这 2dp 高的进度条。
 */
@Composable
private fun LoadProgressBar(tab: BrowserTab?) {
    val progress = tab?.progress ?: 1f
    if (progress in 0.01f..0.99f) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        )
    } else {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    secure: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(21.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (secure) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "安全连接",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFF2E7D32)
                )
                Spacer(Modifier.width(6.dp))
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = "搜索或输入网址",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** 补全协议；不像网址就走搜索引擎。 */
private fun normalizeInput(raw: String): String {
    val input = raw.trim()
    if (input.isEmpty()) return HOME_URL
    if (input.startsWith("http://") || input.startsWith("https://") ||
        input.startsWith("file://") || input.startsWith("about:")
    ) {
        return input
    }
    // 形如 example.com / 1.2.3.4:8080 / localhost:8080 的当作网址
    val looksLikeHost = !input.contains(' ') && (
        input.contains('.') || input.startsWith("localhost") || input.contains("://")
        )
    return if (looksLikeHost) "https://$input" else SEARCH_URL + android.net.Uri.encode(input)
}

// ---------------------------------------------------------------------------
// 右上角菜单
// ---------------------------------------------------------------------------

@Composable
private fun BrowserMenu(
    expanded: Boolean,
    incognito: Boolean,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onClearHistory: () -> Unit,
    onWipeAll: () -> Unit,
    onMcp: () -> Unit,
    onAbout: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // 普通窗口才给“新建页面”；无痕窗口里新建出来的一律是无痕页
        if (!incognito) {
            MenuRow(Icons.Default.Add, "新建页面", onNewTab)
        }
        if (incognito || IncognitoActivity.isSupported) {
            MenuRow(AppIcons.Incognito, "新建无痕页面", onNewIncognitoTab)
        } else {
            // API 26/27 没有独立 WebView 数据目录，宁可不提供，也不做“假无痕”
            MenuRow(
                icon = AppIcons.Incognito,
                label = "新建无痕页面（需 Android 9+）",
                onClick = {},
                enabled = false
            )
        }
        MenuRow(AppIcons.History, "清除浏览记录", onClearHistory)
        MenuRow(Icons.Default.Delete, "将浏览器记录数据全部删除", onWipeAll, destructive = true)
        HorizontalDivider()
        // MCP 只读主进程（普通页面）的 Cookie，无痕窗口里不提供入口，避免误解
        if (!incognito) {
            MenuRow(AppIcons.Server, "MCP 服务", onMcp)
        }
        MenuRow(Icons.Default.Info, "关于", onAbout)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    // 注意：这里不能用 Color.Unspecified 当 tint。
    // Icon 在 tint == Color.Unspecified 时会**跳过** ColorFilter，
    // 于是矢量图里写死的 Color.Black 会原样画出来 —— 深色主题下等于看不见。
    // 用 LocalContentColor 才是 DropdownMenuItem 想给的正確颜色。
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> LocalContentColor.current
    }

    DropdownMenuItem(
        text = { Text(text = label, color = color) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = color)
        },
        enabled = enabled,
        onClick = onClick
    )
}

// ---------------------------------------------------------------------------
// 标签页列表
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsSheet(
    controller: BrowserController,
    incognito: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (incognito) "无痕标签页" else "标签页",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    controller.newTab(HOME_URL)
                    onDismiss()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items = controller.tabs, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                text = t.url.ifEmpty { "空白页" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            when {
                                t.id == controller.activeId -> Icon(
                                    Icons.Default.Check,
                                    contentDescription = "当前",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                // 无痕窗口里的标签页统一用“隐身”图标区分
                                incognito -> Icon(
                                    AppIcons.Incognito,
                                    contentDescription = "无痕标签页",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                else -> WebCookieMarkIcon(size = 20.dp)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                controller.closeTab(t)
                                if (controller.tabs.isEmpty()) onDismiss()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭标签页")
                            }
                        },
                        modifier = Modifier.clickable {
                            controller.selectTab(t)
                            onDismiss()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MCP 服务面板
// ---------------------------------------------------------------------------

@Composable
private fun McpPanelDialog(onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { McpPrefs(context) }
    val status by McpServiceState.status.collectAsState()

    var enabled by remember { mutableStateOf(status.running || prefs.enabled) }
    var portText by remember {
        mutableStateOf((if (status.running) status.port else prefs.port).toString())
    }

    val portValue = portText.toIntOrNull()
    val portError = McpPrefs.portRangeError(portValue)

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            onMessage("未授予通知权限：MCP 服务照常运行，只是状态栏不显示通知")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Server,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("MCP 服务", style = MaterialTheme.typography.titleLarge)
                }

                Text(
                    text = "开启后会在局域网内提供 SSE 接口，外部 AI Agent 可通过 " +
                        "get_current_cookies 工具读取本机 Cookie。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )

                // ---- 开关 ----
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用 MCP 服务", style = MaterialTheme.typography.titleMedium)
                            StatusLine(running = status.running)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { on ->
                                enabled = on
                                prefs.enabled = on
                                if (on) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationLauncher.launch(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                    McpServerService.start(context, prefs.port)
                                    onMessage("正在启动 MCP 服务…")
                                } else {
                                    McpServerService.stop(context)
                                    onMessage("正在停止 MCP 服务…")
                                }
                            }
                        )
                    }
                }

                // ---- 端口 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { input ->
                            portText = input.filter { it.isDigit() }.take(5)
                        },
                        label = { Text("端口号") },
                        placeholder = { Text("8080") },
                        isError = portError != null,
                        supportingText = {
                            Text(portError ?: "范围 ${McpPrefs.MIN_PORT} - ${McpPrefs.MAX_PORT}")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val p = portValue
                            if (p == null || portError != null) {
                                onMessage(portError ?: "端口不合法")
                                return@Button
                            }
                            prefs.port = p
                            if (enabled) {
                                McpServerService.restart(context, p)
                                onMessage("正在以端口 $p 重启服务…")
                            } else {
                                onMessage("端口已保存：$p")
                            }
                        },
                        enabled = portError == null
                    ) {
                        Text("应用")
                    }
                }

                // ---- 运行地址 ----
                if (status.running && status.endpoints.isNotEmpty()) {
                    SectionTitle("局域网地址")
                    status.endpoints.forEach { endpoint ->
                        CopyRow(
                            text = endpoint,
                            mono = true,
                            onCopy = {
                                copyText(context, "MCP SSE", endpoint)
                                onMessage("已复制地址")
                            }
                        )
                    }
                    Text(
                        text = "MCP 客户端配置里直接填上面的 SSE 地址即可。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val errorMessage = status.error
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // ---- 安全提醒（v1.01 起已无鉴权）----
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "无鉴权提醒",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "v1.01 起已移除安全 Token。只要知道地址，" +
                                "同一局域网内的任何人都能读取本机 Cookie。" +
                                "请只在可信网络下开启，用完及时关掉。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(running: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (running) "运行中" else "已停止",
            style = MaterialTheme.typography.bodySmall,
            color = if (running) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun CopyRow(text: String, mono: Boolean, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = if (mono) MonoTextStyle else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy) {
            Icon(AppIcons.Copy, contentDescription = "复制", modifier = Modifier.size(18.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 关于
// ---------------------------------------------------------------------------

@Composable
private fun AboutDialog(onDismiss: () -> Unit, onOpenSite: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        icon = { WebCookieAppLogo(size = 64.dp) },
        title = {
            Text(
                text = "webck浏览器",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = ABOUT_DESC,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "作者：$ABOUT_AUTHOR",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = ABOUT_SITE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenSite() }
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// WebView 控制器
// ---------------------------------------------------------------------------

/** 一个标签页：WebView + 可观察的 UI 状态。 */
private class BrowserTab(
    val id: String,
    val webView: WebView,
    initialTitle: String = "新标签页"
) {
    var url by mutableStateOf("")
    var title by mutableStateOf(initialTitle)
    var progress by mutableFloatStateOf(0f)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

/**
 * 管理所有标签页的 WebView。
 *
 * 所有 WebView 都挂在同一个 [FrameLayout] 容器里，切换标签页只是替换子 View，
 * 这样既保留了每个页面的完整状态（滚动位置、表单、history），又只需要一个
 * `AndroidView`。
 */
@SuppressLint("SetJavaScriptEnabled")
private class BrowserController(
    private val context: Context,
    private val incognito: Boolean
) {

    val tabs = mutableStateListOf<BrowserTab>()

    var activeId by mutableStateOf<String?>(null)
        private set

    private var container: FrameLayout? = null

    val activeTab: BrowserTab? get() = tabs.firstOrNull { it.id == activeId }
    val activeWebView: WebView? get() = activeTab?.webView

    init {
        // MCP 工具需要读 document.cookie（JS 侧可见性），这里把能力注入桥里。
        // 无痕进程刻意**不**注入：无痕页面的数据不应该以任何形式流给 MCP。
        // （实际上无痕跑在独立进程，本来就有自己的 BrowserBridge 实例。）
        if (!incognito) {
            BrowserBridge.documentCookieProvider = {
                withContext(Dispatchers.Main) { readDocumentCookie() }
            }
        }
    }

    fun bindContainer(frame: FrameLayout) {
        container = frame
        syncActive()
    }

    fun newTab(url: String): BrowserTab {
        val webView = WebView(context)
        val tab = BrowserTab(
            id = UUID.randomUUID().toString(),
            webView = webView,
            initialTitle = if (incognito) "无痕页面" else "新标签页"
        )
        configure(webView, tab)
        tabs.add(tab)
        activeId = tab.id
        webView.loadUrl(url)
        syncActive()
        return tab
    }

    fun selectTab(tab: BrowserTab) {
        activeId = tab.id
        syncActive()
        BrowserBridge.currentUrl = tab.url
        BrowserBridge.currentTitle = tab.title
    }

    fun closeTab(tab: BrowserTab) {
        val wasActive = tab.id == activeId
        tabs.remove(tab)
        (tab.webView.parent as? android.view.ViewGroup)?.removeView(tab.webView)
        runCatching {
            tab.webView.stopLoading()
            tab.webView.loadUrl("about:blank")
            tab.webView.clearHistory()
            tab.webView.removeAllViews()
            tab.webView.destroy()
        }
        if (tabs.isEmpty()) {
            newTab(HOME_URL)
        } else if (wasActive) {
            activeId = tabs.last().id
            syncActive()
        }
    }

    /** 把当前活动标签页的 WebView 放进容器。 */
    fun syncActive() {
        val frame = container ?: return
        val webView = activeWebView
        if (webView == null) {
            frame.removeAllViews()
            return
        }
        if (webView.parent !== frame) {
            (webView.parent as? android.view.ViewGroup)?.removeView(webView)
            frame.removeAllViews()
            frame.addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    /** 彻底清空 Cookie / 缓存 / 表单 / 本地存储。 */
    fun wipeAll() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies { /* 回调只用于日志，无需处理 */ }
        cookieManager.flush()

        tabs.forEach { tab ->
            runCatching {
                tab.webView.clearCache(true)
                tab.webView.clearFormData()
                tab.webView.clearHistory()
                tab.webView.clearSslPreferences()
                tab.webView.clearMatches()
                tab.webView.loadUrl("about:blank")
                tab.canGoBack = false
                tab.canGoForward = false
            }
        }

        runCatching { WebStorage.getInstance().deleteAllData() }
        runCatching {
            // 只用没被废弃的那个 API：
            //   · 表单数据 —— 每个 WebView 的 clearFormData()（未废弃）
            //   · HTTP 认证凭据 —— WebViewDatabase.clearHttpAuthUsernamePassword()
            // WebViewDatabase.clearFormData() 已标记 @Deprecated，这里刻意不再调用。
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        }
    }

    fun destroyAll() {
        BrowserBridge.documentCookieProvider = null
        BrowserBridge.currentUrl = null
        tabs.forEach { tab ->
            (tab.webView.parent as? android.view.ViewGroup)?.removeView(tab.webView)
            runCatching { tab.webView.destroy() }
        }
        tabs.clear()
        container?.removeAllViews()
    }

    // ---------------- 内部：WebView 配置 ----------------

    private fun configure(webView: WebView, tab: BrowserTab) {
        webView.settings.apply {
            // ===== 网页缩放 =====
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // ===== 手机屏幕适配（这两项缺一不可，否则会出现“页面大小不能调整”）=====
            // useWideViewPort: 允许页面声明 viewport，使内容按屏幕宽度布局
            useWideViewPort = true
            // loadWithOverviewMode: 首次加载按屏幕宽度缩放（overview 模式）
            loadWithOverviewMode = true

            // 注意：**没有** setInitialScale 了。
            // 它在旧版本里常被写成 setInitialScale(0) 表示"不固定缩放"，
            // 但 API 37 的 android.jar 里 WebSettings 已经没有这个方法了
            // （javap 确认：只剩 setDefaultZoom / setTextZoom / setSupportZoom 等），
            // 写了会直接编译失败。而且 0 本来就是默认值，删掉不影响行为 ——
            // 上面这两项才是决定"页面能否按屏宽自适应 + 能否双指缩放"的关键。

            // ===== 基础能力 =====
            javaScriptEnabled = true
            domStorageEnabled = true
            // 注意：没有 databaseEnabled —— WebSettings.setDatabaseEnabled 已废弃
            // （WebSQL 数据库早已从 Chromium 移除），现代站点用 domStorageEnabled 就够了。
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = true

            // 允许 target=_blank 弹窗，由 onCreateWindow 转成新标签页
            setSupportMultipleWindows(true)

            // 安全：不允许网页直接读本地文件
            allowFileAccess = false
            allowContentAccess = false
        }

        // ===== Cookie =====
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // 第三方 Cookie（很多站点的登录态依赖它）
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                return when (uri.scheme?.lowercase()) {
                    // http/https 一律在应用内加载
                    "http", "https" -> false
                    else -> {
                        // tel: / mailto: / weixin: / intent: 交给系统
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank() && url != "about:blank") tab.url = url
                tab.progress = 0.05f
                tab.canGoBack = view?.canGoBack() == true
                tab.canGoForward = view?.canGoForward() == true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                tab.url = url ?: view?.url ?: tab.url
                tab.canGoBack = view?.canGoBack() == true
                tab.canGoForward = view?.canGoForward() == true
                tab.progress = 1f

                // 页面加载完成后立刻把 Cookie 落盘，避免进程被杀导致丢失。
                // 无痕进程不落盘 —— 本来就不该在磁盘上留下痕迹。
                if (!incognito) {
                    runCatching { CookieManager.getInstance().flush() }
                }

                if (tab.id == activeId) {
                    BrowserBridge.currentUrl = tab.url
                    BrowserBridge.currentTitle = tab.title
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                tab.canGoBack = view?.canGoBack() == true
                tab.canGoForward = view?.canGoForward() == true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress.coerceIn(0, 100) / 100f
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // 先落到非空的局部变量，否则 resultMsg 在后面不会被智能转换
                val message = resultMsg ?: return false
                val transport = message.obj as? WebView.WebViewTransport ?: return false
                val newTab = newTab("about:blank")
                transport.webView = newTab.webView
                message.sendToTarget()
                return true
            }
        }
    }

    // ---------------- 内部：读取 document.cookie ----------------

    private suspend fun readDocumentCookie(): String? {
        val webView = activeWebView ?: return null
        if (webView.url.isNullOrBlank()) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                webView.evaluateJavascript(BrowserBridge.JS_READ_DOCUMENT_COOKIE) { value ->
                    if (cont.isActive) cont.resume(decodeJsString(value))
                }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

/** `evaluateJavascript` 返回的是 JSON 字面量（带引号和转义），这里还原成原始字符串。 */
private fun decodeJsString(raw: String?): String? {
    if (raw == null || raw == "null" || raw == "undefined") return null
    return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
}

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

private fun copyText(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager ?: return
    manager.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

private fun toast(context: Context, message: String) {
    // Android 13 起系统在写入剪贴板时已经会自动弹提示，避免重复打扰
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && message.contains("已复制")) return
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * 拉起无痕窗口。
 *
 * 它跑在独立进程 `:incognito` 里，并在进程启动时用
 * `WebView.setDataDirectorySuffix()` 换一份独立的 WebView 数据目录，
 * 因此 Cookie / 缓存 / 本地存储与普通页面完全隔离（细节见 IncognitoActivity）。
 */
private fun openIncognito(context: Context) {
    if (!IncognitoActivity.isSupported) {
        toast(context, "无痕模式需要 Android 9 及以上")
        return
    }
    runCatching {
        context.startActivity(Intent(context, IncognitoActivity::class.java))
    }.onFailure { toast(context, "无法打开无痕窗口：${it.message}") }
}

/** 无痕模式顶部提示条：紫色底 + 隐身图标，让用户随时知道自己在无痕窗口里。 */
@Composable
private fun IncognitoBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppIcons.Incognito,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "无痕模式 · 不保存 Cookie，关闭窗口后自动清除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
