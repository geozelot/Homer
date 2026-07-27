# Homer

A **Nextcloud-first Android audiobook player**. Homer streams and manages a private
audiobook library straight from your own Nextcloud over WebDAV — no extra server to
install, no accounts, no Google Play Services, no tracking. Just your books, your
server, and an Audible-class listening experience.

> Homer is built to be self-hosted and standalone. It talks to exactly one thing:
> the Nextcloud you sign in to.

## Features

- **Stream or download** — listen on demand, or take books offline (pause / resume /
  abort downloads, Wi-Fi-only option).
- **Cross-device resume** — your position, bookmarks, finished flags and metadata
  corrections sync through a `.homer` file in your own storage. Three privacy tiers:
  on-device only, private per-account, or a shared library cache for a household.
- **Rich playback** — variable speed, skip-silence, a configurable skip interval,
  optional auto-rewind on resume, a volume boost, a sleep timer with shake-to-extend,
  and bookmarks.
- **Chapters** — multi-file books, embedded MP3 (ID3 CHAP) and MP4/M4B (`chpl`)
  chapters, with a chapter picker.
- **A real library** — cover art (embedded, folder, or opt-in Open Library lookup),
  durations and time-left, series grouping, search, sort and group-by, custom covers,
  and per-book metadata overrides.
- **Candlelit UI** — a warm dark theme, a docked mini-player, and a focused Now Playing
  screen.
- **Private by design** — a scoped Nextcloud app password stored in the Keystore,
  optional biometric app lock, and optional trust-on-first-use certificate pinning.

## How it works

Homer authenticates with **Nextcloud Login Flow v2** to obtain a scoped, revocable app
password (never your real password), stored only in Keystore-backed encrypted storage.
It crawls your library folder over **WebDAV** into a local **Room** index, streams audio
with **Media3/ExoPlayer** over an authenticated OkHttp data source, and keeps cross-device
state in a small `.homer` JSON file in your own Nextcloud storage.

Tech: Kotlin, Jetpack Compose, Hilt, Room, Media3, OkHttp, DataStore, WorkManager.
`minSdk 26`.

## Building

Requires **JDK 17** and the Android SDK. Android Studio (Ladybug or newer) bundles both;
for command-line builds, point `local.properties` at your SDK (`sdk.dir=/path/to/Android/Sdk`).
Gradle comes from the committed wrapper — don't install it globally.

```bash
./gradlew :app:assembleDebug      # build a debug APK
./gradlew :app:installDebug       # install to a connected device / emulator
```

### Signed release APK

Release signing reads a **gitignored** `keystore.properties` at the repo root, so no
secrets land in git. Without it, `assembleRelease` still completes but is signed with the
debug key (not for distribution).

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

### Releases via CI

Pushing a version tag builds a versioned APK and attaches it to the matching GitHub
Release:

```bash
git tag v0.9.0 && git push origin v0.9.0
```

CI signs the build if the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` repository secrets are set; otherwise it falls back to a debug-signed APK.

## Status

Homer is pre-1.0. Everything above works; some features (cross-device tiers 2/3,
biometric lock, M4B chapters, certificate pinning) are best-effort and want verification
across more devices and libraries.

## A note on how this was built

Homer was developed collaboratively with **Claude** (Anthropic) doing the bulk of the
implementation under human direction and review. The commit history reflects that
iterative process.

## License

© geozelot. Licensing is at the author's discretion; see the repository for details.
