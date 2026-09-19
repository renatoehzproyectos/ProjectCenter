# Project Center ProGuard rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Retrofit / Moshi
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Keep data models
-keep class com.projectcenter.app.domain.models.** { *; }
-keep class com.projectcenter.app.data.github.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
