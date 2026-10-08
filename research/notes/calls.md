# Phone-call scam protection a Play-distributed Android app can offer (80+ user, Galaxy S23+, Android 16 / One UI 8.x) — researched 2026-10-07

## 1. Call Screening role: what an app can do, Play policy, conflict with existing Samsung/Google protection, and "don't change defaults" drift detection

### Takeaway
A CallScreeningService app (ROLE_CALL_SCREENING) can silence/reject/block/log-flag calls from numbers NOT in the contact list, and does not need READ_CALL_LOG or default-dialer status. Play's restricted Call Log policy is therefore avoidable for this feature. However, the role is a single-holder "default app" slot, so taking it would displace Samsung's Smart Call/Hiya or Google's caller ID/spam role and would itself be a default change that the drift-detection app must treat as an intentional, whitelisted state.

### Cited Findings
- Call screening is supported on Android 10 (API 29)+; the app can identify calls from numbers not in the address book as potential spam and users can choose to have them silently rejected. — [Android Developers: Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- No READ_CALL_LOG is needed for call screening or caller ID (the Android 10 API removed that requirement). Service must be declared with BIND_SCREENING_SERVICE and the CallScreeningService intent filter. — [Android Developers: Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- onScreenCall() is invoked for new incoming/outgoing calls when the number is NOT in the user's contacts. — [Android Developers: Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- CallResponse options: setDisallowCall (block), setRejectCall, setSilenceCall, setSkipCallLog, setSkipNotification. Blocked-call info stays in the call log so users can see what they missed. — [Android Developers: Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- Call.Details.getCallerNumberVerificationStatus() exposes network-provider verification: PASSED (likely valid), FAILED (likely invalid/spam), NOT_VERIFIED. The page does not name STIR/SHAKEN. — [Android Developers: Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- Play policy: READ_CALL_LOG / WRITE_CALL_LOG / PROCESS_OUTGOING_CALLS (and SMS permissions) are restricted; allowed for the default SMS/Phone/Assistant handler for core function. Non-default apps may get a temporary exception only if there is a listed core function and no alternative; the list includes "Caller ID, spam detection, and spam blocking", "Connected device companions...", "Enterprise management", "emergency SMS". — [Google Play: SMS and Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/9047303)
- Same policy: prohibited uses include call recording and remote device control; undeclared/deceptive use can lead to removal or account termination; phone-call account verification stops qualifying for READ_CALL_LOG from 2027-01-27. — [Google Play: SMS and Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/9047303)
- Samsung's Smart Call is provided by Hiya; enabled in Phone > Settings > "Caller ID and spam protection"; flags suspected fraud/spam, lets user block/report. Features "may vary by country and device." — [Samsung Smart Call page (UK)](https://www.samsung.com/uk/apps/smart-call/)
- Hiya's number-reputation API is not free: premium, requires a signed service agreement; caching/offline use not addressed (must ask Hiya). — [Hiya developer docs](https://developer.hiya.com/docs/guides/voice-protection/number-reputation/get-started)

### Inferences
- Because screening only fires for non-contacts, an app-level policy of "silence/reject non-contacts, let voicemail catch them" is feasible without call-log permissions and is the strongest call-side control for an 80+ user. Contacts she saved (family, doctor) ring normally. (Reasoned from the "not in contact list" behavior above; verify on-device.)
- Android allows one app in the call screening role. Taking the role would replace whichever app (Samsung/Hiya "Smart Call", Google Phone caller ID & spam) holds it today; the old app's spam labelling would stop. Exact role-holder behavior on Samsung (whether Smart Call uses the role vs. internal dialer hooks) was NOT verified. Moving the role is itself a "default change", so Phone Angel's drift detector must (a) record the role holder in its baseline, (b) mark itself as the intended holder, and (c) alert if the role later moves away from it. A lighter alternative that avoids any conflict: do NOT take the role; just verify that Samsung Smart Call / Google spam protection stays enabled (detect drift) and recommend turning it on.
- Recommended design: make call screening an opt-in guardian-chosen mode, not default; default product = monitor + guide.

### Gaps
- Whether Play will treat a pure CallScreeningService app as needing any declaration form (the developer page says no READ_CALL_LOG needed; no explicit Play statement found about the role itself). Could not fetch the RoleManager docs or the CallScreeningService class reference (page truncated in fetch).
- How Samsung's Phone app / Smart Call interacts with a third-party screening role on One UI 8 (no authoritative source found).
- Whether the S23+ Samsung dialer shows the third-party app's labels (no source).

## 2. Android 16 in-call protections and Samsung support

### Takeaway
Android 16 blocks (during a call with a non-contact) enabling "Install unknown apps" and granting accessibility, and Google Play Protect can't be disabled mid-call; screen-share + bank-app warnings are a separate pilot (Android 11+) with named bank/fintech apps. There is no developer API to detect or customize these blocks, and Samsung's rollout/behaviour on One UI 8.x is unconfirmed by primary sources. Samsung ships its own Auto Blocker and (on One UI 8.5) its own Phone-app scam detection.

### Cited Findings
- Google (Android Show I/O Edition, 2025-05-13): on Android 16 users can't enable "Install unknown apps" or accessibility services for any app during a call; services already enabled keep working; block is temporary; protections run on-device and apply only to calls with non-contacts. — [Android Authority](https://www.androidauthority.com/new-anti-scam-features-android-show-3556831)
- Google blog: Play Protect can't be disabled during a call (Android 6+); blocking first-time sideloading from browser/messaging apps during a call and blocking accessibility grants are Android 16 only. — [Google Blog: What's new in Android security & privacy 2025](https://blog.google/security/whats-new-in-android-security-privacy-2025/)
- Dialog title in Beta 2 was "Can't complete action during call"; builds on Enhanced Confirmation Mode (Android 15). Beta-era article; "expected, though not confirmed" for the public release. — [Android Authority: Android 16 phone call protections](https://androidauthority.com/android-16-phone-call-protections-3526068)
- Screen-share + banking warning: when a participating bank app is opened while screen sharing with an unknown contact, a warning offers one-tap end call/stop sharing (Android 11+). Began UK (Monzo, NatWest, Revolut), expanded to Brazil, India, and now US with Cash App and JPMorgan Chase; BleepingComputer says it "continues to run in testing phase" and the popup persists 30 seconds with end-call as the only option. — [Google Blog](https://blog.google/security/whats-new-in-android-security-privacy-2025/); [BleepingComputer](https://www.bleepingcomputer.com/news/security/google-expands-android-scam-protection-feature-to-chase-cash-app-in-us/)
- Google's post mentions no developer API for any of this. — [Google Blog](https://blog.google/security/whats-new-in-android-security-privacy-2025/)
- CONFLICT: A Yahoo/Tech piece says the sideloading toggle block "applies only with Advanced Protection enabled"; Google's own sources say it applies generally on Android 16 during calls with non-contacts. Prefer Google; flag. — [Yahoo Tech](https://tech.yahoo.com/cybersecurity/articles/googles-phone-app-disable-sideloading-170010465.html) vs [Google Blog](https://blog.google/security/whats-new-in-android-security-privacy-2025/)
- Samsung: "AI Scam detection" in the Phone app is "only available on Galaxy phones running Android 16 with One UI 8.5 or higher"; on by default; Settings > Scam detection in Phone app; tone/vibration alert options. — [Samsung US support](https://www.samsung.com/us/support/answer/ANS10012991/)
- One UI 8.5 stable reached Galaxy S23 series (S23, S23+, S23 Ultra): Korea first, then India (2026-05-21), with Europe/SEA/Americas expected next. US timing not confirmed in that source. — [SamMyFans](https://www.sammyfans.com/2026/05/21/one-ui-8-5-global-rollout-begins-for-galaxy-s23-series/)
- Samsung's earlier "voice phishing detection" (AI-voice detection) in the One UI 8 Phone app is South Korea only. — [GSMArena](https://m.gsmarena.com/samsung_adds_voice_phishing_detection_in_the_one_ui_8_phone_app-amp-68943.php)
- Samsung Auto Blocker: blocks installs from outside Galaxy Store/Google Play; default On for devices launched with One UI 6.1.1+; Settings > Security and privacy > Auto Blocker. S23+ upgraded from earlier versions keeps its prior setting. — [Samsung support](https://www.samsung.com/latin_en/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/)
- SamMobile (2025) only said the Android 16 call protections "will likely come to Galaxy" with One UI 8.0; not confirmed. — [SamMobile](https://www.sammobile.com/news/one-ui-8-android-16-save-frauds-scams-thieves/)

### Inferences
- Phone Angel cannot toggle or observe these protections directly; it can only (a) check Settings-visible state it can legally read (e.g., Play Protect on/off via Play Integrity-adjacent signals is NOT available; user-visible guidance instead), (b) show a guardian checklist: Auto Blocker on, Play Protect on, Samsung Scam detection on (if One UI 8.5), spam protection on. Treat these as "verify with guardian once" items rather than app-enforced.
- The in-call blocks only help when the scammer is a non-contact; a spoofed contact number or a scammer who gets her to install before/after the call defeats them. They do not stop remote-control apps already installed from Play (e.g., AnyDesk/TeamViewer QuickSupport are on Play; installing from Play is not "sideloading").
- The bank-app screen-share warning only covers participating apps (named: Monzo, NatWest, Revolut, Cash App, Chase); her own bank may not be covered.

### Gaps
- Exact S23+ One UI 8.x behavior for Android 16 in-call blocks; no Samsung-authored doc found.
- Whether the S23+ in the US received One UI 8.5 by 2026-10 (likely but unverified).
- Which "unknown contact" definition Android uses (contacts-based vs. also recent calls).
- Google Phone scam detection on Samsung devices: unverified (a GSMArena headline suggests S26 "may" support it; not fetched).

## 3. Detecting "on a call while..." events; what can be alerted to guardians; call recording limits

### Takeaway
A normal app can know THAT she is on a call (TelephonyManager/TelephonyCallback call state, requires READ_PHONE_STATE, a runtime permission) but not WHO or WHAT was said. Pairing call state with app-install/launch/accessibility-change signals is feasible for some events; call content/recording is prohibited or blocked. Guardian alerts should be minimal metadata ("on a call for N min; X happened") with her consent.

### Cited Findings
- (From general Android knowledge, unverified because the fetched Android reference page was truncated) TelephonyCallback.CallStateListener (API 31+) reports IDLE / RINGING / OFFHOOK and requires READ_PHONE_STATE; the old PhoneStateListener is deprecated; the callback delivers state, not the number. — [Android Developers: TelephonyCallback.CallStateListener](https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener) (page read was truncated; claims came from fetch-model background, verify)
- Call number access: via CallScreeningService (non-contact calls; no READ_CALL_LOG needed) — [Android Developers: Screen calls](https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls)
- Play: Accessibility API "cannot be requested" for remote call audio recording; cannot be used to bypass platform security/privacy controls; must not be used to block users disabling/uninstalling an app except parental-control authorized by parent/guardian or enterprise admin; only disability-assist apps may set isAccessibilityTool=true; others need in-app disclosure, consent, and Play Console declaration; must prefer narrower APIs. — [Google Play: permissions/APIs policy](https://support.google.com/googleplay/android-developer/answer/16585319)
- Play: Call Log permissions cannot be used for call recording or remote device control; sold/shared data prohibited. — [Google Play policy](https://support.google.com/googleplay/android-developer/answer/9047303)
- Play: QUERY_ALL_PACKAGES restricted; targeted package queries preferred; location is sensitive (request narrowest scope; background needs strong justification). — [Google Play policy](https://support.google.com/googleplay/android-developer/answer/16585319)
- Play: third-party call recording apps were banned from Play (policy update) — [Engadget](https://www.engadget.com/google-is-banning-third-party-call-recording-apps-from-the-play-store-093201443.html) (search result only, 2022 article, not fetched; treat as background)

### Inferences
- Feasible detections without special roles: (1) call state OFFHOOK started/ended; (2) App installed/removed events (package-added broadcasts have restrictions on newer Android; use PackageManager checks on a periodic or foreground basis, with queries limited to a curated list of remote-control apps such as AnyDesk, TeamViewer, RustDesk, "Quick Support" apps — avoids QUERY_ALL_PACKAGES); (3) UsageStats (PACKAGE_USAGE_STATS special access, user grant) to know a curated banking/remote app was foregrounded during a call — this is an elevated permission and Play review will scrutinize it; (4) Accessibility enabled-services list changes (readable via Settings.Secure ENABLED_ACCESSIBILITY_SERVICES without being an accessibility service; the Android 16 call block makes this less likely during calls; unverified API detail).
- Guardian alert content should be metadata only: "Mom was on a call (non-contact, 14 min) while AnyDesk was opened". Do not forward numbers? Number sharing needs consent and disclosure; recommend showing last-4 or "unknown number" and letting her/guardian opt in. Never record, never transcribe. Consent/disclosure UI and a prominent Play data-safety disclosure are required (sensitive data handling).
- Passive-only is key: the app cannot end the call or close other apps; it can raise a full-screen "Stop — hang up and call Mom" card or notification.

### Gaps
- Did not retrieve official text confirming READ_PHONE_STATE classification or Android 12+ changes (page truncated), UsageStats policy on Play, or whether Play requires a declaration for READ_PHONE_STATE (the policy page I retrieved did not address it).
- No source found on whether Samsung Knox/One UI blocks UsageStats or foreground detection for background apps.
- Legal: two-party consent rules for recording are out of scope; recording not proposed.

## 4. Free datasets and licences for scam phone numbers

### Takeaway
The only clearly free, public-domain, daily-updated U.S. source I confirmed is the FTC Do Not Call reported-calls dataset (unverified complaints, includes spoofed numbers, no stated licence on the web page but the data.gov record says us-pd). Commercial reputation data (Hiya, Nomorobo, Truecaller) is paid/contracted and cannot be bundled offline without agreement. FCC Robocall Mitigation Database lists providers, not scam numbers.

### Cited Findings
- FTC DNC Reported Calls Data: complaints about unwanted/robocalls: originating number, complaint date, call time, consumer city/state, subject, robocall flag; "None of the information about the reported calls is verified"; daily CSV (e.g., DNC_Complaint_Numbers_2026-10-06.csv, ~0.3–1.7 MB), updated weekdays ~noon ET; API available since April 2020. Page states no licence. — [FTC DNC Data](https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data)
- data.gov listing for the API: "License: us-pd" (US public domain label 1.0). — [data.gov](https://catalog.data.gov/dataset/do-not-call-dnc-reported-calls-data-api)
- FCC Robocall Mitigation Database: lists voice service providers' certifications; downloadable CSV; not a list of scam numbers. — [FCC RMD](https://www.fcc.gov/node/246205)
- Hiya number reputation API: premium, signed agreement required; not free. — [Hiya developer docs](https://developer.hiya.com/docs/guides/voice-protection/number-reputation/get-started)
- Nomorobo API: sold as prepaid query packages ("No custom contract required to get started"); free status/licence not stated on page; B2B ToS linked but not read. — [Nomorobo API](https://www.nomorobo.com/api/)
- Truecaller API licence addendum (archived copy): limited, non-exclusive, non-transferable; may not redistribute/sublicense; must delete data on termination. Archived copy, current terms may differ. — [Truecaller API License Addendum (archive)](https://web-archive.nli.org.il/National_Library/mp_/https://www.truecaller.com/api-license-addendum)

### Inferences
- A practical offline list: nightly job (server-side or GitHub Action) aggregates the last 30-90 days of FTC DNC numbers, filters to numbers with many complaints and subjects like imposters/tech support, and ships a compact hashed/Bloom-filter list via app update or small CDN file. Caveats: FTC numbers are mostly spoofed and short-lived, so hit rate on a given 80-year-old's incoming calls will be modest (rough, unsourced estimate: low); false positives possible when a spoofed number belongs to a real person. Use as an "amber warning" label, not auto-block.
- Better value than number lists: behaviour-based protection (non-contact screening + hang-up-and-call-back card), because spoofing makes number blocklists weak.
- Do not scrape or embed Hiya/Truecaller/Tellows/Nomorobo data without written contract.

### Gaps
- Tellows API licence, community lists (e.g., Spamhaus-type phone lists, Reddit/GitHub lists), Consumer Sentinel raw data access (Sentinel is law-enforcement only; public datasets are aggregated) — not verified in this pass.
- Actual coverage/hit-rate of FTC data against real calls: no source.
- Exact FTC dataset column names not on the page.

## 5. Senior-targeted scam patterns and 2025-2026 statistics

### Takeaway
Phone remains the dominant contact channel for high-dollar losses among older adults; impersonation (bank/Amazon fraud dept, government, Microsoft/Apple pop-up tech support) drives large losses, and crypto ATMs plus bank transfers are top payment routes. Investment/romance scams are bigger by dollar and largely begin online, so call-side tools address only part of the problem.

### Cited Findings
- FTC 2025 (all ages): 3 million fraud reports, $15.9 billion reported losses (up from 2.6M reports and >$12B in 2024); imposter scams most frequently reported (>1M reports, >$3.5B); investment scams highest loss ($7.9B). — [FTC testimony to Joint Economic Committee, Mar 2026](https://www.ftc.gov/news-events/news/press-releases/2026/03/ftc-testifies-joint-economic-committee-agencys-efforts-combat-fraud)
- FTC Data Spotlight (Aug 2025, adults 60+): reports of $10,000+ losses to business/government impersonation rose from 1,790 (2020) to 8,269 (2024); >$100K losses nearly 7x and combined losses 8x. Patterns: fake bank/Amazon "suspicious activity", "your SSN is tied to a crime" government imposters, fake Microsoft/Apple computer security pop-up with phone number. Contact method for $10K+ losses (2024): phone call 41%, online ad/pop-up 15%, email 13%. Payment: crypto 33% (mostly Bitcoin ATMs), bank transfer 20%, cash 16%, gold ~5% (21% of >$100K). — [FTC Data Spotlight Aug 2025](https://ftc.gov/news-events/data-visualizations/data-spotlight/2025/08/false-alarm-real-scam-how-scammers-are-stealing-older-adults-life-savings)
- FTC (2024 report): adults 60+ reported ~ $2.4B losses in 2024, about 4x 2020 (~$600M); 80+ median individual loss > $1,600 (as stated); tech support about $159M lost by older consumers. — [FTC](https://www.ftc.gov/node/326601)
- FBI IC3 2025 (victims 60+): 201,266 complaints, $7.75B losses (+59%), average $38,500; investment $3.52B; tech/customer support $1.04B; romance/confidence $584M; BEC $568M; recovery scams $540M; crypto ATM 6,188 incidents / $257.5M (+58%); distress ("grandparent") scams >$5M in losses; AI-referenced complaints >3,100 / $352M. Secondary report (HousingWire) of the IC3 data. — [HousingWire](https://www.housingwire.com/articles/fbi-seniors-cybercrime-2025/)
- FTC family-emergency/grandparent scams: caller claims to be a relative in trouble, urgent, voice cloning possible; payment by wire transfer or gift cards; advice: hang up and call the relative at a known number. — [FTC Consumer: Family emergency scams](https://consumer.ftc.gov/scams/family-emergency-scams)
- FBI/IC3 advice (per HousingWire): verify urgent money requests by calling the alleged family member at a known number; scammers often move conversations from text to messaging apps. — [HousingWire](https://www.housingwire.com/articles/fbi-seniors-cybercrime-2025/)

### Inferences — mapping to app-detectable signals
| Scam | Call-side signal | Device-side signal an app might see | Guardian action |
|---|---|---|---|
| Tech support / Microsoft pop-up | Inbound from non-contact, or she calls a number from a pop-up | Remote-control app opened/installed (AnyDesk, TeamViewer); long call | "Never call numbers on pop-ups; Microsoft never calls" card |
| Bank/Amazon fraud dept | Non-contact call, long duration | Bank app opened during call; new payee; large transfer | "Hang up, call number on card" card |
| Government (SSA/IRS/Medicare) | Non-contact, spoof of agency | none beyond call | Education card; these agencies do not demand payment by phone |
| Grandparent/emergency | Non-contact or unknown call; voice clone | Possible payment apps/ gift-card store visit (not detectable) | Family code word; "call me back on known number" |
| Gift cards / crypto ATM | Call during travel to store | Location change during call (sensitive; not recommended) | Educate; consider retail guidance |
| Romance / investment | Mostly text/social/video | Not call-based | Out of call scope |
| Package delivery | Mostly SMS, sometimes call | Link tap | Out of scope for this note |
- Because 41% of $10K+ loss reports began by phone and 33% paid by crypto (largely Bitcoin ATMs), "unknown caller + long call + urgent money" is the highest-value pattern; the device cannot see ATM use, so education and family code word matter more than detection.

### Gaps
- FTC 2025 breakdown by age (60+/80+) not yet published in what I could retrieve; AARP 2025-26 survey statistics not retrieved (search returned local news only).
- HousingWire is a secondary summary of IC3; primary IC3 PDF not read.
- Gift-card statistics for older adults 2025: not found in retrieved sources.
- Data Spotlight does not mention gift cards (explicitly).

## 6. Guardian-facing call-safety features that are feasible

### Takeaway
Feasible with a normal Play app and no special roles: "Hang up and call me" education/overlay card, non-contact guidance (enable Samsung/Google spam protection, voicemail screening, Do Not Disturb rules), emergency quick-dial home widget, and optional (opt-in) CallScreeningService mode.

### Cited Findings
- FTC advice: pause, don't trust the voice, hang up and call the relative at a number you already have, be wary of wire/gift card requests, talk to family beforehand. — [FTC Consumer](https://consumer.ftc.gov/scams/family-emergency-scams)
- Samsung: Smart Call (Hiya) is toggled at Call Settings > "Caller ID and spam protection"; One UI 8.5 adds Phone > Settings > Scam detection (on by default). — [Samsung Smart Call](https://www.samsung.com/uk/apps/smart-call/); [Samsung US support](https://www.samsung.com/us/support/answer/ANS10012991/)
- Auto Blocker lives at Settings > Security and privacy > Auto Blocker. — [Samsung support](https://www.samsung.com/latin_en/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/)
- Play policy requires prominent disclosure/consent for sensitive data and bars Call Log use outside documented core function. — [Google Play policy](https://support.google.com/googleplay/android-developer/answer/9047303)

### Inferences
- Feature list sorted by risk: (1) Safe-call card: persistent notification / full-screen intent when call state goes OFFHOOK from a non-contact: "Don't give codes, money or access. Hang up and call [Kevin] at [number]." (Uses READ_PHONE_STATE; full-screen intent permissions on Android 14+ are restricted for non-calling apps; may degrade to heads-up notification — unverified). (2) Home-screen one-tap "Call Kevin" / "Call 911" shortcuts (ACTION_DIAL avoids CALL_PHONE permission; unverified detail). (3) Guardian-set code word reminder. (4) Drift checks: Smart Call/Scam detection/Auto Blocker/Play Protect on, voicemail configured, default dialer unchanged. (5) Optional non-contact screening via call screening role. (6) Guardian digest: weekly count of blocked/flagged calls (counts only).
- Avoid: recording, transcription, reading SMS, location tracking by default, auto-hangup.

### Gaps
- Full-screen intent and background-start restrictions on Android 16 for non-calling apps: not verified.
- Whether ACTION_CALL for emergency quick dial needs CALL_PHONE under Play policy: not verified (ACTION_DIAL is the safe route).
- Time-limited pending items: Play policy changes in 2026 beyond the account-verification change (Jan 27, 2027) not exhaustively reviewed.
