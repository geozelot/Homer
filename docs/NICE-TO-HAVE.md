# Nice to have

Things considered, understood, and deliberately not built. Each one says what it is, why it is not
in, and what it would cost — so the decision does not have to be re-derived the next time somebody
notices the same gap.

Not a roadmap. Nothing here is promised — for work that IS meant to happen, see [NEXT.md](NEXT.md).

---

## Pause instead of duck when audio focus is lost briefly

**What.** When another app takes audio focus transiently — a navigation prompt, a notification that
speaks, a timer — Android can either ask us to *duck* (drop to about a fifth of the volume) or to
*pause*. Homer ducks, because that is what ExoPlayer does by default.

For music that is right: the track keeps its shape underneath the interruption. For a book it is
wrong. Speech under speech is not quieter listening, it is words you have to go back for — and going
back is exactly what an audiobook player exists to make unnecessary.

**Why it is not done.** There is no setting for it. `AudioFocusRequest` has
`setWillPauseWhenDucked(true)`, which tells the system to send a full transient loss instead of a
duck request, and Media3 does not expose it. Getting it means building the player with
`handleAudioFocus = false` and owning focus outright: requesting it, pausing on `LOSS` and
`LOSS_TRANSIENT`, resuming on `GAIN` **only if we were the ones who paused**, and keeping all of that
consistent with the media session and the notification.

That replaces well-tested Media3 code on the path every second of playback goes through. The failure
mode is not a visible bug — it is playback quietly failing to resume after a phone call, on some
devices, once. It wants a device and a deliberate afternoon, not a spare ten minutes.

---

## The two-pane layout: library and player side by side

**What.** On a wide window, the library keeps a docked player beside it instead of pushing a player
destination. It was built, shipped in a beta, and taken out again.

**Why it is not in.** It was gated on `screenWidthDp >= 840`, which a large phone clears the moment
its owner picks a smaller display size — so the first person to rotate a real device got a squeezed
player pane on a phone, which is the opposite of what the feature is for. That gate is fixable (the
same `smallestScreenWidthDp` signal the rail now uses tells a phone from a tablet at any density),
but fixing it would only have moved the feature somewhere nobody has looked at it: it has never run
on a tablet.

So it came out rather than being re-gated. A player pane is a real design question — how wide, what
it shows when nothing is playing, whether a rail on one side and a player on the other leaves a grid
worth having between them — and answering it from a threshold in a nav host, sight unseen, is how it
went wrong the first time.

**What it would cost.** Mostly the design. The code is in the history (`2d36c04`, reverted in the
commit that fixed landscape) and was not complicated; what it lacks is a tablet and a decision.

---

## The side rail on a tall wide window — a tablet

**What.** The Currently-listening rail now exists: on a short, wide window (a phone on its side) the
panel goes down the left instead of across the top. What is *not* covered is the other wide window —
a tablet, which is wide **and** tall, and so still stacks the panel above the library.

**Why it is not in.** A tablet has no height problem, and the rail was built to solve one. Turning it
on there is a design question rather than a fix: the stacked panel is not hurting anything, and the
rail would compete for attention with the docked player pane on the opposite side. Two rails and a
grid between them is a layout that wants deciding, not defaulting into.

**What it would cost.** A threshold (`libraryLayoutFor` currently keys the rail on being short), and
an answer for how the rail and the two-pane player share a screen. Worth doing when tablets get their
own pass.

---

## A third library view: covers only

**What.** A view with no info panel under the cards — two per row so the covers are large, with the
usual facts folded into corner marks the way the grid cards already do it.

**Status.** Drafted once and parked by the user: *"I'll come up with a design based on your draft."*
Waiting on that design, not on the code.

---

## The library card's information layout

**What.** How much each card says, and where — title, author, genre, series and collection marks,
progress, download state.

**Status.** Reworked several times and left at *"not super happy with the visual distribution of
info, but it's not bad either."* No specific complaint to fix, so nothing to do until one exists.
Recorded because the next person to feel it should know it is a known feeling and not a new find.

---

## More than PDFs, and a longer view of them

**What.** Two gaps the supplementary-document feature leaves open on purpose.

**Other formats.** `AudioFormats.isDocument` recognises PDF and nothing else. EPUB and CBZ are the
obvious additions and each needs a READER before it needs an extension in that set — listing a
format Homer cannot open would show a button that fails, which is worse than showing none.

**A continuous scroll instead of a pager.** The reader turns one page at a time. A single scroll of
every page is what a desktop PDF reader does, and it is what keeps several full-resolution bitmaps
alive at once; a zoomed A4 page is twenty-odd megabytes. Holding one page is what makes it
affordable to re-render that page at the zoom actually asked for rather than magnifying a blurry
thumbnail, and legible small print is the thing somebody zooms in FOR. Changing this means a
render/recycle budget across visible pages, which is a different piece of work from what is there.

**What it would cost.** Little for a format whose renderer already exists on the device; a real
piece of work for the scroll.
