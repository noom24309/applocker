# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes Exceptions, Signature, InnerClasses

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }

#================================= ADJUST

-keep class com.adjust.sdk.** { *; }
-keep class com.google.android.gms.common.ConnectionResult {
    int SUCCESS;
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
    com.google.android.gms.ads.identifier.AdvertisingIdClient$Info getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
    java.lang.String getId();
    boolean isLimitAdTrackingEnabled();
}
-keep public class com.android.installreferrer.** { *; }
#pangle
-keep class com.bytedance.sdk.openadsdk.** { *; }
#Appsflyer
-keep class com.appsflyer.** { *; }

#tintergal
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.mbridge.** {*; }
-keep interface com.mbridge.** {*; }
-keep interface androidx.** { *; }
-keep class androidx.** { *; }
-keep public class * extends androidx.** { *; }
-dontwarn com.mbridge.**
-keep class **.R$* { public static final int mbridge*; }

#-keep class com.ads.control.admob.** { *; }
#-keep class com.ads.control.ads.** { *; }
#-keep class com.ads.control.ads.bannerAds.* { *; }
#-keep class com.ads.control.ads.nativeAds.** { *; }
#-keep class com.ads.control.ads.wrapper.** { *; }
#-keep class com.ads.control.applovin.** { *; }
#-keep class com.ads.control.billing.** { *; }
#-keep class com.ads.control.config.** { *; }
#-keep class com.ads.control.dialog.** { *; }
#-keep class com.ads.control.event.VioAdmobjust { *; }
#-keep class com.ads.control.event.AperoAppsflyer { *; }
#-keep class com.ads.control.funtion.** { *; }
#-keep class com.ads.control.util.AppConstant { *; }

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-keep, allowobfuscation class com.ads.control.**
-keepclassmembers, allowobfuscation class * { *; }
-keepnames class com.ads.control.**
-keepclassmembernames class com.ads.control.** {
    public <methods>;
    public <fields>;
}
-keep class com.ads.control.ads.bannerAds.** { *; }
-keep class com.ads.control.ads.nativeAds.** { *; }
-keepclassmembers class *.R$ {
    public static <fields>;
}
-keepparameternames