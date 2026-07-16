# Homer ProGuard/R8 rules.
# Media3, Hilt, Room, Coil and OkHttp ship consumer rules, so they need nothing here.
# kotlinx.serialization needs help: R8 must not strip/rename the generated serializers or the
# fields of the @Serializable models, or the `.homer` manifest/catalog JSON stops (de)serializing.

-dontwarn org.jetbrains.annotations.**

# ── kotlinx.serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the Companion of every @Serializable class (holds serializer()).
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion *;
}
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the generated $$serializer classes and the fields of Homer's serializable models
# (the manifest + catalog live under data.sync); descriptor classes included so field
# names survive shrinking and JSON round-trips unchanged.
-keep,includedescriptorclasses class com.geozelot.homer.**$$serializer { *; }
-keepclassmembers class com.geozelot.homer.data.sync.** {
    *** Companion;
    <fields>;
}

# ── Strip verbose logging from release builds ───────────────────────────────────
# Info/debug/verbose logs (which include server URLs and library paths) are removed by R8;
# warnings and errors are kept. Only applies to minified (release) builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
