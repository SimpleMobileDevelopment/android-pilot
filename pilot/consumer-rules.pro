# Pilot - keep public API
-keep class co.pilot.android.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class co.pilot.android.** {
    *** Companion;
}
