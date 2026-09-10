# Nice to have

Things considered, understood, and deliberately not built. Each one says what it is, why it is not
in, and what it would cost — so the decision does not have to be re-derived the next time somebody
notices the same gap.

Not a roadmap. Nothing here is promised.

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

## Currently-listening as a side rail on a wide window

**What.** On a window with width to spare, move the Currently-listening panel out of the stack above
the library and into a rail down the left — the folded 46dp cover form, stacked vertically — leaving
the grid everything below the control bar.

**Why it is not in.** It was drafted as the answer to the landscape problem and turned out to be the
answer to a different one. Landscape is a *height* problem, and the height fix (fold the panel, merge
the top bar — see `libraryChromeFor`) solved it without a second layout to design and maintain. A
rail spends width, which a landscape phone has plenty of and a tablet has more of.

So it is really a tablet feature, and it belongs beside the two-pane layout rather than in front of
it: on an expanded window the library already shares the screen with a docked player, and a rail on
the other side is the same idea again. Worth doing when tablets get their own pass; not worth a
second shelf layout before then.

**What it would cost.** A vertical arrangement of the shelf, a width threshold that agrees with the
two-pane one, and a decision about what folding means when the rail is always open — `ListeningFold`
has no answer for that, and inventing one for a mode nobody has asked for yet is how a tested
mechanism acquires an untested branch.

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
