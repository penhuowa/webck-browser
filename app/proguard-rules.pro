# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.app.webcookies.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.app.webcookies.**$$serializer { *; }

# ---- Ktor / CIO (反射 + ServiceLoader) ----
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- MCP Kotlin SDK ----
-keep class io.modelcontextprotocol.** { *; }
-dontwarn io.modelcontextprotocol.**

# ---- Okio / SLF4J 之类可选依赖 ----
-dontwarn org.slf4j.**
-dontwarn okio.**
-dontwarn java.lang.management.**
