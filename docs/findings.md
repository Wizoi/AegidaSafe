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
| 137 | 2026-10-15 | #129 agent report | Urgent warning lines in History (spying app, remote control, special access, admin) stay amber after they close, because History never names the app for urgent kinds (R17), so a closing line cannot say which line it closes. | Needs an app column in History (Room migration 4 to 5) or another way to link a close to its warning; Kevin to decide. | **Status:** open; needs Kevin |
| 139 | 2026-10-15 | Opus 5.5 high re-review of ffe0fe5..d1a90ea | **Real fake pop-ups from a site she allowed are read as scary news (no guardian note)** since #133: a clause about her phone counts as fake only with a STATES word or an order + command word, so "Virus detected! Your phone is at risk", "Your phone is under attack! Tap below to protect it", "Your device is not protected. Download antivirus now" become SiteScaryNews. WP17 has no rows with at risk / under attack / not protected / locked / slow / corrupted. | Rows first; widen to any present-tense claim about her phone/device (is/has been/are + state) outside a question or condition, and "tap below", "download antivirus"; keep the A-rows (reader news) and S-rows as they are. | **Status:** open; fix next |
| 143 | 2026-10-15 | Opus 5.5 high re-review of ffe0fe5..d1a90ea | docs/site/how-it-works.html "Website pop-ups" row does not say that sites she allowed are still checked: fake warnings hidden with a guardian note, scary news hidden with "a scary news story" and no note. | Update the public row (the owner, US spelling, never "safe"). | **Status:** open; fix next |
