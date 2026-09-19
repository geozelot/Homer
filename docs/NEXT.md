# Next

Agreed work, not yet built. Ordered roughly by when it was asked for.

Different from [NICE-TO-HAVE.md](NICE-TO-HAVE.md), which is the opposite list: things understood
and deliberately **not** being built. Everything here is meant to happen; an item leaves this file
when it ships, or moves to the other one if it turns out not to be worth it after all.

---

## Say it on the settings button when an update is waiting

**What.** When the updater has found a new version, two things point at it:

- a small dot in Homer's gold (`Amber`, `0xFFE3A85A`) in the corner of the **settings button**, so the
  library screen says an update exists without saying anything else;
- a **highlighted entry at the top of the settings menu**, above the first section, that goes
  straight to it.

**Where the signal comes from.** `UpdateManager.state` — a singleton `StateFlow<UpdateState>`
(`data/update/UpdateModels.kt`). The relevant states are `Available`, and arguably `ReadyToInstall`
and `Downloading`. The background check that produces it is `UpdateCheckWorker`, gated on the
`autoCheck` setting, so "auto-update detects a new version" means exactly `state` becoming
`Available` without anybody pressing anything.

`UpdateViewModel` already forwards that state and its own KDoc says a bare `hiltViewModel()` is safe
for it — "a second instance costs nothing, because all the actual state lives in the singleton
`UpdateManager`". So the library screen can read it without going through `HomeViewModel`.

**Where the dot goes — there are TWO settings buttons now**, both in `ui/home/HomeScreen.kt`:

- `TopBar`, the horizontal bar (portrait, tablets);
- `LibrarySideBar`, the turned bar (a phone on its side).

Both need it, and neither should grow a second reason to know about updates — a small
`UpdateDot(visible)` wrapper around the icon, or a badge modifier, keeps it to one rule in one place.

**Where the menu entry goes.** `ui/settings/SettingsHubScreen.kt`, above `set_cat_library` — the
first thing on the page, present only while an update is waiting. It routes to
`ROUTE_SETTINGS_ABOUT`, which is where `UpdateSection` already lives and where the install actually
happens. `AmberSoft` (`0x24E3A85A`) is the established fill for a highlighted row.

**Open questions, worth settling before building rather than during:**

- Does the dot cover `Downloading` and `ReadyToInstall` as well as `Available`? `ReadyToInstall`
  arguably deserves it MORE — the work is done and only a tap is missing.
- Does it clear once the reader has opened settings, or stay until the update is installed? Staying
  is simpler and honest; clearing needs somewhere to remember "seen", and a dot that lies about
  being gone is worse than one that nags.
- Anything on the About row itself in the hub, or only the new top entry? Two highlights on one page
  pointing at the same thing would be one too many.

**Strings.** New, both `values/` and `values-de/`.
