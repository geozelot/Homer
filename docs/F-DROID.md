# Homer on F-Droid

F-Droid builds Homer from this repository's source, checks that its build is identical to the APK on
the GitHub release, and then ships **that APK, signed with Homer's own key**. So the copy from
F-Droid, the one from GitHub and the one Obtainium installs are the same app, and anybody can move
between them without reinstalling. F-Droid calls this a reproducible build.

Which releases reach F-Droid is decided by tags: a release goes there once it has a second tag,
`vX.Y.Z-fdroid`. Betas never do.

## How F-Droid's build is matched with the release

1. F-Droid checks out the commit of the `-fdroid` tag and runs `assembleRelease`. There is no
   `keystore.properties` on its build server, so the APK comes out **unsigned** — which is why a
   release build without a keystore is left unsigned rather than signed with the debug key: the
   debug key would make F-Droid's build different from the release.
2. It downloads `homer-X.Y.Z.apk` from the GitHub release, copies that APK's signature onto its own
   build and checks the signature is still valid. That only holds if the two are identical byte for
   byte.
3. If they are, F-Droid publishes the GitHub APK. If not, that version does not reach F-Droid until a
   release that matches; GitHub is not affected either way.

What the build depends on besides the source:

- **The exact commit.** The APK records the commit it was built from
  (`META-INF/version-control-info.textproto`), so a build of any other commit never matches.
- **JDK 17.** CI builds with Temurin 17. A local build with Corretto 17 matched it exactly, so the
  vendor does not matter; another major version might.
- **Gradle and every library** are pinned by the wrapper and the version catalog.

Checked for 2.2.2: the GitHub release APK and a local build of the same commit were identical apart
from the signature.

### Checking a release yourself

Before tagging a release for F-Droid, build its commit without a keystore and compare:

```bash
git checkout v2.2.3
mv keystore.properties keystore.properties.off   # if you have one: the build must be unsigned
./gradlew :app:assembleRelease
python3 scripts/compare-apk.py homer-2.2.3.apk app/build/outputs/apk/release/app-release-unsigned.apk
mv keystore.properties.off keystore.properties
```

`homer-2.2.3.apk` is the APK downloaded from the GitHub release. The script says `IDENTICAL`, or lists
the zip entries that differ.

## Versions

`versionName` and `versionCode` are literals in `app/build.gradle.kts`, because F-Droid reads them
out of that file with a regex and never runs it. The code is `major * 10000 + minor * 100 + patch`:
2.2.3 is `20203`. That always rises, and it sits above the CI run numbers that versioned 2.2.1 and
everything before it (the last was 119), so new releases still install over those.

The release workflow checks a release tag against both literals and refuses to build if either
disagrees. Beta tags and manual runs only override the version *name* (`2.3.0-BETA.131`); their
code stays the literal.

## Cutting a release

1. In the release commit, bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Add the release notes F-Droid shows, one file per language, named for the version code — at
   most 500 characters each:

   ```
   fastlane/metadata/android/en-US/changelogs/20203.txt
   fastlane/metadata/android/de-DE/changelogs/20203.txt
   ```

3. Tag it and push, as before — CI builds, signs and publishes the GitHub release:

   ```bash
   git tag -a v2.2.3 -m "Homer 2.2.3"
   git push origin master v2.2.3
   ```

## Sending a release to F-Droid

Once the GitHub release has its APK (F-Droid downloads it to compare), give the release commit its
second tag and push it:

```bash
git tag -a v2.2.3-fdroid -m "Homer 2.2.3 for F-Droid" 'v2.2.3^{commit}'
git push origin v2.2.3-fdroid
```

CI ignores `-fdroid` tags, so nothing is built or published on GitHub. F-Droid's update check looks
for tags matching `^v[0-9.]+-fdroid$`, finds a higher version code at the newest one, and builds it —
usually within a day or two, after which the update reaches F-Droid users. A release without the
second tag never reaches F-Droid.

## Store listing

The listing comes from `fastlane/metadata/android/` on the commit F-Droid builds:

- `<locale>/title.txt`, `short_description.txt` (at most 80 characters, no full stop) and
  `full_description.txt` (at most 4000; HTML tags such as `<b>`, `<ul>`, `<li>` are allowed),
  in `en-US` and `de-DE`;
- `en-US/images/`: `icon.png`, `featureGraphic.png` and `phoneScreenshots/`, which every language
  falls back to.

The images are rendered on the JVM from the app's real screens with demo books; regenerate them
after a visible UI change:

```bash
./gradlew :app:testDebugUnitTest --tests '*StoreScreenshots*' -Pscreenshots=$PWD/fastlane/metadata/android
```

## The recipe

F-Droid's catalogue lives in [fdroiddata](https://gitlab.com/fdroid/fdroiddata); Homer's entry is
`metadata/com.geozelot.homer.yml`, submitted through a merge request from the branch
`com.geozelot.homer` of a fork. The first submission (2.2.2, signed by F-Droid, from a branch) was
changed at a reviewer's suggestion to the developer-signed builds and `-fdroid` tags described
here, starting with 2.2.3:

```yaml
Categories:
  - Audiobook
License: GPL-3.0-only
AuthorName: geozelot
SourceCode: https://github.com/geozelot/Homer
IssueTracker: https://github.com/geozelot/Homer/issues
Changelog: https://github.com/geozelot/Homer/releases

AutoName: Homer

RepoType: git
Repo: https://github.com/geozelot/Homer.git
Binaries: https://github.com/geozelot/Homer/releases/download/v%v/homer-%v.apk

Builds:
  - versionName: 2.2.3
    versionCode: 20203
    commit: <full commit hash of v2.2.3>
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: ccc94610b984a4c6a4c0ef69524c2371266b0e7ed2ce9e8b31d05b72126d5862

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+-fdroid$
CurrentVersion: 2.2.3
CurrentVersionCode: 20203
```

`Binaries` is where F-Droid downloads the release APK to compare (`%v` is the version name), and
`AllowedAPKSigningKeys` the SHA-256 of Homer's signing certificate — read it from any release with
`apksigner verify --print-certs homer-2.2.3.apk`. The reviewers may adjust the update fields to
their conventions.

Things a reviewer may ask about, and the answers:

- **Anti-features: none.** Every dependency is free software from Google's Maven and Maven
  Central, with no Google Play Services and no tracking. The one service besides the user's own
  Nextcloud is Open Library, an open service, for cover art — off until it is turned on.
- **The in-app updater** is opt-in: nothing contacts GitHub until the user taps *Check now* or
  turns on the daily check, which is off by default. Its section is titled *Updates from GitHub*,
  and the privacy statement explains what the check sends (nothing but the request itself). The
  installer still asks before every install.
- **`MANAGE_EXTERNAL_STORAGE`** is never requested up front; Homer asks for it only if the user
  chooses to keep downloads in a folder of their own (see README, *Permissions*).
- **`-fdroid` tags rather than all tags:** betas are tagged too, and not every release is meant for
  F-Droid. The second tag is the maintainer's explicit "ship this one".

## Things to know

- **Every F-Droid release has to match.** A release whose build differs on F-Droid's server stays
  off F-Droid until one that matches. Check with `scripts/compare-apk.py` before tagging. If a
  difference only appears on F-Droid's side, the likely cause is a different JDK there; the recipe
  can pin JDK 17.
- **F-Droid relies on Homer's signing key now too.** Losing it would end updates on every channel,
  not just GitHub's. Keep the backup.
- **New Gradle versions** reach F-Droid's build server a little after they are released. If a build
  fails there for a Gradle version it does not know yet, that is the reason.
