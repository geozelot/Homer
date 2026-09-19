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
- a **pill on the About row** of the settings menu — "New version" or similar — so the menu says
  where the updater is rather than adding a row of its own above everything.

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

**Where the pill goes.** The existing About row in `ui/settings/SettingsHubScreen.kt` — no new row
above the sections. That row already leads to `ROUTE_SETTINGS_ABOUT`, where `UpdateSection` lives and
the install actually happens, so the pill marks the path rather than duplicating it. `AmberSoft`
(`0x24E3A85A`) is the established fill for an amber highlight.

**Both stay until Homer has actually been updated** — not until the reader has looked. Decided: a
mark that clears on being seen has to remember "seen" somewhere, and one that says the update is
gone when it is not is worse than one that nags. The running version is `UpdateManager.currentVersion`,
so "installed" is observable without storing anything.

**Still to settle when building:** whether `Downloading` and `ReadyToInstall` show it as well as
`Available`. `ReadyToInstall` probably should — the work is done and only a tap is missing.

**Strings.** New, both `values/` and `values-de/`.

---

## Download and install should look like a button

**What.** The updater's "Download and install" is a text-only action where the rest of the app uses a
real button. It should look like one.

**And a sweep with it.** It is unlikely to be the only one left. The question for each is whether the
thing is an *action* — actions get button styling — or a link to somewhere else, which does not. The
repo already has `HomerTextButton` and `SettingsActionPadding`; what it lacks is a check that every
action actually reaches for them.

Starts at `ui/settings/AboutSettingsScreen.kt` (`UpdateSection`, `actionFor`).

---

## Tap feedback that fits the thing being tapped

**What.** Tapping the grid/list view toggle flashes a ripple across a large rectangle around the
buttons rather than on the buttons themselves. Elsewhere the same feedback is bounded much more
tightly to the object. It should be bounded here too.

**Where.** `ViewToggleGroup` / `ViewToggleButton` in `ui/home/LibraryControlBar.kt`. The likely cause
is a `clickable` on a box larger than the pill it contains, with the default unbounded-ish
indication — the group draws its band with `controlGroupPill()` while the tap target is the full
48dp `ControlTapHeight`, so the ripple takes the tap target's shape and not the pill's.

**And a sweep with it.** This is a consistency question, not a one-control bug: the codebase
deliberately separates a control's TAP SIZE from its DRAWN SIZE in several places (`DropdownChip`,
`ArrangeChip`, `ToolButton`, `MiniChapterButton`), and wherever that split exists the indication has
to be told which of the two it belongs to. Worth going through all of them once and deciding the
rule, rather than fixing the one that was noticed.
