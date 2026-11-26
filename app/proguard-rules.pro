# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.chaisteps.app.**$$serializer { *; }
-keepclassmembers class com.chaisteps.app.** { *** Companion; }
-keepclasseswithmembers class com.chaisteps.app.** { kotlinx.serialization.KSerializer serializer(...); }
# osmdroid
-dontwarn org.osmdroid.**
