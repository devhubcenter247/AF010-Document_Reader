
-keep @com.google.gson.annotations.SerializedName class * {
    <fields>;
}
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class kotlin.Metadata
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedTypeVariable
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedArrayType
-dontwarn java.lang.reflect.AnnotatedWildcardType

# Tránh R8 inline/strip sâu vào Guava reflect:
-keep class com.google.common.reflect.** { *; }
-keepclassmembers class com.google.common.reflect.** { *; }