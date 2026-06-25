# Add project specific ProGuard rules here.

# ================================
# Extension API (Mihon/Tachiyomi)
# ================================
# BẮT BUỘC giữ lại nguyên vẹn vì các APK Extension bên thứ 3 compile dựa trên tên gốc của các interface/class này.
-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keep class rx.** { *; }
-keep interface rx.** { *; }
-keep class mihon.extension.** { *; }
-keep interface mihon.extension.** { *; }

# ================================
# Injekt (Dependency Injection)
# ================================
-keep class uy.kohesive.injekt.** { *; }
-keepclassmembers class * implements uy.kohesive.injekt.api.InjektRegistrar {
    public <init>(...);
}

# ================================
# JavaScript Runtime (QuickJS / Duktape)
# ================================
-keep class app.cash.quickjs.** { *; }

# ================================
# Kotlin Serialization
# ================================
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ================================
# JSoup & Network
# ================================
-keep class org.jsoup.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn org.jsoup.**

# ================================
# General Android Rules
# ================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose 
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# SQLDelight 
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# WorkManager
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker {
    public <init>(...);
}

# Source JS Models (nếu có map qua JS)
-keep class com.example.manga_readerver2.source_js.models.** { *; }