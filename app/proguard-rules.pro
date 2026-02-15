# ProGuard rules for 4iran Secure Messaging App

# Keep model classes
-keep class com.fouriran.securemessaging.data.model.** { *; }
-keep class com.fouriran.securemessaging.network.protocol.** { *; }
-keep class com.fouriran.securemessaging.encryption.** { *; }

# Keep encryption keys and certificates
-keepclassmembers class * {
    @javax.crypto.spec.SecretKeySpec <fields>;
}

# Keep protobuf generated classes
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }

# Keep gRPC classes
-keep class io.grpc.** { *; }
-keep class * extends io.grpc.stub.AbstractStub { *; }

# Keep WebSocket classes
-keep class io.socket.** { *; }

# Keep Netty classes
-keep class io.netty.** { *; }
-dontwarn io.netty.**

# Keep MQTT classes
-keep class org.eclipse.paho.** { *; }

# Keep XMPP/Smack classes
-keep class org.jivesoftware.smack.** { *; }
-keep class org.jxmpp.** { *; }
-dontwarn org.jivesoftware.smack.**

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Tink
-keep class com.google.crypto.tink.** { *; }

# Keep Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Hilt
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep class dagger.hilt.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *

# General Android
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-assumenosideeffects class timber.log.Timber {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
