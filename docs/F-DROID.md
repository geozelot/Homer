# Homer on F-Droid

F-Droid builds Homer from this repository's source and signs it with F-Droid's own key. Which
versions it builds is decided by one branch, `fdroid-release`: development and betas carry on on
`master`, and a release reaches F-Droid only when that branch is moved to it.

## What is different about F-Droid's build

F-Droid's recipe passes `-Pfdroid` to Gradle. That one property:

- **leaves the in-app updater out.** F-Droid updates its own build, so there is no update section
  under *Settings → About*, no daily check, no paragraph about it in the privacy statement, and no
  `REQUEST_INSTALL_PACKAGES` permission — the updater's manifest entries live in
  `app/src/selfUpdate/AndroidManifest.xml`, which is merged into every build except this one;
- **leaves the release APK unsigned** (`app-release-unsigned.apk`), for F-Droid to sign, rather
  than falling back to the debug key the way a build without `keystore.properties` does.

Everything else is the same code. To build exactly what F-Droid builds:

```bash
./gradlew :app:assembleRelease -Pfdroid
```

## Versions

`versionName` and `versionCode` are literals in `app/build.gradle.kts`, because F-Droid reads them
out of that file with a regex and never runs it. The code is `major * 10000 + minor * 100 + patch`:
2.2.2 is `20202`. That always rises, and it sits above the CI run numbers that versioned 2.2.1 and
everything before it (the last was 119), so the next GitHub release still installs over those.

The release workflow checks a release tag against both literals and refuses to build if either
disagrees, so a tag can never produce a GitHub APK and an F-Droid build of different versions.
Beta tags and manual runs only override the version *name* (`2.3.0-BETA.131`); their code stays
the literal.

## Cutting a release

1. In the release commit, bump `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Add the release notes F-Droid shows, one file per language, named for the version code — at
   most 500 characters each:

   ```
   fastlane/metadata/android/en-US/changelogs/20202.txt
   fastlane/metadata/android/de-DE/changelogs/20202.txt
   ```

3. Tag it and push, as before — CI builds and publishes the GitHub release:

   ```bash
   git tag -a v2.2.2 -m "Homer 2.2.2"
   git push origin master v2.2.2
   ```

## Sending a release to F-Droid

Move `fdroid-release` to the release tag. Only ever fast-forward it, and only to a release tag:

```bash
git push origin v2.2.2^{commit}:refs/heads/fdroid-release
```

F-Droid checks the head of that branch (`UpdateCheckMode: RepoManifest/fdroid-release`), finds a
higher version code there, and builds that commit — usually within a day or two, after which the
update reaches F-Droid users. A release you leave out of the branch never reaches F-Droid; betas
never do.

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

## The first submission

F-Droid's catalogue lives in [fdroiddata](https://gitlab.com/fdroid/fdroiddata). Homer is added
with one file, `metadata/com.geozelot.homer.yml`, through a merge request:

1. Cut the release that will be F-Droid's first (above), and create `fdroid-release` at it.
2. Fork fdroiddata on GitLab, branch `com.geozelot.homer` from `master`, and add the recipe below
   with `commit:` set to the release's full commit hash (`git rev-parse v2.2.2^{commit}`).
3. Optionally check it locally with fdroidserver: `fdroid lint com.geozelot.homer`,
   `fdroid checkupdates --allow-dirty com.geozelot.homer`, `fdroid build com.geozelot.homer`.
   fdroiddata's CI runs the same checks on the merge request.
4. Commit as `New App: com.geozelot.homer` and open the merge request.

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

Builds:
  - versionName: 2.2.2
    versionCode: 20202
    commit: <full commit hash of v2.2.2>
    subdir: app
    gradle:
      - yes
    gradleprops:
      - fdroid

AutoUpdateMode: Version
UpdateCheckMode: RepoManifest/fdroid-release
CurrentVersion: 2.2.2
CurrentVersionCode: 20202
```

Things a reviewer may ask about, and the answers:

- **Anti-features: none.** Every dependency is free software from Google's Maven and Maven
  Central, with no Google Play Services and no tracking. The one service besides the user's own
  Nextcloud is Open Library, an open service, for cover art — off until it is turned on.
- **`MANAGE_EXTERNAL_STORAGE`** is never requested up front; Homer asks for it only if the user
  chooses to keep downloads in a folder of their own (see README, *Permissions*).
- **`RepoManifest/fdroid-release` rather than `Tags`:** every release is tagged, but not every
  release is meant for F-Droid, and betas are tagged too. The branch is the maintainer's explicit
  "ship this one".

## Things to know

- **The two builds do not install over each other.** F-Droid signs with its own key, so moving
  from the GitHub APK to F-Droid's (or back) means uninstalling first. Listening progress comes back
  from Nextcloud if progress sync is on; downloads and device-only settings do not.
- **Reproducible builds are not set up.** They would let F-Droid ship the APK signed with Homer's
  own key instead, so the two could be swapped freely — but only if both were identical, and the
  GitHub build carries the updater F-Droid's leaves out.
- **New Gradle versions** reach F-Droid's build server a little after they are released. If a build
  fails there for a Gradle version it does not know yet, that is the reason.
