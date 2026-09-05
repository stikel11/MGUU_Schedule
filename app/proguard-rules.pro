# Keep model classes for Gson serialization
-keep class com.mguuschedule.model.** { *; }

# Keep GSON annotations
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.TypeAdapter { *; }
-keep class com.google.gson.TypeAdapterFactory { *; }
-keep class com.google.gson.JsonSerializer { *; }
-keep class com.google.gson.JsonDeserializer { *; }

# Jsoup
-keep class org.jsoup.** { *; }
