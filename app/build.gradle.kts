import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // 注意：不要加 id("org.jetbrains.kotlin.android")。
    // AGP 9.0 起 Kotlin 支持内建，显式应用该插件会报
    // "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0"
    // 并直接构建失败。Kotlin 版本由 AGP 决定（9.4.1 内建 2.4.20）。
    // Kotlin 2.0 起 Compose 编译器随 Kotlin 版本发布，必须显式应用
    id("org.jetbrains.kotlin.plugin.compose")
    // CookieUtils / MCP 工具返回 JSON 需要 @Serializable
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.app.webcookies"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.app.webcookies"
        // minSdk 26：
        //  · Ktor 3 的 CIO 引擎依赖 java.nio.channels.AsynchronousChannelGroup（API 26+）
        //  · 自适应图标（mipmap-anydpi-v26）从 26 起得到原生支持，无需再准备 PNG 兜底
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        // v1.02：修「无痕模式退不出去」与「网页弹窗闪退」；
        //        性能优化：CookieManager.flush 移出主线程并节流、地址栏输入状态下沉
        versionName = "1.02"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 首次跑通建议先关掉混淆，确认功能正常后再打开。
            // proguard-rules.pro 里已经写好了 Ktor / kotlinx.serialization / MCP SDK 的 keep 规则，
            // 打开时把下面两行改成 true 即可。
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // MCP Kotlin SDK 0.15.0 的 JVM 字节码目标是 Java 11，这里取 17（>= 11 即可）
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 打开 BuildConfig，让「关于」页与 MCP serverInfo 直接引用 BuildConfig.VERSION_NAME，
        // 版本号只有 build.gradle.kts 这一个来源，不会再出现两处写死、改一处漏一处的问题。
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // Ktor / MCP SDK 会带进来一批重复的 META-INF 文件，必须排掉否则打包失败
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ---------------- AndroidX 基础 ----------------
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")

    // ---------------- Compose（版本交给 BOM 统一管理）----------------
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // 提供 Icons.Default.*（Close / Refresh / Search / Menu / Info / Lock / Home / Add / Delete / Check / MoreVert）
    // 以及 Icons.AutoMirrored.Filled.ArrowBack / ArrowForward。
    // 版本不写，交给上面的 BOM —— 已核对 compose-bom 2026.09.00 的 POM，
    // 它确实管理 material-icons-core（1.7.8）与 material-icons-extended（1.7.8）。
    // 注意：material-icons-extended 已于 2025-02 冻结在 1.7.8 且不再维护，
    // 本项目缺的 4 个图标（复制 / 下载 / 显示 / 隐藏）在 ui/icon/AppIcons.kt 里手绘，不引入该库。
    implementation("androidx.compose.material:material-icons-core")

    // ---------------- 协程 & 序列化 ----------------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // ---------------- Ktor Server ----------------
    // 引擎选 CIO：纯 Kotlin + NIO，不依赖 Netty（Netty 在 Android 上基本跑不起来）。
    // 版本 3.5.1 与 MCP Kotlin SDK 0.15.0 的 POM 完全一致，避免版本漂移。
    implementation("io.ktor:ktor-server-core:3.5.1")
    implementation("io.ktor:ktor-server-cio:3.5.1")
    implementation("io.ktor:ktor-server-sse:3.5.1")
    implementation("io.ktor:ktor-server-cors:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")

    // ---------------- MCP (Model Context Protocol) Kotlin SDK ----------------
    // 该 SDK 只发布 JVM 变体（没有 android target），Android 会通过
    // org.jetbrains.kotlin.platform.type=standard-jvm 的兼容规则解析到 -jvm 变体。
    // 万一你的 Gradle 版本解析失败，改成下面这个显式坐标即可：
    //   implementation("io.modelcontextprotocol:kotlin-sdk-server-jvm:0.15.0")
    implementation("io.modelcontextprotocol:kotlin-sdk-server:0.15.0")
}
