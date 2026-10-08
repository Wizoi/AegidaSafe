# Emulator versus device

The running list of things the emulator cannot show, and what was seen on a real device. A gap that
closes moves back to the emulator list. Section 8 of the technical design is where this comes from.

## Known gaps (check on Kevin's Pixel)

| Gap | Why the emulator can't show it | Checked on Pixel? | Result |
|---|---|---|---|
| Real Play Protect verdict and Play Integrity | Emulator result differs from a real device | No | |
| Heads-up banner look, sound, Do Not Disturb, Android 15/16 sensitive-notification redaction | Needs real SystemUI and real message apps | No | |
| Real messaging app notification shapes (Google Messages, WhatsApp) | No real accounts on the emulator | No | |
| Real Chrome behaviour with real notification-spam sites | Emulator Chrome differs; real spam sites unsafe on an emulator | No | |
| Private DNS guided-flow wording | Settings UI differs by build | No | |
| Real push latency and Doze with the phone idle overnight | Emulator doesn't model Doze or real push | No | |
| Play-installed build, update flow, closed-test opt-in | Needs Play Console | No | |

## Samsung-only (last; see samsung-validation-options.md)

| Item | Checked? | Result |
|---|---|---|
| Sleeping / deep-sleeping apps with a foreground service and notification listener | No | |
| Never-sleeping deep link opens the right screen | Yes, Remote Test Lab S23+ One UI 8.0 (Oct 8) | Works; screen is titled "Never auto sleeping apps" (findings #13) |
| Auto Blocker readable? Maximum restrictions hides links in notifications? | Partly (Oct 8) | Signature-level permissions, no settings key (findings #10); lab phone has Auto Blocker disabled under Knox Guard, so on/off and Maximum restrictions untested (#11) |
| Samsung Internet website-notification behaviour | No | |
| One UI default-apps and Private DNS screen wording | No | |

## Differences seen between emulator and device

(Add a row each time a fixture captured on the emulator differs from the same capture on a device.)

| Date | What | Emulator | Device |
|---|---|---|---|
