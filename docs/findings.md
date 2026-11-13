# Findings

This file lists **open** findings only: each still needs work, a check on a real phone, or a decision by Kevin, and its status says what is needed and who acts. Closed findings are in [findings-finished.md](findings-finished.md). A row moves there when it is closed, with its number kept; a new finding takes the next number after the highest in either file (114 at the split, Oct 14).

Every review finding, bug and surprise gets a numbered row here **before** it is fixed. A finding that
lives only in a conversation gets lost and decided twice.

Each row says: what was seen, the premise checked (with a count for any "all" or "none" claim), the test
that failed first, and the commit.

| # | Date | Found by | Finding | Premise checked | Test that failed first | Commit / status |
|---|---|---|---|---|---|---|
| 1 | 2026-10-08 | design | Samsung may put Aegida to sleep after about 3 unopened days (docs only, not yet seen on a device) | From documentation, not run | - | Open: needs a multi-day check on a real Samsung (3, 7 and 16 days unopened; build-plan phase 5): does Aegida keep checking with its foreground notice and listener? Who: Kevin, with a Galaxy he owns or Mom's phone. Until then the guardian's "hasn't checked in" note (#23) is the backstop |
| 2 | 2026-10-08 | design | Samsung Auto Blocker "Maximum restrictions" hides links in messages, so link checks would have nothing to check (docs only) | From documentation, not run | - | Open: needs an unmanaged Galaxy on One UI 6+ with Auto Blocker on Maximum restrictions: does a link in a message still reach Aegida? Who: Kevin, with a real Samsung (build-plan phase 5). If links are hidden, then code: Home must not read as "no known problems found" for link checks |
| 11 | 2026-10-08 | Samsung Remote Test Lab | On the lab phone the Auto Blocker package is **disabled** (`enabled=2`) and the phone is managed by Knox Guard (`com.samsung.android.kgclient` device admin). Auto Blocker is absent from Security and privacy and from More security settings, so the lab **cannot test Auto Blocker** | `dumpsys package` (enabled state), `dumpsys device_policy`, walked both Settings pages | - | Open: the Auto Blocker on/off settings diff still needs an unmanaged Galaxy on One UI 6+ (samsung-validation-options.md option C; the S25 lab phone in #106 had Auto Blocker switched off by the lab's own app). Until then Aegida records only what she says (#89, #95). Who: Kevin, with a real Samsung (build-plan phase 5) |

## From #42 on

Same idea as above, with one result column: how it was checked, what changed, and the status.

| # | Date | Context | Finding | Result: how it was checked, what changed, status |
|---|---|---|---|---|
| 175 | 2026-11-13 | Testers Community report, Fable/Opus review | "Call a guardian" opens an empty keypad when the guardian's phone is blank (optional in setup) | Checked: FixActions.kt:73-77; About step `valid` does not check the phone. Open: release B. K1 decided (Kevin, Nov 13): phone optional, pick from contacts, no call button without a phone; docs/tester-feedback-design.md T4 |
| 176 | 2026-11-13 | Testers Community report, Fable/Opus review | A typo in her name or the guardian's details can only be fixed through Pick a different guardian, which tells the old guardian and makes a new channel; her name cannot be changed at all | Checked: SettingsScreen.kt shows the name only; GuardianChange.kt:24-41; first-run.md FR13 wrongly says the name can be changed in My settings. Open: release B, K2 and K7 decided (Kevin, Nov 13: as recommended), docs/tester-feedback-design.md T5 |
| 183 | 2026-11-13 | Final review of 0.4.3 | The store listing has no screenshot of a warning that needs a tap (design T6); producing one on the emulator needs the daily check to run again after setup | Checked: Tried Nov 13 on Aegida36: a forced job run did not start a new check shortly after setup; the new-apps scenario clears the app first. Open: next emulator scenario run, keep the app (KEEP=1) and capture the warning with a fixture app, never a real brand |
