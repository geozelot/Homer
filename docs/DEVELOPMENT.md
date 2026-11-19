# Developing Homer

How Homer is put together, how to build and test it, and how releases are made. For what Homer does
and how to use it, see the [README](../README.md).

## How it works

Homer authenticates with **Nextcloud Login Flow v2** to obtain a scoped, revocable app password
(never the user's real password), stored only in encrypted storage behind a key in the Android
Keystore. It crawls the library folder over **WebDAV** into a local **Room** index, streams audio
with **Media3/ExoPlayer** over an authenticated OkHttp data source, and keeps cross-device state in
small JSON files in the user's own Nextcloud: a private progress file per account, and the shared
library index under `<library>/.homer/`.

A crawl reads names, sizes and ETags — one cheap request per folder. A book's *length* is different:
it needs a ranged read of every audio file, so it is measured when a book is first opened, or for
every book at once from *Settings → Upkeep → Scan*.

Playback runs as a foreground service and holds a wake lock while a book is playing, which is
everything Android asks for. Devices that still freeze background apps are detected under
*Settings → Playback → Playing in the background*.

Tech: Kotlin, Jetpack Compose, Hilt, Room, Media3, OkHttp, DataStore, WorkManager. `minSdk 26`,
`targetSdk 36`, compiled against 37.

## Building

Requires **JDK 17** and the Android SDK. Android Studio (Ladybug or newer) bundles both; for
command-line builds, point `local.properties` at your SDK (`sdk.dir=/path/to/Android/Sdk`). Gradle
comes from the committed wrapper — don't install it globally.

```bash
./gradlew :app:assembleDebug      # build a debug APK
./gradlew :app:installDebug       # install to a connected device / emulator
```

A copy you built yourself is signed with your own debug key, so a release APK can't be installed
over it — Android refuses. Homer's updater detects this and says so rather than failing
cryptically. Uninstall the local build first, or keep building it yourself.

## Testing

```bash
./gradlew :app:testDebugUnitTest            # unit tests, on the JVM
./gradlew :app:lintDebug                    # Android lint
./gradlew :app:connectedDebugAndroidTest    # Room migration tests — needs a device or emulator
```

The store screenshots are drawn by Robolectric from the app's real screens and are left out of an
ordinary test run; [F-DROID.md](F-DROID.md#store-listing) has the command that renders them.

## Signed release APK

Release signing reads a **gitignored** `keystore.properties` at the repo root, so no secrets land in
git. Without it, `assembleRelease` still completes but leaves the APK unsigned
(`app-release-unsigned.apk`), which Android will not install. That is on purpose: it is the build
F-Droid compares with the signed release (see [F-DROID.md](F-DROID.md)). To try a build on a phone,
use the debug build.

1. Generate a keystore once (back it up — losing it means you can't update the app):

   ```bash
   keytool -genkeypair -v -keystore homer-release.jks \
     -alias homer -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Copy `keystore.properties.template` to `keystore.properties` and fill in real values.

3. Build:

   ```bash
   ./gradlew :app:assembleRelease   # → app/build/outputs/apk/release/app-release.apk
   ```

## Releases

The version is two literals in `app/build.gradle.kts`, `versionName` and `versionCode`
(`major * 10000 + minor * 100 + patch`), bumped in the release commit. Pushing a matching tag builds
the APK and attaches it to the GitHub release; CI refuses a release tag that disagrees with either
literal:

```bash
git tag -a v2.3.0 -m "Homer 2.3.0" && git push origin master v2.3.0
```

Each release commit also adds the notes F-Droid shows, and a section in
[CHANGELOG.md](../CHANGELOG.md). A release reaches F-Droid when it gets a second tag, `v2.3.0-fdroid`
on the same commit; CI ignores those. [F-DROID.md](F-DROID.md) has the details.

CI signs the build if the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`
repository secrets are set. Without them a manual run builds an unsigned APK, and a tag refuses to
publish anything.

A tag with a pre-release suffix (`v1.1.0-BETA18`, `v1.2.0-rc1`) is marked as a pre-release, so
`/releases/latest` keeps pointing at the last finished release and the in-app updater's Stable
channel doesn't offer betas. A beta tag is moved rather than re-cut: each build appends the CI run
number to the version name (`2.3.0-BETA.131`), so the updater sees every build rise, while the
version code stays the literal.

Forks: the repository the updater watches is the `UPDATE_REPO` field in `app/build.gradle.kts`.
