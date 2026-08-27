# Retrofit + kotlinx.serialization
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers class ** { @kotlinx.serialization.Serializable <fields>; }
-keep class com.marisbyte.invest.data.remote.**$$serializer { *; }
-keepclassmembers class com.marisbyte.invest.data.remote.** { *** Companion; }
