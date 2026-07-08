# Research: On-device chapter / metadata / cover-art extraction

Spike to de-risk P3 (chapter model). Verified against primary sources (Media3
source on `androidx/media`, Android API reference, Maven Central, library LICENSE
files) as of **July 2026**. This note records the findings that drive decisions;
see `SCOPE.md` §3 and the risk register for how they were applied.

## Headline

**Media3's native MP4/M4B chapter parsing is real but UNRELEASED.** Issue
[#2803](https://github.com/androidx/media/issues/2803) ("Support for MP4 Chapters")
was closed-completed 2026-03-17; it parses **both** Nero `chpl` and QuickTime
chapter tracks (preferring QuickTime) and exposes them format-agnostically as
`androidx.media3.extractor.metadata.Chapter`. But it sits under *"Unreleased
changes"* — latest tag is `1.11.0-beta01` (2026-07-08); **no stable release
contains it**. Do not depend on it until a pinned version's release notes confirm
#2803 is in it.

## MP3 — ID3v2 CHAP / CTOC (fully supported, stable today)

- `androidx.media3.extractor.metadata.id3.ChapterFrame`: `chapterId`, `startTimeMs`,
  `endTimeMs`, `startOffset`, `endOffset`; nested subframes via `getSubFrameCount()` /
  `getSubFrame(int)`.
- `androidx.media3.extractor.metadata.id3.ChapterTocFrame`: `elementId`, `isRoot`,
  `isOrdered`, `String[] children`.
- **Chapter titles are nested** — the title is a `TIT2` `TextInformationFrame`
  *subframe* of the CHAP frame. There is no `getTitle()`; walk subframes and match
  `id == "TIT2"`. TOC order comes from `ChapterTocFrame.children`.
- Read without playback via `androidx.media3.exoplayer.MetadataRetriever`
  → `retrieveTrackGroups()` (async, Guava `ListenableFuture`) → `TrackGroup`
  → `Format.metadata`.

## M4B / MP4 — chapters (the real hole)

- **Conventions that matter:** only two — Nero `chpl` (box under `moov.udta`) and
  QuickTime/iTunes text-track chapters (a timed-text track referenced from the audio
  track via `tref`→`chap`). ffmpeg (thus AAXtoMP3, most tooling) writes **both** by
  default, so many real files carry both; the **QuickTime track is the most reliable
  single target** (survives damage/reprocessing; Media3 prefers it).
- **Android platform APIs read no chapters:** `MediaMetadataRetriever` has no chapter
  key; `MediaExtractor` has no chapter concept. Confirmed.
- **No FOSS library reads M4B chapters cleanly:** jaudiotagger (LGPL, unmaintained,
  AWT in upstream), mp4parser/isoparser (Apache-2.0, reads box tree only — `chpl` is
  an `UnknownBox`), and all TagLib bindings all lack MP4 chapter parsing.

## Cover art + basic tags (both formats, easy)

- Platform `MediaMetadataRetriever.getEmbeddedPicture()` returns compressed image
  bytes for ID3 `APIC` and MP4 `covr`; `extractMetadata()` covers title/author/
  album/albumArtist/composer/writer. No dependency needed at minSdk 26.
- Or Media3 `MediaMetadata` (`artworkData`, `title`, `artist`, `albumArtist`, …).
- **OOM risk:** artwork bytes are full-resolution (1–3 MB common) — decode with
  downsampling to a bounded bitmap before caching.

## WebDAV scan cost — full-file vs ranged reads (drives P2 DataSource design)

- **MP3:** cheap. ID3v2 (text + `APIC`) is at the **front** — read the 10-byte
  header for tag size, then one ranged fetch of that many bytes. No full download.
- **M4B:** depends on `moov` placement. faststart/moov-at-front → a few front ranged
  reads. **moov-at-end (common for audiobooks)** → header walk at front, then a ranged
  read of the tail for `moov`. Chapter title text lives in `mdat` samples addressed by
  `moov` offsets → targeted ranged reads.
- **Requirement:** the WebDAV `DataSource` **must honor HTTP `Range` and expose
  `getSize()`**. Then even moov-at-end files stay cheap (Media3 `Mp4Extractor` seeks
  past `mdat` on a seekable source rather than streaming it). A non-seekable source +
  moov-at-end degrades to a full read, and Media3 throws `EOFException` on
  unknown-length progressive sources.
- Use `Mp4Extractor.FLAG_OMIT_TRACK_SAMPLE_TABLE` when extracting metadata only.
- `MediaMetadataRetriever.setDataSource(...)` is documented time-consuming and can
  crash in native code — run off-main-thread, wrap in try/catch.

## Library reference

| Library | License | Android | Maint. (2026) | ID3 CHAP | MP4 chapters |
|---|---|---|---|---|---|
| media3 (exoplayer/common/extractor) | Apache-2.0 | Yes | active | Yes (stable) | Yes (**unreleased**, #2803) |
| jaudiotagger (Adonai Android fork, JitPack) | LGPL-2.1 | Yes | dormant (2020) | Yes | No |
| mp4parser / `org.mp4parser:isoparser:1.9.56` | Apache-2.0 | Yes | dormant (2022) | n/a | No (box tree only) |
| TagLib bindings (Kyant0/taglib, KTagLib) | Apache-2.0/MIT | Yes | active | n/a | No |

## Decision applied

- **MP3 chapters:** Media3 `MetadataRetriever` on current stable — low risk.
- **M4B chapters:** put chapters behind an interface. Ship a **manual parser bridge**
  (QuickTime chapter track first, Nero `chpl` fallback; mp4parser for box access) so
  we're not blocked on unreleased Media3. Swap to Media3's native `Chapter` API once a
  pinned stable release confirms #2803. This also feeds sidecar/manual tiers 2–3.
- **Bump `media3` to current stable (1.10.x)** from the placeholder 1.4.1 when P3/P4
  touch playback.
- **P2 WebDAV `DataSource` must be Range-capable and length-aware** — non-negotiable
  for cheap M4B scanning and for seek during playback.
