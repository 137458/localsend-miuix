# LocalSend Miuix Proguard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class io.ktor.** { *; }
-keep class top.yukonga.miuix.** { *; }
