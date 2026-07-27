# Design: Library & Sync Management + Storage Relocation

Status: **DRAFT for review** (2026-07-27). Covers two requested features:

- **B1 — Discovery sweep + a dedicated Library & Sync management screen.**
- **B2 — Relocatable storage** (downloads + device `.homer`) with an optional custom folder
  whose contents survive an app reinstall.

Decisions already locked with the user:

- Storage: **default = app external-files dir**; **custom option = Storage Access Framework (SAF)
  folder** (the only modern-Android mechanism that survives uninstall). Selectable in settings.
- **Design-first**: this document is reviewed before any code.
- The "device `.homer`" question is answered below (§B2.4) as a recommendation.

---

## 0. Interpretation notes

- "Move the app installation to common Android storage" is read as **relocating the app's data**
  (offline downloads, cover cache, and a local `.homer`) out of app-private *internal* storage —
  **not** relocating the APK. The APK install location isn't meaningfully user-controllable
  (`android:installLocation` only nudges SD-card eligibility and is irrelevant here).
- "App library folders" / "syncable app folders" is read as **folders that carry a Homer marker**
  — a `.homer/index.json` (private progress), a `.homer/catalog.json` (shared library), and/or
  audiobook content — discoverable across the server and the device.

---

## B2 — Relocatable storage

### B2.1 Current state

`DownloadStorage` and `CoverCache` both write plain `java.io.File` under `context.filesDir`
(app-private internal storage). `PlaylistResolver` plays downloaded books via `file://` URIs.
Everything there is deleted on uninstall and is not user-visible. There is **no** device-side
`.homer` today — the manifest/catalog live only on the server.

### B2.2 Storage-location model

Introduce a single abstraction, `StorageLocation`, that both the download layer and the cover
cache write through, with two concrete backends:

| Mode | Backing | User-visible | Survives uninstall | I/O API |
|---|---|---|---|---|
| **Default** | `getExternalFilesDir()` (`Android/data/<pkg>/files`) | somewhat | **No** | `File` |
| **Custom** | SAF tree (`content://` from `ACTION_OPEN_DOCUMENT_TREE`) | Yes | **Yes** | `DocumentFile` / `ContentResolver` |

Notes:
- The default moves data off *internal* storage onto shared external storage (bigger, and
  user-reachable via a file manager on most devices), while needing **no** permissions and **no**
  I/O refactor (still `File`).
- The custom mode is opt-in. The SAF permission is persisted
  (`takePersistableUriPermission`). The permission is **lost on uninstall**, but the *data*
  survives; on reinstall the user re-picks the same folder and everything reconnects
  (see §B2.5). This is exactly the requested "rediscoverable when set as home again."

### B2.3 Refactor scope (the real cost)

Moving to a `content://` tree means local file I/O can no longer assume `java.io.File`:

- **`DownloadStorage`** → becomes an interface with `FileStorage` and `SafStorage` impls.
  `fileFor()` returns an opaque handle; add `openOutput()/openInput()/exists()/delete()/size()`.
- **`DownloadWorker`** → writes via the handle's `OutputStream` (already streams bytes; the
  `.part` + atomic-rename dance needs a `DocumentFile`-friendly equivalent — create a temp doc,
  then `rename`/move, or write-in-place with a completion flag in Room).
- **`PlaylistResolver`** → local playback URI becomes the `content://` document URI instead of
  `file://`. ExoPlayer's `DefaultDataSource` already handles `content://` via `ContentResolver`,
  so playback itself is fine.
- **`CoverCache`** → same handle abstraction (or keep covers in internal cache always — they're
  cheap and disposable; only *downloads* + `.homer` truly need to relocate). **Recommendation:
  keep the cover cache in internal storage** to shrink the refactor; only downloads + `.homer`
  move.

### B2.4 The device `.homer` — recommendation

**Recommend: yes, write a local `.homer/index.json` progress mirror into the chosen storage
folder, reusing the existing manifest format and reconcile logic.**

Rationale:
- Room (internal DB) is wiped on uninstall. Tier-1 (on-device-only) users therefore lose *all*
  progress on reinstall today. A local `.homer` in the (custom, persistent) folder fixes that —
  progress becomes part of the "data that survives reinstall" the user asked for.
- It reuses `HomerSyncRepository`'s manifest schema + LWW reconcile almost verbatim, just pointed
  at a local file instead of WebDAV. Low marginal cost.
- For Tier 2/3 the server `.homer` stays authoritative for cross-device; the local mirror is an
  offline copy + reinstall insurance, reconciled the same way.

Mechanics: on every position flush, also write the local mirror. On (re)selecting a folder that
already contains `.homer/index.json`, **import** it into Room via the existing reconcile (LWW) so
a reinstall or a folder brought from another device repopulates progress.

