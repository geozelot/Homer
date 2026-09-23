# Next

Agreed work, not yet built. Ordered roughly by when it was asked for.

Different from [NICE-TO-HAVE.md](NICE-TO-HAVE.md), which is the opposite list: things understood
and deliberately **not** being built. Everything here is meant to happen; an item leaves this file
when it ships, or moves to the other one if it turns out not to be worth it after all.

The last of **2.2.0**. Three of the four are done — several authors, filing by surname, and the
fast-scroll lane.

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
install" as a real button, with the sweep that found two more rows whose action was bare text; press
feedback bounded to the control rather than to its tap target, with the sweep that found four more
(`TapFeedback.kt` holds the rule); and the fast-scroll lane, which first needed the grid's emission
order turned into a value (`LibraryGridSlots.kt`) so the lane and the list could not disagree.*
