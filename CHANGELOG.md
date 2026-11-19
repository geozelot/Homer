# Changelog

What changed in each release of Homer, newest first. Every release, with its APK, is on the
[releases page](https://github.com/geozelot/Homer/releases).

**Betas** run under one moving tag — `v2.2.0-BETA` was the last — replaced by each build rather than
added to, so there is one pre-release to follow. Its title carries the actual build
(`2.2.0-BETA.117`) while the APK keeps a stable name, and the in-app updater offers betas only to
devices on the *Betas too* channel. A beta belongs to the release it leads up to, never the one
behind it: `2.1.0-BETA.90` ranks below `2.1.0`, so once 2.1 shipped, its beta tag was spent and the
next one had to name the release it was working towards.

## 2.2.3

The release Homer goes to F-Droid with. F-Droid builds it from this source, checks that the result
is identical to the APK published on GitHub, and ships that APK, signed with the same key: the
F-Droid copy and the GitHub one are the same app, and you can move between them without
reinstalling. The built-in updater is in both and still does nothing until you use it; its section
in *About* now says that it fetches updates from GitHub.

## 2.2.2

Nothing changed in the app. The version code now follows the version — 2.2.2 is 20202 — instead of
counting CI runs, and still rises above every earlier release, so it installs over them like any
other.

## 2.2.1

A small follow-up. The volume keys control the book whenever Homer is on screen, playing or not;
before, a paused book left them on the ringer. And the Books row under *Upkeep* says what a scan is
doing while it does it — scanning, fetching covers or measuring — with a measuring count that only
includes books there is something left to measure in.

## 2.2

A library you can read at a glance, and books that come with more than audio.

- **Booklets.** The PDFs filed beside a book — a booklet, a libretto, a map — are found by the scan,
  open in Homer's own reader from the details card or from a pill on the player's cover, and go
  offline with the book.
- **Names.** A book can credit several authors, the first one primary, each its own chip; filing by
  surname is a sort of its own, so Pratchett files under P while his name still reads the way it is
  written; and a jump-to-letter lane down the edge of the library follows whatever it is sorted by
  and shows where you are.
- **Your own display size**, apart from the phone's, in *Settings → Browsing*. The *On this device*
  settings are three pages now — Storage, Browsing and Playback — rather than one long one.
- **Ignored folders**, in *Upkeep → Reading folder names*: new additions that still need tidying
  stay out of the library until you say otherwise.
- **A quieter library.** Every item sits on a faint ground of its own, an opened series washes into
  the shelf around it, and list rows carry their marks at the end of the row instead of on a cover
  too small for them.

Underneath: your credentials moved off Jetpack Security, which is deprecated, onto a key of Homer's
own in the Android Keystore. The first launch after updating carries them over, so you stay signed
in. A book's corrections and your own hidden or finished flags now sync on separate clocks, so
hiding a book no longer stops another device's fix to it from arriving. Every dependency is current,
the database migrations are tested on a device, and a deep review fixed, among other things, a scan
that could lose books when it was interrupted, chapters missing from a downloaded M4B first opened
offline, and deleted bookmarks coming back.

The review before release fixed more of the same kind:

- *End of chapter* on the sleep timer works in a book that is one file with chapter marks; it used
  to play on to the end of the book.
- A bookmark tapped in the library opens the file it was made in, and only once — turning the phone
  no longer jumps back to it.
- Moving your downloads to another folder moves them, instead of losing track of them.
- Turning the phone no longer locks the app again or restarts first-run setup.
- Tapping the media notification opens Homer, and play on a headset resumes the last book even when
  Homer was not running.
- Undoing a correction, or removing a chapter cut, now reaches your other devices; chapter cuts no
  longer turn into bookmarks when progress syncs.
- Progress sync can no longer stop for good over a bookmark in a book this device has not scanned.

If more than one of your devices maintains the same shared library, update them together: a 2.1
device does not know about ignored folders and would read them back in.

## 2.1.2

Mostly about a phone held sideways, and about settings that do what they say.

Landscape stops being a portrait screen squeezed: the Currently-listening panel turns ninety degrees
into a rail down one side, and the top bar turns with it — buttons at the head, the wordmark running
up from the foot the way a spine does — so the library gets the height back instead of a sliver of
it. Which layout a window gets is decided by the one measurement that does not move between
orientations, because a large phone at a smaller display size had been reporting tablet numbers and
getting a tablet's layout.

The volume override works. Reduced and boosted both did nothing: every press of play reset the
volume to full, and the loudness boost had never once been applied, because the permission an audio
effect needs was not declared. A seek and a manual chapter skip are now saved when they happen
rather than up to fifteen seconds later, so a process death cannot resume you in the previous
chapter. Chapter steps sit on the mini-player, and dragging away from the scrubber scrubs at half
and then a quarter speed, for finding a sentence in an hour-long chapter.

A waiting update says so — a gold dot on the settings button, a pill on the About row — until it is
actually installed. Actions are drawn as buttons rather than as coloured words, and a press lands on
the control rather than on the invisible target around it.

Underneath: the two largest files became packages, the library's view model handed its work to three
collaborators, and a cancelled request is no longer mistaken for an answer from the server.

## 2.1

Finding your way around a library, and setting one up.

- **Setup is a wizard.** Where the books live, a look at the folder, what was found there and one
  recommendation, then where your listening position is saved. Re-run from settings at whichever
  step answers the question you have — which is what makes moving a private library to a server,
  or adopting a shared one instead of your own, cost nothing beyond onboarding.
- **A shared library can carry rules.** Its owner can require that devices use the shared index
  rather than each crawling the server for themselves, and can keep published edits to themselves.
  Everyone else is told what applies and why, and a read-only reader still keeps their own edits
  locally.
- **The library reorganises**: collections above series, a search box that also filters, path
  templates for folders not laid out `Author / Series / Book`, a details card behind every book and
  shelf, genre chips on items, and a grid whose column count follows the screen.
- **The player states where you are**: the book, what it belongs to, and a chapter list carrying
  each chapter's length and its place in the book — measured off the device itself once a book is
  downloaded, so it holds up offline.

## 2.0

Reworks how the library is indexed and synced. Two things to know before updating from 1.x:

- **The shared index format changed incompatibly.** A 2.0 device and a 1.x device cannot share
  a library, and nothing converts the old catalogue — a 2.0 device rebuilds it from the library
  itself. Update every device that shares a library at roughly the same time.
- **The local database is rebuilt from scratch.** 2.0 resets the schema baseline and carries no
  migrations from the 1.x line, so updating discards the on-device database. Almost everything in
  it is restored on the next sync — resume positions, metadata edits, chapter cuts, the shelf
  itself — and already-downloaded files are re-adopted from disk rather than fetched again.
  **Bookmarks are device-local and are not restored.** Note them down first if you want to keep
  them.
