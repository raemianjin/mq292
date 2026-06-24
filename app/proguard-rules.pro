# WebView + JavascriptInterface keep rules (active only if minify enabled)
-keep class com.myquant.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
