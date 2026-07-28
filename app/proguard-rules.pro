# Homer ProGuard/R8 rules.
# Media3, Hilt, Room, Coil and OkHttp ship consumer rules, so they need nothing here.
# kotlinx.serialization needs help: R8 must not strip/rename the generated serializers or the
# fields of the @Serializable models, or the `.homer` manifest/catalog JSON stops (de)serializing.

-dontwarn org.jetbrains.annotations.**

# ── WorkManager ─────────────────────────────────────────────────────────────────
# WorkManager instantiates the InputMerger (default OverwritingInputMerger) by class name via
# reflection for EVERY job. R8 was stripping its no-arg constructor, so every worker failed with
# "Could not create Input Merger" — breaking all background work (downloads, scans, covers) in
# release builds while debug builds worked. Keep the InputMergers' constructors.
-keep class androidx.work.OverwritingInputMerger { <init>(); }
-keep class androidx.work.ArrayCreatingInputMerger { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }

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

# ── Strip only verbose/debug logging from release builds ────────────────────────
# Verbose/debug logs are removed by R8. Info/warning/error are KEPT so the in-app Diagnostics
# screen is useful on release builds too (Homer's own progress/errors survive). Homer's info logs
# can include the server URL + library paths, but logcat is readable only by the app itself on
# modern Android, so this stays on-device.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
