# Add project specific ProGuard rules here.
-keep class org.schabi.newpipe.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }
