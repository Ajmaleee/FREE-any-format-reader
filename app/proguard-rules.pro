# ProGuard / R8 rules for Universal Reader

# Preserve Room database and entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# Preserve Domain and Parser Models
-keep class com.example.data.model.** { *; }
-keep class com.example.parser.** { *; }

# Coil image loader rules
-dontwarn coil.**
-keepclassmembers class * implements coil.request.Request { *; }
