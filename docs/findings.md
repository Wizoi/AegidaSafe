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
| 197 | 2026-11-24 | Play Console / production | Play pre-launch recommendation (0.4.4): optimized resource shrinking is not enabled (and AGP 9 is suggested) | Checked: gradle.properties has no android.r8.optimizedResourceShrinking; AGP is 8.13.2, where the flag is supported (Android Developers Blog, Sep 2025). Open: 0.4.5, add the flag and check the release build on the emulator; AGP 9 is a separate project. |
| 198 | 2026-11-24 | Play Console / production | Play recommendation (0.4.4): deprecated Window.setStatusBarColor / setNavigationBarColor, from b.p.a | Checked: mapping.txt: b.p is androidx.activity.EdgeToEdgeApi29 (setUp), run only on Android 10-14 by enableEdgeToEdge(); Aegida's code calls neither. Open: 0.4.5, try a newer androidx.activity (1.8.2 today) and see whether Play still flags it. |
| 199 | 2026-11-24 | Play Console / production | Play recommendation (0.4.4): edge-to-edge may not display for all users (targetSdk 36) | Checked: MainActivity calls enableEdgeToEdge(); emulator screens at API 36 portrait are clear of the bars; Aegida is not locked to portrait and landscape with three-button navigation (bar at the side) was never looked at. Open: 0.4.5, emulator check in landscape, fix side insets if needed. |
| 200 | 2026-11-24 | Play Console / production | Aegida is public on Play (production 0.4.4, Nov 24), so Tell a friend and Rate (K4, K8) should show | Checked: Play tracks read back Nov 24: production 18 (0.4.4) completed; PublicPages.LISTED_ON_PLAY is still false. Open: 0.4.5, turn it on (Kevin decided K4/K8; release steps now go straight to production, Kevin Nov 24). |
