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
| 148 | 2026-10-15 | Opus 5.5 high re-review of d1a90ea..0efb7fd | **The website word rules do not converge.** Fourth round, still wrong both ways for sites she allowed: "Your phone is slow? Try these 5 fixes", "Your phone is locked? Here's how to get back in", "AT&T hacked: your phone number is at risk", "your 2025 refund is at risk", "your Chrome passwords are at risk", "Your phone is locked down by Google's new theft protection", "Retailer hacked. Tap here to read more" are called fake (guardian told); "your phone is at high risk / in serious danger / vulnerable / not secure / under threat" are called news (guardian not told); any "your phone is ..." passes the gate ("Your phone is getting Android 17 today" is hidden). | Lead recommends simplifying instead of another word round: for a site she allowed, any notice that passes the scare-word gate is hidden and she is told, and the guardian is told only for sites she did not allow. Kevin to decide. | **Status:** open; Kevin chose the simplification (Oct 15): for a site she allowed, a notice that passes the scare-word gate is hidden and she sees "A website you allowed sent an alarming message. Aegida hid it." (plus the way to stop that site); the guardian is told only for sites she did not allow. Building next. |
