# Homer — v1 Scope & Architecture

A Nextcloud-first Android audiobook player. Streams and manages a private
audiobook library from Nextcloud over WebDAV, with an Audible-class feature set,
background playback, and Android Auto. Locally built, self-signed APK, FOSS-only
stack. Single user.

This document is the living successor to `PROJECT.md`. It records what is decided
and the build order. `PROJECT.md` remains as the original brief.

---

## 1. Decisions locked

From the original brief, confirmed and extended in scoping:

| # | Decision | Choice |
|---|----------|--------|
| Backend | v1 backend strategy | **WebDAV only.** `LibraryBackend` interface + capability flags preserved so Subsonic slots in later without rework. |
| D1 | Unifying position/metadata store | **Central `.homer` index is authoritative.** Self-sufficient offline; portable across future backends. |
| D2 | Metadata precedence | sidecar override **>** embedded tags **>** backend-derived. |
| D3 | Position conflict resolution | **Last-write-wins by timestamp**, ETag optimistic concurrency on the state file. Single user makes this safe. |
| D4 | Subsonic server | **Deferred to v2.** Only viable option is the installed Nextcloud Music app (weak `savePlayQueue`); spike empirically against the real server before committing any code to it. |
| D5 | minSdk | **26.** Clean Keystore + Media3 story; negligible device loss. |
| D6 | Subsonic auth | Moot for v1. Separate config screen when Subsonic arrives. |
| D7 | Desktop scanner | **Out.** Phone-side hashing/parsing is fine at personal scale. |
| D8 | Android Auto | **In from day one.** Shapes the MediaLibraryService + browse tree; cheaper now than retrofit. |
| — | Book identity (v1) | **`path + size + mtime`.** Content-hash (move/rename survival) deferred to a later enhancement — it costs a ranged GET + parse per file at scan time. |
| — | Sync target | Single user, one account, multiple devices. No sharing/multi-user. |

**Why WebDAV-only is the right v1:** every Audible-parity feature sits on Media3
+ the `.homer` index. Subsonic's only unique benefit for audiobooks would be
server-side resume sync, which the `.homer` index already delivers over plain
WebDAV. Its other benefits (transcoding, server search, cover art) are near-zero
value for low-bitrate, sequentially-consumed, small-catalog audiobooks. So the
whole "biggest technical unknown" is removed from the critical path.

---

## 2. v1 feature set (Audible parity)

- Variable playback speed (0.5×–3.0×, per-book memory)
- Chapter navigation (three-tier chapter model, below)
- Cross-device resume (via `.homer` index)
- Sleep timer (fixed durations + "end of chapter" + **shake-to-extend**: a shake while the timer is running or just-elapsed adds another interval)
- Bookmarks (per-book, with optional note, stored in `.homer`)
- Series / author / library organization
- Cover art: sidecar > embedded > backend-derived > **opt-in online lookup** > placeholder
- Offline download (WorkManager, optional encryption at rest)
- Skip-silence (Media3 `SilenceSkippingAudioProcessor`)
- Background playback + lockscreen/notification controls
- **Android Auto** browse + playback

---

## 3. Chapter model (three tiers)

1. **Embedded** — ID3v2 `CHAP`/`CTOC` frames (MP3) or MP4 chapter atoms (M4B) → parse client-side.
2. **Sidecar** — no embedded chapters but `.homer.json` sidecar exists → read offsets.
3. **None** — single long chapter. In-app manual chapter-marking tool writes a sidecar. Tier-3 books flagged visually ("no chapters").

Chapter parsing was spiked — see [docs/research/chapter-parsing.md](docs/research/chapter-parsing.md). Findings:
- **MP3 (ID3 CHAP/CTOC):** fully supported by Media3 `MetadataRetriever` on current
  stable. Titles are `TIT2` subframes (walk subframes; no `getTitle()`). Low risk.
- **M4B/MP4:** the hole. Media3's native MP4 chapter API (`Chapter`, issue #2803) is
  **merged but unreleased** (1.11 betas only), and **no FOSS library reads M4B chapters
  cleanly**. Mitigation: chapters sit behind an interface; ship a **manual parser
  bridge** (QuickTime chapter track first, Nero `chpl` fallback; `org.mp4parser:isoparser`
  Apache-2.0 for box access) and swap to Media3's native `Chapter` once a pinned stable
  release confirms #2803.
