# Aegida — working rules

Short on purpose. Every rule here comes from a lesson in SmugView or TagPup. The reasoning is in
[docs/aegida-technical-design.md](docs/aegida-technical-design.md); what the app does is in
[docs/product-brief.md](docs/product-brief.md); why decisions were made is in
[docs/decisions.md](docs/decisions.md). Findings live in [docs/findings.md](docs/findings.md).

Aegida is an Android app that helps an 80+ parent keep her phone safe and tells her family
"guardians" when something serious happens. The user cannot follow technical prompts. Plain words, big
text, never red, never "safe" (say "no known problems found").

## Where code goes

New code goes where design section 2 puts it. Modules (compile-time layering, imports go down only):

```
:app  ->  :alerts  ->  :detectors  ->  :core
:app  ->  :platform / :store       ->  :core
```

- `:core` is pure Kotlin/JVM. No `android.*`. It holds the models, the user text (`Texts`) and the ports
  (`Clock`, `HttpClient`, `OemSource`, `CheckLog`, ...) that `:platform` and `:store` implement.
- `:detectors` and `:alerts` are pure JVM too and depend only on `:core`, never on `:platform`/`:store`.
  `LayerTest` checks this graph against every `build.gradle.kts`. Guard tests live in `:guards`.
- `:detectors` is pure logic over a `Snapshot`. No `android.*`. Only detectors create `Finding`s.
- `:platform` holds the Android adapters (Clock, PackageSource, SettingsSource, NotificationSource,
  HttpClient, OemSource). `:store` is the only place with Room/SQL.
- `:app` wires and draws. No decisions.
- **Everything Samsung-specific lives only in `OemSource`** and returns `NotPresent` / `Unreadable`
  instead of guessing.

## Rules that keep being broken (each has a guard test that fails the build)

- One owner per concern: time (`Clock`), HTTP (`HttpClient`), ntfy (`NtfySender`), SQL (`:store`),
  notifications posted (`NotificationPoster`), OEM code (`OemSource`).
- **Never fetch a URL taken from a message or notification.**
- **Never say "safe".** Never show a setting as on or fine unless Aegida read it; otherwise "you told me"
  or "can't check".
- Every string a user sees lives in `Problem`/`Texts`. No raw exception text.
- No `AccessibilityService`, SMS permissions, call recording, VPN service, overlays, full-screen intents, or
  the direct battery-exemption permission.
- No secrets in source or the APK. Feed keys live on the sync proxy only.

## Before changing behaviour

- **Name where each side gets its data.** When two things disagree, find both sources first.
- **The second fix for one symptom is a question:** which single place should own this?
- **Check the premise against real data** (an emulator or phone dump, a real feed) before writing a fix. A
  premise about "all" or "none" needs a count over the whole set, recorded in the findings row.
- **Scenarios before code (Kevin, Oct 11).** Before writing a feature or fix, write a scenario table from the problem's
  side, not the code's: who, what happens, what Mom and the guardian should see. It must have must-never rows (a bank
  is never flagged, a scam address never leaves the phone unmasked and never shows as a link in message words), hostile inputs (look-alike letters, labels shaped like web
  addresses), repeats and returns (same notice twice, snooze, repost), failure states (offline, killed half way, clock
  wrong, alerts off) and undo paths (No, Warn me again, change guardian). Put it in the design doc or the finding row.
  An Opus 5.5 high reviewer checks the table for missing rows before coding. Tests come from the table, one per row,
  named after the situation; the final review checks every row has a test and the code handles nothing the table omits.
- **Every fix gets a test that fails on the old code first.** Say that it failed, and how. No `git stash`:
  comment the fix out or use a worktree.
- **A test's data is made by the code that makes the real thing.** Fixtures come from a real device or
  emulator, not from imagination. Dates are relative to now. Fakes behave like the real thing (Room
  `REPLACE` is not append).
- **Design for how it fails first:** offline, killed mid-check, two triggers at once, permission revoked,
  put to sleep by the phone. Decide what Mom sees in each state.
- **Record every review finding in `docs/findings.md` before fixing it.**
- **Ask before one-way choices:** a Room migration, a new default Mom did not choose, changing what
  "alert" or "resolved" means, anything that sends to guardians.

## Keeping the code clean (docs/code-health.md)

- Search before adding a helper, fake, text or test file; reuse what exists. Delete what a change makes unused, in that change.
- No new dependency without Kevin's OK and a check that the artifact really exists with a history (AI-suggested names can be fake).
- Tests assert behaviour or a named rule, never a count of call sites or the shape of the source.
- Guards fail the build on unused texts (`UnusedTextsTest`) and on mutation checks that stop matching (`MutantsMatchTest`).

## Testing: emulator first

- Order of evidence: JVM -> Robolectric -> emulator -> Kevin's Pixel -> (last) a Samsung.
- `scripts/emulator-checks.sh` refuses any serial that is not `emulator-*`.
- The Pixel is only for gaps listed in [docs/emulator-vs-device.md](docs/emulator-vs-device.md). A symptom
  seen on a device is a finding in the same step, with an emulator reproduction tried before it is deferred.
- Samsung-specific checks come last (see `docs/samsung-validation-options.md`).
- Look at every screen in both themes and at 200% font size.
- `python scripts/mutation-check.py` breaks each guarded rule on purpose and requires a test to go red (193 checks in
  `scripts/mutants.json`). Add one when you add a rule worth guarding; it always restores the file.
- Check per-class test times when the suite passes 5 minutes or a class passes 60 s.

## Running things

- Gradle needs the Android Studio JBR. Check for `BUILD SUCCESSFUL`; do not read success from a piped
  command's exit code (the pipe reports `tail`'s code).
- Device-test builds use `-PlocalBuild` so they report `-local` and cannot be mistaken for a Play build.
- Windows shell traps (backslashes in the Bash tool, `adb shell` quoting, line endings): [docs/windows-notes.md](docs/windows-notes.md).

## Working with agents

- Every implementing agent gets a 45-minute wall-clock box and commits or reports by it.
- The lead runs the full suite before any commit. An agent's "green" is a claim.
- Name one owner per shared file in a plan. Commit before launching background agents; keep their work in
  worktrees.

## Before committing or shipping

- Run the full unit suite and the guard tests.
- Scan staged diffs for secrets. One concern per commit; stage by path.
- Publishing to Play, `git push`, and destructive git operations each need explicit confirmation.
- Play requires targetSdk within one year of the latest Android. A rejected upload can still burn its
  versionCode.
