# Add project specific ProGuard rules here.
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn javax.annotation.**

# Mantém classes referenciadas no AndroidManifest.xml
-keep class com.demonc.ytmate.MainActivity { *; }
-keep class com.demonc.ytmate.YTMateApplication { *; }
-keep class com.demonc.ytmate.service.DownloadService { *; }

# Mantém classes Application/Activity/Service que podem ser instanciadas por reflection
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
