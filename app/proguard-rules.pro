# Media3 / ExoPlayer
-dontwarn com.google.common.**
-keep class androidx.media3.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep data classes used by reflection-free JSON parsing
-keep class com.auris.app.model.** { *; }
