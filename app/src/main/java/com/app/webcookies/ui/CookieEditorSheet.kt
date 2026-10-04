package com.app.webcookies.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.webcookies.browser.BrowserBridge
import com.app.webcookies.ui.icon.AppIcons
import com.app.webcookies.ui.theme.MonoTextStyle
import com.app.webcookies.util.CookieEntry
import com.app.webcookies.util.CookieUtils

/**
 * 仿 Chrome 插件 "Cookie-Editor" 的 Cookie 管理面板。
 *
 * 数据来源：`CookieManager.getCookie(url)`（权威，含 HttpOnly）+ 当前页面
 * `document.cookie`（补充 JS 可见性）；Domain / Path 由
 * [CookieUtils.readCookies] 多 URL 探测反推，界面上以浅色标注为"推断"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieEditorSheet(
    url: String?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var cookies by remember { mutableStateOf<List<CookieEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var keyword by remember { mutableStateOf("") }
    var reloadTick by remember { mutableIntStateOf(0) }

    // 加载 / 刷新 Cookie
    LaunchedEffect(url, reloadTick) {
        loading = true
        val jsCookie = runCatching { BrowserBridge.documentCookieProvider?.invoke() }.getOrNull()
        cookies = if (url.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { CookieUtils.readCookies(url, jsCookie) }
                .onFailure { onMessage("读取 Cookie 失败：${it.message}") }
                .getOrDefault(emptyList())
        }
        loading = false
    }

    val filtered = remember(cookies, keyword) {
        if (keyword.isBlank()) {
            cookies
        } else {
            val k = keyword.trim().lowercase()
            cookies.filter {
                it.name.lowercase().contains(k) ||
                    it.value.lowercase().contains(k) ||
                    it.domain.lowercase().contains(k)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // ---------------- 标题栏 ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cookie 管理", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = buildString {
                            append(cookies.size).append(" 条")
                            if (keyword.isNotBlank()) append(" · 匹配 ").append(filtered.size)
                            hostOf(url)?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { reloadTick++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // ---------------- 搜索 + 操作 ----------------
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索 Name / Value / Domain") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        copyToClipboard(context, "cookies.json", CookieUtils.toJson(cookies))
                        onMessage("已复制 ${cookies.size} 条 Cookie（JSON）")
                    },
                    enabled = cookies.isNotEmpty()
                ) {
                    Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制全部 JSON")
                }

                ExportMenu(
                    enabled = cookies.isNotEmpty(),
                    onExport = { label, text ->
                        copyToClipboard(context, "cookies", text)
                        onMessage("已复制（$label）")
                    },
                    buildJson = { CookieUtils.toJson(cookies) },
                    buildNetscape = { CookieUtils.toNetscape(cookies) },
                    buildHeader = { CookieUtils.toRequestHeader(cookies) },
                    buildPairs = { cookies.joinToString("\n") { it.headerPair } }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            // ---------------- 列表 ----------------
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxWidth().fillMaxHeight(), Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }

                    url.isNullOrBlank() -> EmptyHint("还没有打开任何网页\n先在地址栏输入网址吧")

                    filtered.isEmpty() -> EmptyHint(
                        if (cookies.isEmpty()) "当前网页没有 Cookie" else "没有匹配「$keyword」的 Cookie"
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = filtered, key = { it.name + "|" + it.domain + it.path }) { entry ->
                            CookieRow(entry = entry, onMessage = onMessage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().fillMaxHeight(), Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 单条 Cookie 卡片。 */
@Composable
private fun CookieRow(entry: CookieEntry, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Name + 复制按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MonoTextStyle.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (revealed) AppIcons.EyeOff else AppIcons.Eye,
                        contentDescription = if (revealed) "隐藏" else "显示",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        copyToClipboard(context, entry.name, entry.value)
                        onMessage("已复制 ${entry.name} 的 Value")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        AppIcons.Copy,
                        contentDescription = "复制 Value",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Value
            Text(
                text = if (revealed) entry.value.ifEmpty { "(空)" } else mask(entry.value),
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )

            if (entry.value.length > 80) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(if (expanded) "收起" else "展开全部", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Domain / Path / 属性
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Domain: " + (entry.domain.ifEmpty { "—" }) + "   Path: " + entry.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.inferred) {
                        Text(
                            text = "Domain/Path 为推断值",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                FlagChip("Secure", entry.secure)
                if (entry.httpOnly) FlagChip("HttpOnly", true)
                if (entry.isSession) FlagChip("Session", true)
            }
        }
    }
}

@Composable
private fun FlagChip(label: String, on: Boolean) {
    if (!on) return
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ExportMenu(
    enabled: Boolean,
    onExport: (label: String, text: String) -> Unit,
    buildJson: () -> String,
    buildNetscape: () -> String,
    buildHeader: () -> String,
    buildPairs: () -> String
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) {
            Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("导出")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("JSON（Cookie-Editor 格式）") },
                onClick = { open = false; onExport("JSON", buildJson()) }
            )
            DropdownMenuItem(
                text = { Text("Cookie 请求头") },
                onClick = { open = false; onExport("Cookie 头", buildHeader()) }
            )
            DropdownMenuItem(
                text = { Text("name=value 列表") },
                onClick = { open = false; onExport("name=value", buildPairs()) }
            )
            DropdownMenuItem(
                text = { Text("Netscape cookies.txt") },
                onClick = { open = false; onExport("Netscape", buildNetscape()) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 小工具
// ---------------------------------------------------------------------------

private fun hostOf(url: String?): String? =
    url?.let { runCatching { android.net.Uri.parse(it).host }.getOrNull() }

private fun mask(value: String): String = when {
    value.isEmpty() -> "(空)"
    value.length <= 8 -> "•".repeat(value.length)
    else -> value.take(4) + "•".repeat((value.length - 8).coerceAtMost(24)) + value.takeLast(4)
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}
