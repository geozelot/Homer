# Next

Agreed work, not yet built. Ordered roughly by when it was asked for.

Different from [NICE-TO-HAVE.md](NICE-TO-HAVE.md), which is the opposite list: things understood
and deliberately **not** being built. Everything here is meant to happen; an item leaves this file
when it ships, or moves to the other one if it turns out not to be worth it after all.

All four of 2.2.0's items have landed — several authors with a primary, filing by surname as a sort
of its own, the fast-scroll lane, and supplementary PDFs. One thing is left over from it, on purpose:

## Drop Jetpack Security once 2.2.0 is everywhere

**What.** Delete `data/auth/LegacyCredentialStore.kt` and the `security-crypto` dependency.

**Why not yet.** 2.2.0 moved credentials onto a Keystore key of Homer's own; its first launch reads
the old `EncryptedSharedPreferences` file once, carries the credentials over and deletes the file.
That reader is the only thing still using the library. Removed too early, a phone that skips 2.2.0
and updates straight to a later release comes back signed out. A release or two after 2.2.0, once
nobody can still be updating from 2.1, it can go.

## Left over from the 2.2.0 release review

Found and understood, not fixed in 2.2.0 because each needs more than a release week allows:

- **A storage move while a downloaded book is playing.** The playlist keeps the old folder's file
  addresses until the book is reloaded, so the next chapter can fail to open. Reload the playing
  book once the move has finished.
- **The resume-position check on opening a book** is meant to give up after five seconds, but the
  request underneath cannot be interrupted, so on a slow server it waits for the connect timeout.
  Needs a cancellable call (OkHttp's coroutine support).
- **Deleting a download while it runs** removes the files before the worker has stopped, so the
  file it was writing can land afterwards. Wait for the worker to finish before deleting.
- **Un-ignoring a folder during a running scan** is undone by that scan's end.
- **Withdrawn chapter cuts after a restart.** A pull notices cuts that were removed only while the
  app still holds the previous copy of the corrections; a restart in between leaves the old chapters.
- Smaller: dialogs that forget their typed text on rotation, and speeds shown with a decimal point
  in German.

---

*Shipped from this list: the gold dot and the About pill for a waiting update; "Download and
install" as a real button, with the sweep that found two more rows whose action was bare text; press
feedback bounded to the control rather than to its tap target, with the sweep that found four more
(`TapFeedback.kt` holds the rule); the fast-scroll lane, which first needed the grid's emission order
turned into a value (`LibraryGridSlots.kt`) so the lane and the list could not disagree; and the
supplementary PDFs a book carries — found by the crawl, read in Homer's own `PdfRenderer` viewer,
and brought along when a book goes offline.*
