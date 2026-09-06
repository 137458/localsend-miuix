# LocalSend Miuix Proguard Rules

# Keep annotations and inner classes
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Kotlinx Serialization
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable class *;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class * implements kotlinx.serialization.KSerializer {
    *;
}

# LocalSend Models & DTOs (Preserve property names for JSON REST API)
-keep class org.localsend.miuix.model.** { *; }

# Ktor Server & Client
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Netty
-dontwarn io.netty.**
-keep class io.netty.** { *; }
-dontwarn java.nio.ByteBuffer
-dontwarn sun.misc.Unsafe
-dontwarn org.jboss.marshalling.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.apache.commons.logging.**
-dontwarn com.google.protobuf.**
-dontwarn com.ning.compress.**
-dontwarn lz4.**

# Bouncy Castle (TLS and self-signed certificate generation)
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-dontwarn org.bouncycastle.**

# Miuix UI Components & Navigation
-dontwarn top.yukonga.miuix.**
-keep class top.yukonga.miuix.** { *; }

# Okio & ZXing
-dontwarn okio.**
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Coroutines & Logging
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
-dontwarn reactor.blockhound.**