### B2.5 Migration & rediscovery

- **First run after this ships**: existing downloads live in internal `filesDir/downloads`. On
  upgrade, either (a) leave them and let them be re-downloaded into the new default location, or
  (b) one-time move them. **Recommendation: (a)** — simpler, and downloads are re-derivable; mark
  them not-downloaded so the UI reflects reality. (Flag for the user: acceptable to re-download?)
- **Changing location** (default → custom, or custom → new folder): offer to move existing
  downloads, or drop + re-download. Move is nicer but slower; start with drop + re-download and
  add move later.
- **Reinstall + reconnect**: user picks the same SAF folder → app finds `downloads/` +
  `.homer/index.json` there → marks those books downloaded (verifies files exist) and imports
  progress. This is a "rediscover local library" pass.

### B2.6 Risks / verification

- SAF + `DocumentFile` I/O and `content://` playback **cannot be verified here** — needs device
  iteration (this is the biggest untestable surface in the whole app).
- Atomic-write semantics differ on SAF; partial-download detection must move from `.part` files
  to a Room-tracked completion flag.
- Scoped-storage behavior varies by OEM; test on the real LineageOS/A16 device.

---

## B1 — Discovery sweep + Library & Sync management screen

### B1.1 Discovery service

A new `LibraryDiscovery` that enumerates **candidate locations**, each annotated with what was
found, without mutating anything:

**Server-side** (WebDAV PROPFIND, offline-safe via `NetworkMonitor`):
- The account's **files-root** `.homer/` (private progress — Tier 2).
- The configured **library root** and its `.homer/` (shared catalog — Tier 3).
- **Shared-with-me mounts**: PROPFIND the files-root depth-1 for folders, probe each for a
  `.homer/catalog.json` (bounded — top-level only, not a full-tree crawl).

**Device-side**:
- The current storage location's local `.homer/` + `downloads/` (§B2).
- If a custom SAF folder is set, that folder.
- (Not a whole-filesystem scan — scoped storage forbids it and it'd be slow. Discovery is
  marker-based in known/selected locations.)

Each result → a `DiscoveredLibrary` record:
`{ location, kind (server-root|library-root|shared-mount|device), hasIndex, hasCatalog, isOwner,
bookCount, lastSync, tierAvailable }`.

### B1.2 Management screen

A dedicated screen (its own route, reached from the settings gear → "Library & Sync"), replacing
the current opaque sync-tier switch. Sections:

1. **This device** — current storage location (default/custom), path, used space, "Change
   folder…" (SAF picker), local `.homer` status.
2. **Server libraries** — each discovered location as a card: name/path, book count, "shared
   catalog: yes/no", owner, your sync tier here, last sync time, and actions
   (Set as library root / Enable shared catalog / Sync now). **This directly answers "was a
   shared library found and what happens on refresh".**
3. **Sync** — the tier selector, but now *explained by* what discovery found (e.g. "A shared
   catalog exists here, owned by <user> — you can consume it" vs "No shared catalog; you'd create
   one"). Rescan / refresh actions with one-line descriptions of exactly what each does.
4. **Rediscover** — manual button re-running the sweep; shows progress + a timestamped summary.

### B1.3 Data model

- Discovery results are transient (not persisted) — recomputed on open + on Rediscover. Optionally
  cache the last result in DataStore for instant display, refreshed in the background.
- No schema change strictly required for B1; it reads existing Room + probes the server.

### B1.4 Relationship to deferred structural tools

The previously-deferred folder merge/split/reassign + ambiguity queue (task #7) belong on this
same screen later. B1 builds the surface they'll plug into.

---

## Proposed phasing

1. **B1 first** (buildable now, high value, no platform risk): `LibraryDiscovery` + management
   screen + manual rediscover. Makes shared-library state legible immediately.
2. **B2 default relocation** (low risk): `StorageLocation` abstraction, move downloads to
   `getExternalFilesDir()` default, settings entry. No SAF yet.
3. **B2 custom SAF folder + local `.homer` mirror + rediscovery** (the big, device-only-verifiable
   piece): SAF picker, `DocumentFile` I/O, `content://` playback, reinstall-survival import.

Each phase is independently shippable and compile-checked; phase 3 needs device iteration.

## Needs device verification

- All of phase 3 (SAF I/O, `content://` playback, reinstall reconnect).
- Discovery PROPFIND behavior against the real Nextcloud (shared-mount detection, `oc:owner-id`).

## Open questions for the user

1. On upgrade, OK to **drop-and-re-download** existing offline books (simplest), or must they be
   moved in place?
2. Cover cache: OK to **keep covers in internal storage** (not relocated), or should they move too?
3. Management screen entry point: nested under the existing settings sheet, or a top-level
   destination of its own?
