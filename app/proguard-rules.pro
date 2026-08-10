# FlowCam ProGuard / R8 rules

# Keep CameraX classes that use reflection for vendor extensions
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Camera2 interop annotated classes
-keep class androidx.camera.camera2.interop.** { *; }

# Kotlin metadata / coroutines
-keepattributes *Annotation*
-keepclassmembers class kotlin.Metadata { public <methods>; }
-dontwarn kotlinx.coroutines.**

# DataStore
-keep class androidx.datastore.*.** { *; }

# Keep our data/model classes used for settings & capability inspection
-keep class com.flowcam.camera.** { *; }
-keep class com.flowcam.settings.** { *; }
