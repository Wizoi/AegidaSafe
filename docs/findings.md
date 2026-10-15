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
| 150 | 2026-10-15 | Opus 5.5 high review of 0efb7fd..b3d8037 | **A calm website can be refused as "sent fake warnings":** `fakeOnRecord` treats any stored SiteNotifications finding as a fake, but PopupSpamDetector also makes SiteNotifications for a calm notice while there is no baseline (before setup is done). A news site that posted "New recipe posted" during setup can never be allowed afterwards; she is told it "sent fake warnings", and the guardian, who got the question, never hears the answer. Breaks the must-never "a site without a fake on record can still be allowed". | Count only a finding that had alarming words (record that in its evidence, or do not raise SiteNotifications for calm notices before setup); a test from the real detector with no baseline. | **Status:** open; fix next |
| 151 | 2026-10-15 | Opus 5.5 high review of 0efb7fd..b3d8037 | (a) #146 d left `RoomFindingStore.toFinding` calling DetectorId/Problem/FindingState.valueOf: one finding row of an unknown kind breaks every check, the reminder and Home; (b) the Yes button's choice between the kept-hidden page and none (MainContent.kt) has no test or mutation check; (c) small gaps: no test for an old/malformed listener_checked_at_last_seen value, none for skipping an unknown allow-list row. | (a) skip such finding rows, test; (b) a screen test that taps Yes for a site with and without a fake on record, mutation check; (c) tests. | **Status:** open; fix next |
| 152 | 2026-10-15 | Opus 5.5 high review of 0efb7fd..b3d8037 | docs/site/how-it-works.html still says "Yes remembers the site and the notification comes back" (detailed table) and "If it was you … Aegida remembers" (simple view), not true for a site with a fake warning on record; the "Known edge" note in #149 is wrong (an old question answered Yes after the update still matches, since the detail is unchanged). | Update both page places; correct the #149 note or prove it with a test. | **Status:** open; fix next |
