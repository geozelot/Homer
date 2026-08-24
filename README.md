# Homer

A **Nextcloud-first Android audiobook player**. Homer streams and manages a private
audiobook library straight from your own Nextcloud over WebDAV — no extra server to
install, no accounts, no Google Play Services, no tracking. Just your books, your
server, and an Audible-class listening experience.

> Homer is built to be self-hosted and standalone. It talks to exactly one thing:
> the Nextcloud you sign in to.


### Pre-build APK

[Go to Releases](https://github.com/geozelot/Homer/releases)

### Staying up to date

Homer can update itself: **Settings → About → Updates**. *Check now* is always available;
the once-a-day automatic check is **off by default**, because it reaches a third party on a
schedule you didn't ask for. Nothing about you or your library is sent — only the question.
Choose **Stable** or **Betas too**, and Homer downloads the release APK and hands it to the
system installer, which still asks you to confirm.

Prefer to keep updates outside the app? [Obtainium](https://github.com/ImranR98/Obtainium)
tracks this repository's releases and installs them for you — point it at the repo URL and
skip Homer's updater entirely.

> A copy you built yourself is signed with your own debug key, so a CI-signed release APK
> can't replace it — Android refuses the install. Homer detects this and says so rather than
> failing cryptically. Uninstall the local build first, or keep building it yourself.


## Features

- **Any Nextcloud folder — or a share link** — point Homer at your own account, or open a
  public share link (`…/s/<token>`) as a read-only or read-write library. Where the books
  come from is independent of where your progress is saved.
- **Stream or download** — listen on demand, or take books offline (pause / resume /
  abort downloads, Wi-Fi-only option).
- **Cross-device resume** — your position, bookmarks and finished flags sync through a
  `.homer` file in your own storage, controlled by two independent switches: *sync my
  progress* (private to your account, or off and device-local) and *share the library
  index* (a shared catalogue of titles, authors, series and cover art so other devices
  reading the same folder skip scanning — and so a title you correct stays corrected
  everywhere). Your listening position is never written to a shared folder.
- **Rich playback** — variable speed, skip-silence, a configurable skip interval,
  optional auto-rewind on resume, a volume boost, a sleep timer with shake-to-extend,
  and bookmarks.
- **Chapters** — multi-file books, embedded MP3 (ID3 CHAP) and MP4/M4B (`chpl`)
  chapters, with a chapter picker.
- **A real library** — cover art (embedded, folder, or opt-in Open Library lookup),
  durations and time-left, search, custom covers, and per-book metadata overrides.
  Three independent controls arrange it: **shelve** by author or genre, show a **series**
  stacked or flat, and **sort** within that.
- **Candlelit UI** — a warm dark theme, a docked mini-player, and a focused Now Playing
  screen.
- **Private by design** — a scoped Nextcloud app password stored in the Keystore, backups
  disabled, optional biometric app lock, and optional trust-on-first-use certificate
  pinning.

## How it works

Homer authenticates with **Nextcloud Login Flow v2** to obtain a scoped, revocable app
password (never your real password), stored only in Keystore-backed encrypted storage.
It crawls your library folder over **WebDAV** into a local **Room** index, streams audio
with **Media3/ExoPlayer** over an authenticated OkHttp data source, and keeps cross-device
state in a small `.homer` JSON file in your own Nextcloud storage.

A crawl reads names, sizes and ETags — one cheap request per folder. A book's *length* is
different: it needs a ranged read of every audio file, so Homer measures a book the first
time you open it rather than doing the whole library up front. If you want them all at
once, *Library source → Measure lengths* does that deliberately, on request.

**Playback in the background is up to your phone, not to Homer.** Homer runs playback as a
foreground service and holds a wake lock while a book is playing, which is everything
Android asks for — but many devices still freeze backgrounded apps to save battery, which
stops the audio until you reopen it. *Settings → Playback → Playing in the background*
shows whether yours is doing that, and opens the system screen to allow it.

Tech: Kotlin, Jetpack Compose, Hilt, Room, Media3, OkHttp, DataStore, WorkManager.
`minSdk 26`.

## Permissions

Homer requests only what it needs, and runs fully on its own app-private storage with no storage
permission at all:

- **`INTERNET`, `ACCESS_NETWORK_STATE`** — reach your Nextcloud, and fail fast when offline.
- **`FOREGROUND_SERVICE` / `…_MEDIA_PLAYBACK` / `…_DATA_SYNC`, `POST_NOTIFICATIONS`** — background
  audio playback and offline downloads that survive the app being closed, each with a notification.
- **`REQUEST_INSTALL_PACKAGES`** — lets the in-app updater hand a downloaded release APK to the
  system installer. Android gates this behind a per-app *install unknown apps* toggle that only you
  can grant, and the installer still asks you to confirm every install. Nothing installs silently,
  and the scheduled check that would find an update is off unless you turn it on.
- **`MANAGE_EXTERNAL_STORAGE`** (all-files access) — **optional and opt-in.** It is *never* requested
  at install or first run. Homer only sends you to the system grant screen if you explicitly choose
  to keep downloads in a folder you pick yourself (Settings → *On this device* → *Browse device*).
  It exists
  because the Android document picker (SAF) is refused on some ROMs — certain LineageOS builds
  return no folder at all — which otherwise leaves no way to use a user-visible, uninstall-surviving
  library folder. Skip that option and the permission stays ungranted. Because of this permission
  Homer is distributed by sideloading / F-Droid rather than Google Play.

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

A tag with a pre-release suffix (`v1.1.0-BETA18`, `v1.2.0-rc1`) is marked as a pre-release, so
`/releases/latest` keeps pointing at the last finished release and the in-app updater's Stable
channel doesn't offer betas. Forks: the repository the updater watches is the `UPDATE_REPO`
field in `app/build.gradle.kts`.

## Status

Homer is at **1.1** — stable for day-to-day listening — with **2.0 in beta**, tagged
`v2.0.0-BETA`. That tag moves: each build replaces it rather than adding another, so there
is one beta release to follow and the APK name carries the actual build (`homer-2.0.0-BETA.42.apk`).

2.0 reworks how the library is indexed and synced, and **changes the shared index format
incompatibly** — a 2.0 device and a 1.x device cannot share a library. Existing shared
catalogs are converted once, automatically.

It's a personal, self-hosted project; a few advanced features (share-link libraries, the
shared library index, M4B chapter text-tracks, certificate pinning) are best-effort and
still evolving. Issues and patches welcome.

## A note on how this was built

Homer was developed collaboratively with **Claude** (Anthropic) doing the bulk of the
implementation under human direction and review. The commit history reflects that
iterative process.

## License

Homer is free software, licensed under the **GNU General Public License v3.0** — see
[LICENSE](LICENSE). © geozelot. You may use, study, share, and modify it under the terms of the
GPLv3; distributed modifications must remain under the same license.
