# ═══════════════════════════════════════════════════════════════
# Abu Zahra Admin - ProGuard / R8 Rules
# ═══════════════════════════════════════════════════════════════

# ─── Kotlin ──────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ─── Gson ────────────────────────────────────────────────────
# Keep all model classes used with Gson (prevent type erasure)
-keep class com.abuzahra.admin.data.model.** { *; }
-keep class com.abuzahra.admin.data.api.** { *; }

# Keep fields with @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Prevent Gson TypeToken/TypeAdapter issues
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gson's internal classes that handle generic types
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep $Gson types (anonymous TypeToken subclasses)
-keep class com.abuzahra.admin.data.api.**$Gson*
-keep class com.abuzahra.admin.data.model.**$Gson*

# ─── Retrofit ────────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# Keep Retrofit service interfaces (suspend functions + return types)
-keep,allowobfuscation interface com.abuzahra.admin.data.api.RetrofitApiService
-keep class retrofit2.** { *; }

# Don't warn about Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.HttpException { *; }

# ─── OkHttp ──────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─── Coroutines ──────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ─── General Android ─────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ─── Suppress common warnings ────────────────────────────────
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlin.collections.EmptyList
-dontwarn kotlin.collections.EmptyMap
-dontwarn kotlin.collections.EmptySet