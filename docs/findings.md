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
| 156 | 2026-10-18 | Opus 5.5 high review of #155 (627f0c6..7d90477) | An all-Cyrillic brand look-alike (аррӏе.com, xn--80ak6aa92e.com) can be marked "This link is fine": `FindingKey.mayBeAllowed` refuses only MixedScript, but a whole-script homograph is not mixed; the guardian sees only xn*****.com. ST36 accepted it for display reasons, not coaching. | Refuse "This link is fine" when the host has a non-ASCII label (or the brand word appears only after look-alike folding); row + test + mutation check. | **Status:** open; fix next |
| 157 | 2026-10-18 | Opus 5.5 high review of #155 (627f0c6..7d90477) | The popular list is trusted as Majestic ranks it each day: a look-alike that climbs into the top 10,000 becomes "never a look-alike" (facebook-mypage.ru is at 70,427 today). The Oct 18 count (all 203 brand-rule hits are brands' own) is not enforced. | The feed build drops any popular domain LinkSigns flags (BrandLookalike or MixedScript) unless it is on a short reviewed list of brands' own sites; test. | **Status:** open; fix next |
| 158 | 2026-10-18 | Opus 5.5 high review of #155 (627f0c6..7d90477) | A Majestic problem (download failure, layout change, a bad or repeated row in the top 10,000) fails the whole daily feed job, so the scam lists freeze too. | On any Majestic failure, build without --popular (phones fall back to today's rule, PS5) and say so in the job log; test with a broken file. | **Status:** open; fix next |
| 159 | 2026-10-18 | Opus 5.5 high review of #155 (627f0c6..7d90477) | A brand's own name under its popular domain is flagged: LinkDetector judges `front + ".invalid"`, so icloud.apple.com (owned before) becomes icloud.invalid and is a look-alike. | Decide ownership on the real host; use the front part only for the brand words; test. | **Status:** open; fix next |
| 160 | 2026-10-18 | Opus 5.5 high review of #155 (627f0c6..7d90477) | how-it-works.html does not say a site on the 10,000 most-linked list is never a look-alike, and its "Download the public lists" diagram does not name Majestic (CC BY 3.0). | Update the page; the Aegida-Feed NOTICE.txt credit is committed locally (e034b89) and goes with the push. | **Status:** open; fix next |
