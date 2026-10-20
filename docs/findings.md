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
| 163 | 2026-10-19 | Opus 5.5 high review of #162 | (1) the invitation's new line suggests the label "Mom (Aegida)", assuming how the owner is addressed (against the Oct 9 rule that guardian text never does); (2) privacy.html says the private name is "your first name followed by a long random part" (not true for channels made before 0.4.1 or names with no plain letters) and how-it-works.html says the name is left out only for names with no Latin letters (also when any letter has no plain form); (3) letters outside the basic Unicode range (two chars) slip past the "never half a name" check ("Ruth𞤀" gives "Ruth"); (4) a title becomes the name ("Dr. Ruth" gives Dr-aegida-). | (1) use her name in the example ("Ruth (Aegida)") per the existing rule; (2) correct both pages; (3) check code points; (4) Kevin to decide (lead recommends skipping common titles). | **Status:** (1)-(3) fixed; not released: the label example is her name (`Texts.Invite.LABEL_EXAMPLE`; none when no name), both pages corrected, the half-name check uses code points; `InviteAndFixTest#theInviteTellsTheGuardianTheyCanGiveTheChannelALabelTheyRecognize` and `#withNoOwnerNameTheLabelLineGivesNoExampleName` failed first, `EndpointTest#aNameWithNoPlainLettersGivesAChannelNameWithoutAName` failed first with an Adlam letter; mutation checks label-example-is-her-name, channel-name-never-half-a-name, invite-signed-by-the-owner caught. (4) Kevin decided (Oct 19): skip common titles before the name; also (Kevin, Oct 19) the Android tap-to-subscribe link sets the channel's display name to "<Name> (Aegida)". Building. |
