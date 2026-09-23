# Next

Agreed work, not yet built. Ordered roughly by when it was asked for.

Different from [NICE-TO-HAVE.md](NICE-TO-HAVE.md), which is the opposite list: things understood
and deliberately **not** being built. Everything here is meant to happen; an item leaves this file
when it ships, or moves to the other one if it turns out not to be worth it after all.

The four below are **2.2.0**.

---

## A fast-scroll lane down the library

**What.** A narrow alphabetical lane at the edge of the library that fades in while scrolling and
back out when it stops. Tap or drag a letter to jump there. A setting turns it off.

**Which letters, and when there are none.** The lane's letters are the list's own sort key, not the
title: under sort-by-title it is the title's initial, under sort-by-author the author's. Under
**Recent** and **Duration** an alphabetical lane means nothing, so it does not appear — the
alternative is a control that looks like it navigates and lands somewhere arbitrary. When the
library is shelved (by author, genre, series) the lane targets the SHELF HEADINGS, which is what a
reader is actually aiming at.

Only letters that exist get a slot. A full A–Z with two thirds greyed out is mostly dead targets,
and on a short landscape window there is not room for twenty-six of anything.

**The part that needs deciding before code.** Jumping means `scrollToItem(index)`, and the grid's
item index is not the entry index: `libraryContent` emits a variable number of items per entry (a
header is one, a standalone is one, an open series is several) and the count changes as shelves
open. Computing that mapping a second time, beside the code that emits it, is exactly the kind of
duplicate that drifts.

The fix is to stop emitting straight from `entries`: build a plain list of grid slots first, emit
from it, and let the lane read the same list. One source of truth, and the mapping becomes a pure
function with a test — which is how every other rule in this package is held. It is a real refactor
of `LibraryGrid.kt` and wants its own commit, before the lane.

---

## Several authors, with a primary

**What.** A book can credit more than one author; the first is the one it shelves and sorts under.

**Almost all of this is already decided.** `GenreList.kt` is the same problem solved: genres went
from one to several by storing them newline-delimited **in the same column**, with the first one
primary. Read its header before starting — it argues the whole design, including why order carries
meaning and why a list of one round-trips as the bare string it always was.

Following it means **no Room migration and no change to the `.homer` wire format**: `author` stays a
nullable String in `BookEntity`, `BookOverrideEntity` and the structure facet. An `AuthorList.kt`
mirroring `GenreList.kt` is most of the work.

**What follows from the precedent, so it needs no re-arguing:** the author SHELF sections on the
primary only (otherwise the shelf stops being a partition — counts stop summing to the library and a
reader meets the same book under three headings); an `author:` filter matches ANY of them, because
`valuesFor` already returns a list and matching any is what somebody means.

**What genres did not have to answer:** an older Homer on another device reads the stored string
whole, so a two-author book shows there as one author with a line break in it. Genres made that same
trade silently. Worth deciding deliberately this time — it is the only cross-device cost.

---

## Author sorting by last name

**What.** `Pratchett, Terry` rather than `Terry Pratchett`, with a toggle.

**This is a fix, not a preference.** `unitComparator` sorts `LibrarySort.AUTHOR` on
`it.author?.lowercase()` — the raw stored string — and the author shelf sections on the same value.
So today a library sorted by author is alphabetised by FIRST name: Terry Pratchett files under T.
It reads as an ordering choice rather than a bug, and the shelf headings repeat the name it sorted
by, so it is self-consistently wrong.

It also gates the item at the top of this list: an alphabetical lane is only worth having if its
letters agree with how a reader expects the shelf to be ordered.

**Where the toggle goes — recommendation: Arrange, not Settings.** It changes how the library is
ORDERED, which is precisely what the Arrange field is for, and it belongs beside sort-by-author
rather than on a page you leave the library to reach. The cost is a fourth control in a field built
for three, on a row already tight in landscape — so it may want to be a property of the Author sort
(a second tap cycling the order) rather than a control of its own.

**The hard part is the parsing, and it should not be stored.** "Terry Pratchett" is easy; "Ursula K.
Le Guin", "Jean-Jacques Rousseau", "Hans Christian Andersen", "Cixin Liu" (already surname-first),
"Homer", "The Brothers Grimm" are not, and a wrong guess is worse than no reordering. Suggested
shape: a derived DISPLAY and SORT key computed from the stored name, never written back — so the
stored value stays what the reader typed and a bad guess is a rendering bug rather than data loss.
The per-book override already exists as the escape hatch: type the name the way you want it filed.

---

## Supplementary PDFs

**What.** A PDF sitting beside a book — booklet, libretto, map, score — opened from the player and
from the details card. Found hierarchically: the book's own folder first, then upwards.

**Discovery is nearly free.** The crawl already lists every file in a folder, not just audio: that is
how `BookDetector` finds `cover.jpg`, and how it finds a cover at the book level while the audio
sits in `CD1/`. The same listing yields PDFs, and the same climbing rule gives the hierarchy — a PDF
at the series folder belongs to every book under it, exactly as a cover does. Needs a column on
`BookEntity` (so: the first real Room migration since v2) and a field on the structure facet, or a
derived lookup that re-lists on demand.

**The viewer — recommendation: render in-app with `PdfRenderer`.** It is in the platform from API
21, needs no dependency, and keeps a downloaded booklet readable offline on a plane, which is the
situation this feature is for. Handing the file to another app by intent is less code, but it needs
a downloaded copy anyway, it leaves the app, and it fails on a device with no PDF reader — against
everything else in Homer, which is deliberately standalone.

**What it still needs deciding:** whether the PDF downloads with the book (part of offline) or on
first open; and whether the reader is a full pager with zoom or a page-at-a-time view, which is a
different amount of work by an order of magnitude.

---

*Shipped from this list so far: the gold dot and the About pill for a waiting update; "Download and
install" as a real button, with the sweep that found two more rows whose action was bare text; and
press feedback bounded to the control rather than to its tap target, with the sweep that found four
more (`TapFeedback.kt` holds the rule).*
