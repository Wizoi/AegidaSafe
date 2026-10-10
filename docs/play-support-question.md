# Question for Google Play Developer Support (draft, Oct 9)

Research by a helper agent from web pages it could read; **confirm the policy text in a browser before relying on it**.

## What the policies say (as read)
- **Monitoring / stalkerware** (support.google.com/googleplay/android-developer/answer/10065487): code that collects or sends personal
  or sensitive data without adequate notice and consent, and without a persistent notification, is prohibited. The stated exceptions
  are parental tracking of children and enterprise management; tracking another adult is not allowed "even with that person's
  knowledge and permission" (written about spouses). Allowed monitoring apps must not present themselves as spying, must not hide
  tracking, must show a persistent notification and use a unique icon.
- **User data, prominent disclosure and consent** (answer/9888076): an in-app disclosure shown during normal use (not only in a
  privacy policy), then consent by an affirmative action (back, home or dismissing does not count), before collection starts.
- Not retrieved: the Spyware / Mobile Unwanted Software and Permissions policy texts.

## Why ask
Aegida is self-installed by the owner, consent-based, visible (the always-on notice, normal icon) and she can turn it off. But the
named exceptions are children and enterprise; an adult choosing her own family guardian is not clearly covered. Better to ask before
moving beyond closed testing.

## How
Play Console, Help, "Contact us" / request help, quoting the 19-digit account ID from "About you"
(support.google.com/googleplay/android-developer/answer/13634081). No dedicated "policy question" form was found; enforcement
appeals are separate (the link in an enforcement email). Replies are in English, Chinese, Japanese or Korean.

## Draft message (about 235 words)
Subject: Pre-release policy question: Monitoring / Stalkerware policy and consented family alerts (com.aegida.app)

Hello,

I am an individual developer (Kevin Idzi, US). My app Aegida (com.aegida.app) is in internal and closed testing. It is a free app that
helps people over 80 avoid scams: it checks notifications for scam links and notices spying or remote-control apps on the phone.

One optional feature lets the owner alert ONE family member she chooses (a "guardian") through ntfy.sh when something serious is found.
The consent flow:
- During setup the owner is asked: "Let Aegida tell your guardian about serious problems? Yes / No". Nothing is sent unless she taps Yes.
- She can turn it off at any time in My settings.
- While the feature is on, Aegida shows a persistent notification and the app keeps its normal, visible icon.
- The message contains only fixed sentences with her first name. It never includes message text, whole web addresses, contacts or location. It may name the app a scam message came from and a masked web address (first two letters, stars, ending).

Questions:
1. Does this design comply with the Monitoring / Stalkerware policy, given that the exceptions name parental and enterprise use but this is
   an adult who chooses her own guardian?
2. Is a separate prominent in-app disclosure screen (per the User Data policy) required before the Yes/No consent, or is the setup
   question sufficient?
3. Is any Play Console declaration, Data safety entry or store-listing wording required before I move beyond closed testing?

Account ID: [19-digit ID]. Thank you.

## Decision (Kevin, Oct 9)
Not sending this for now: similar apps are on Play, and Kevin would rather move forward and see whether Play raises anything. The draft stays here. If Play flags the guardian alerts (a policy email, a rejected release, a warning in the Console), send it then, or answer the notice with the same facts: self-installed by the owner, an explicit Yes/No in setup, off at any time, a persistent notice, a normal icon, and fixed sentences that never include message text, links or app names.
