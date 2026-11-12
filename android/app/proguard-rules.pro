# kotlinx.serialization ships its own rules; keep our @Serializable models' companions.
-keep,includedescriptorclasses class app.lumentv.data.**$$serializer { *; }
-keepclassmembers class app.lumentv.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.xmlpull.v1.**
