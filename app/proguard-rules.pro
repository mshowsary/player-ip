# kotlinx.serialization — keep serializers for our DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.novaplay.tv.**$$serializer { *; }
-keepclassmembers class com.novaplay.tv.** {
    *** Companion;
}
-keepclasseswithmembers class com.novaplay.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
