# Androidx rules
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Compose rules
-keep class androidx.compose.** { *; }
-keep class kotlin.** { *; }

# Android framework rules
-keep public class android.** { *; }
-keep interface android.** { *; }

# Kotlin serialization
-keepclassmembers class **$Lambda* {
    *;
}

# View constructors required for inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Lifecycle stuff
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Google Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Application classes
-keep class com.yasergirit.zikirmasterpro.** { *; }