- Cover art + basic tags for both formats: platform `MediaMetadataRetriever` /
  Media3 `MediaMetadata` (downsample artwork — 1–3 MB covers risk OOM).

---

## 4. Architecture

### Layering

Pragmatic light module split (solo project — avoid over-modularizing):

- **`:app`** — Compose UI, navigation, DI wiring (Hilt), Android Auto entry.
- **`:core`** — model, DB (Room), settings, DI qualifiers. Pure-ish.
- **`:data`** — auth, network (OkHttp/WebDAV), `LibraryBackend` + `WebDavBackend`, `.homer` metadata store, scan engine, download manager.
- **`:playback`** — Media3 `MediaLibraryService`, session, audio processors, Auto browse tree.

If build times aren't a concern, this can collapse to one module with strict
package boundaries. Start with the split above; it keeps `:playback` and `:data`
independently testable.

### Backend abstraction

```
interface LibraryBackend {
    val capabilities: Capabilities        // providesChapters, providesCoverArt,
                                          // supportsServerScrobble, supportsTranscoding,
                                          // supportsSavePlayQueue, ...
    suspend fun list(path): List<RemoteEntry>
    fun openDataSource(book, offset): DataSource   // ranged GET, auth-injected
    ...
}
```

`WebDavBackend` declares `providesChapters=false, supportsServerScrobble=false,
supportsSavePlayQueue=false` etc. UI and sync behavior read capabilities rather
than assuming parity, so Subsonic can later declare more without UI rewrites.

### Storage / data model

**Room (local, fast, source of truth for live state):**
- `Book` (id, title, author, series, seriesIndex, coverRef, durationMs, backendPath, size, mtime, chapterTier)
- `Chapter` (bookId, index, title, startMs, endMs)
- `PlaybackState` (bookId, positionMs, speed, updatedAt, dirty)
- `Bookmark` (bookId, positionMs, note, createdAt)
- `ScanRecord` (path, etag, mtime, lastSeen) — incremental crawl
- `DownloadState` (bookId, status, localPath, encrypted, bytes)

**`.homer` central index (WebDAV, authoritative, sync layer):**
- One JSON manifest at library root: schema version, per-book identity → {position, speed, bookmarks, overrides, updatedAt}.
- Fetched once on startup, reconciled with Room by timestamp (D3), written back debounced.

**`.homer.json` sidecars (per book):** only overrides/enrichment — chapter marks for single-file MP3s, custom cover, corrected title/author.

**Position write strategy:** live position lives in Room; flushes to the `.homer`
index are **debounced** (on pause / chapter boundary / app background + a periodic
timer) — never per playback tick. Read on foreground; reconcile by timestamp.

### Playback

- `MediaLibraryService` (not just `MediaSessionService`) from day one so Android Auto gets a browsable tree (Library → Series/Authors → Books → Chapters, with correct browsable/playable flags).
- Foreground service type `mediaPlayback` declared (required API 34+).
- `OkHttpDataSource` (Media3 okhttp extension) reusing the authed OkHttp client → ranged GETs for seek within large single files.
- Audio pipeline: `SilenceSkippingAudioProcessor` for skip-silence; speed via `PlaybackParameters`.
- **Shake-to-extend sleep timer:** a lightweight `SensorManager` accelerometer listener, registered only while a sleep timer is armed or within a short grace window after it fires; a threshold-crossing shake resets/extends by the configured interval. Debounced; unregistered otherwise to avoid battery drain.

### Cover art resolution

Resolved lazily per book, cached durably: **sidecar > embedded tag > backend-derived > opt-in online lookup > generated placeholder.**

Online lookup (a privacy-sensitive outbound call — **opt-in or on-demand**, never automatic in the background):
- **Default provider: Open Library** covers (FOSS-aligned, ISBN/title) — matches the project's self-hosted/privacy ethos.
- Alternative providers selectable in **Settings**: **iTunes Search API** (`media=audiobook`, keyless, best audiobook match rate — rewrite `artworkUrl100` to higher resolution) and any future sources.
- Two trigger modes: an opt-in toggle (auto-resolve missing art on scan) *or* per-book on-demand ("Find cover" action). On-demand works even with the toggle off.
- On a hit, persist into the book's `.homer.json` sidecar so it becomes offline-durable and never re-fetches.
- Modeled as a `CoverArtSource` behind a capability flag, same pattern as `LibraryBackend`; the provider list is a settings-driven ordering.

