# Building Homer

## Requirements

- **Android Studio** (Ladybug or newer) — it bundles the JDK 17 and Android SDK that Homer needs.
  Command-line builds need **JDK 17** and an Android SDK with **API 35** installed, plus a
  `local.properties` pointing at the SDK (`sdk.dir=/path/to/Android/Sdk`). Android Studio writes
  this automatically on first open.
- Gradle is provided by the committed wrapper (`./gradlew`) — do not install Gradle globally.

> Note: this project targets AGP 8.7 / Kotlin 2.0 / Compose. A JDK older than 17 (e.g. system
> JDK 11) **cannot** build it; use Android Studio's bundled JDK or install JDK 17.

## Debug build / run

```bash
./gradlew :app:assembleDebug      # build a debug APK
./gradlew :app:installDebug       # install to a connected device/emulator
```

Or just open the project in Android Studio and Run.

## Release build (signed, sideloaded APK)

Release signing is driven by a **gitignored** `keystore.properties` at the repo root, so no
secrets ever land in git. Until you create it, `assembleRelease` still completes but is signed
with the debug key (not for distribution).

1. Generate a keystore once (keep it safe and backed up — losing it means you can't update the app):

   ```bash
   keytool -genkeypair -v \
     -keystore homer-release.jks \
     -alias homer -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` at the repo root (copy from `keystore.properties.template`):

   ```properties
   storeFile=homer-release.jks
   storePassword=********
   keyAlias=homer
   keyPassword=********
   ```

3. Build the signed release APK:

   ```bash
   ./gradlew :app:assembleRelease
   # → app/build/outputs/apk/release/app-release.apk
   ```

## What P0 contains

Scaffold only: Compose + Material3 UI shell, Hilt DI, Room + Media3 + OkHttp wired as
dependencies, minSdk 26 / targetSdk 35, and the release signing pipeline. It launches to a
themed placeholder screen. Auth, scanning, and playback arrive in later phases (see `SCOPE.md`).
