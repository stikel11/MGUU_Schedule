# Keep model classes for Gson serialization
-keep class com.mguuschedule.model.** { *; }

# Keep database entities for Room and Gson
-keep class com.mguuschedule.repository.*Entity { *; }

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

# WorkManager
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Room
-keep class com.mguuschedule.repository.AppDatabase_Impl { *; }
