# AlarmX release shrinking rules.
# Keep Room entities and Android components used by reflection / manifests.

-keep class com.alarmx.smartalarm.data.local.** { *; }
-keep class com.alarmx.smartalarm.domain.model.** { *; }

-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

-dontwarn org.jetbrains.annotations.**