### Auth

Nextcloud **Login Flow v2**: Custom Tab → `/index.php/login/v2` → poll → scoped
app password → Keystore-backed `EncryptedSharedPreferences`/DataStore. WebDAV
requests use it via HTTPS Basic. TLS enforced (`cleartextTrafficPermitted=false`);
opt-in cert pinning; optional `EncryptedFile` for downloaded audio.

---

## 5. Build order

Each phase is independently demoable.

- **P0 — Scaffold.** Gradle + Compose + Media3, minSdk 26, Hilt, Room, OkHttp. Own keystore → `assembleRelease` producing a sideloadable signed APK. Verify the empty app installs and signs.
- **P1 — Auth.** Login Flow v2 + Keystore storage. Milestone: log in to your Nextcloud, store app password, make one authed PROPFIND.
- **P2 — WebDAV + scan.** PROPFIND crawl (depth-controlled, cancellable, resumable) → incremental etag index in Room. Milestone: full library indexed, cheap rescan.
- **P3 — Book detection + chapters.** Folder-as-book heuristics, filename ordering, ambiguous-case flagging. Chapter tiers 1–2 (embedded + sidecar). **Spike M4B/ID3 CHAP parsing here.**
- **P4 — Playback core + Auto.** `MediaLibraryService`, streaming with ranged GET, MediaSession + notification/lockscreen, Android Auto browse tree. Milestone: play a book from the car head unit.
- **P5 — Playback features.** Variable speed, chapter nav, sleep timer, skip-silence, bookmarks.
- **P6 — `.homer` index + resume.** Central manifest read/write, debounced flush, cross-device reconcile. Milestone: resume a book on a second device.
- **P7 — Offline.** WorkManager download, optional encryption at rest, offline-only playback path (index + files + cached art usable with no server).
- **P8 — Library UI.** Compose browse (series/author), cover art display + on-demand "Find cover" action + provider settings (default Open Library), tier-3 manual chapter-marking tool, ambiguous-scan confirmation UI. (The `CoverArtSource` resolution/caching logic lands earlier in P3 with metadata handling; P8 adds the UI, settings, and on-demand trigger.)
- **P9 — Polish + release.** Error states, empty states, settings, signed release build.

---

## 6. Risk register

| Risk | Severity | Mitigation |
|------|----------|------------|
| M4B chapter parsing (no FOSS lib; Media3 native support unreleased) | **High** | Spiked (see research note). MP3 via Media3 now; M4B via manual QuickTime-track + Nero `chpl` parser behind an interface; swap to Media3 `Chapter` API when a pinned stable ships #2803. Tiers 2–3 (sidecar/manual) cover parse failures. |
| PROPFIND performance / Nextcloud quirks on large trees | Med | Depth-controlled, resumable crawl; incremental etag; test against real library early (P2). |
| Seek within large files + cheap M4B metadata scan over WebDAV | Med | **P2 WebDAV `DataSource` must honor HTTP `Range` and expose `getSize()`** — required so moov-at-end M4B files stay cheap (Media3 seeks past `mdat`) and for playback seek. Verify Nextcloud honors `Range` on WebDAV GET early. |
| Media3 pinned at placeholder 1.4.1 | Low | Bump to current stable (1.10.x) when P3/P4 touch playback. |
| Android Auto validation (browse tree, flags, media requirements) | Med | Build `MediaLibraryService` correctly from P4; test on Desktop Head Unit early. |
| `.homer` index write concurrency across devices | Low | Single user + ETag optimistic concurrency + timestamp LWW. |
| Android 14+ foreground-service / background limits | Low | Declare `mediaPlayback` FGS type; standard Media3 session handling. |
| Skip-silence quality | Low | Media3 `SilenceSkippingAudioProcessor` is built-in; expose sensitivity later if needed. |

---

## 7. Explicitly deferred (post-v1)

- Subsonic backend (spike against installed Music app first — D4).
- Content-hash book identity for move/rename survival.
- Companion desktop scanner (D7).
- Multi-user / sharing.
- Cert pinning UI (mechanism in from P1, opt-in).
