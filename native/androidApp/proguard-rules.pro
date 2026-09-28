# R8 rules for the Birdy release build.
#
# Google Play Billing, the Mobile Ads SDK, UMP, AndroidX and kotlinx.serialization all ship
# their own consumer rules inside their AARs/JARs; the rules below only pin what this app relies
# on beyond those, so a future library/R8 upgrade cannot silently break persistence or payments.

# Keep line numbers for readable Play Console crash reports; hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: saved progress (birdy-progress JSON) must round-trip across releases.
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class de.robinrehbein.birdy.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
-keepclasseswithmembers class de.robinrehbein.birdy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.robinrehbein.birdy.**$$serializer { *; }

# Play Billing: AIDL service stubs are looked up by name.
-keep class com.android.vending.billing.** { *; }

# Mobile Ads / UMP mediation and consent classes are instantiated reflectively.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.gms.ads.**
