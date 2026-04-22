# Add project specific ProGuard rules here.

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembernames class * { @dagger.hilt.* <methods>; }

# Keep Room entities and DAOs
-keep class com.gasprice.data.local.** { *; }

# Keep domain models (used by Room via reflection)
-keep class com.gasprice.domain.model.** { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.libraries.places.** { *; }

# Keep enum names (used in Room string columns)
-keepclassmembers enum * { *; }

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
