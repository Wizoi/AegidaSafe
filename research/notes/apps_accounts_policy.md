# On-device app/account scam protections: Play policy, feasibility, free data sources (Android 16 / Galaxy S23+, as of 2026-10-07)

Note on method: ~25 tool calls; several fetches returned summarized (not verbatim) policy text. Items marked UNVERIFIED are from background knowledge, not fetched sources. Not legal advice; Play policy pages say the full policy overrides summaries.

## 1. Play policy constraints (QUERY_ALL_PACKAGES, Accessibility, notification listener, usage access, SYSTEM_ALERT_WINDOW, FGS, stalkerware/guardian alerting)

### Takeaway
QUERY_ALL_PACKAGES is explicitly allowed for antivirus-type apps, but only if no narrower method works and a Console declaration is filed; a narrower `<queries>` list is safer for approval. Accessibility should be avoided entirely (monitoring/antivirus apps are explicitly NOT accessibility tools and automation is restricted). Play's stalkerware policy only permits child-monitoring/enterprise apps and states adult-to-adult tracking is not allowed even with consent, so the "guardian alerts" feature must be designed as user-controlled, self-initiated, visible sharing, and its Play acceptability needs a pre-submission check (see Gaps).

### Cited Findings
- QUERY_ALL_PACKAGES: allowed only when broad app visibility is core functionality; antivirus is a listed permitted use; a less intrusive visibility method must be insufficient; must be Console-declared via Permissions Declaration Form; effective for API 30+/Android 11+; deceptive/undeclared use can cause suspension; Google "encourages developers to use security or fraud prevention methods that don't rely on this permission." — [Play: QUERY_ALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/10158779)
- Invalid QUERY_ALL_PACKAGES uses include data for sale, sharing app inventory for analytics/ads, and cases where narrower visibility works. Banking/wallet apps may get it solely for security. — same source.
- Installed-app inventory is classed as "personal and sensitive user data"; requires prominent in-app disclosure + affirmative consent shown during normal use (not only in privacy policy), consent not inferred from back/home, disclosure before permission prompt/collection. Recommended format: "[This app] collects/transmits/... [data] to enable [feature]..." — [Play: User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)
- Accessibility: `isAccessibilityTool=true` only for apps whose primary purpose is helping people with disabilities; "Antivirus software, automation tools, assistants, monitoring apps, cleaners, password managers, and launchers are not accessibility tools." Non-tools need prominent disclosure, affirmative consent, Console declaration with video (API 31+ targets). Prohibited: autonomously initiating/planning/executing actions; changing settings without permission; working around platform security/privacy controls; recording remote call audio; deceptive UI. Use narrower APIs where possible. — [Play: AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491) and [Play: Permissions and APIs policy page](https://support.google.com/googleplay/android-developer/answer/9888170)
- Android 16 blocks granting accessibility access (and enabling install-unknown-apps) during an active phone call (reported on Beta 2; article did not confirm Pixel-only or stable status) — [Android Authority](https://androidauthority.com/android-16-phone-call-protections-3526068)
- VpnService: permitted only if VPN is core functionality or requires a remote server for listed functions (parental control/enterprise, app usage tracking, device security, network tools...); must document in listing, encrypt tunnel, no manipulating traffic for monetization, prominent disclosure for sensitive data. — [Play: Permissions and APIs](https://support.google.com/googleplay/android-developer/answer/9888170)
- SMS/Call Log: READ_SMS/RECEIVE_SMS only for default SMS/Assistant handler; READ_CALL_LOG only for default Phone/Assistant handler; cannot be declared otherwise; cannot derive this data via other APIs; policy page did not address OTP reading; suggests SMS Retriever API. — [Play: SMS and Call Log](https://support.google.com/googleplay/android-developer/answer/9888170#sms-call-log)
- Foreground services: Android 14+ targets must declare a valid FGS type per service plus matching permission, and file a Console declaration; use must be user-beneficial, core, user-perceptible/stoppable, time-limited; `shortService`/`systemExempted` exempt from criteria. — [Play: FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821) (summary via the Permissions policy fetch; the newer pages 16965181 / 16559646 surfaced in search but were not read)
- Device and Network Abuse (quoted items): no code that introduces/exploits vulnerabilities; no circumventing sandbox to derive user activity from other apps; no installing apps without consent; no self-updating or loading executable code outside Play. — [Play: Permissions and APIs / Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888170)
- Stalkerware (Play policy announced Sept 2020, effective Oct 1, 2020): prohibited = code that collects/transmits personal or sensitive data without adequate notice/consent and without a persistent notification; apps presenting as spy/secret surveillance. Allowed monitoring = apps "exclusively designed and marketed for parents to track their children or enterprise management" meeting requirements; other non-stalkerware behavior-monitoring apps allowed if minimum requirements met (no hiding/disguising, persistent notification whenever running, unique identifying icon). Adult-to-adult: apps "cannot be used to track anyone else," such as a spouse, even with knowledge/permission, with or without notification. — [Play: Stalkerware announcement](https://support.google.com/googleplay/android-developer/answer/10065487) (page is a 2020 summary; current full policy text in the Developer Program Policy was not retrieved)
- NotificationListenerService: no Play policy page found that specifically names it; it falls under User Data prominent disclosure/consent and the SMS/OTP-derivation restriction ("may not derive this data through other permissions, APIs"). — inference from [User Data](https://support.google.com/googleplay/android-developer/answer/10144311) and [SMS policy](https://support.google.com/googleplay/android-developer/answer/9888170#sms-call-log)

### Inferences
- Guardian alerting design that most likely stays within policy: the 80+ user's own phone runs the app, the user (not the guardian) enrolls with a clear disclosure naming the guardian, alerts contain only coarse, user-previewable events (e.g., "Play Protect turned off", "new app from unknown source installed", "default browser changed") not content/location/activity logs; persistent notification while monitoring; unique icon; user can see/stop/unpair any time; no hidden mode; no remote control. Frame it as a "security alert sharing" feature, not tracking a person. Even so, an adult-to-adult reporting channel is the highest policy risk.
- Prefer `<queries>` intent filters/specific package lists over QUERY_ALL_PACKAGES; use QUERY_ALL_PACKAGES only if scanning all installed apps for risky permission combos (an antivirus-style core function) is the headline feature, and expect manual review.
- Avoid: AccessibilityService, NotificationListener (possible, but disclosure-heavy and tempting to misuse for OTP), READ_SMS, VpnService, SYSTEM_ALERT_WINDOW (overlay is what banking trojans use; no policy text found to cite).
- Foreground service: likely not needed. A periodic WorkManager scan plus on-demand check avoids the declaration; if a persistent monitor is wanted, choose a type that truthfully fits (UNVERIFIED which; `specialUse` requires justification) and give the persistent notification.

### Gaps
- Current (2026) text of the Stalkerware/Monitoring policy within the Developer Program Policy, and the Usage Access (PACKAGE_USAGE_STATS) and SYSTEM_ALERT_WINDOW and NotificationListener-specific policy sections, were not retrieved. Search the Play Policy Center directly; consider asking Play Developer Support whether "elder-safety alert sharing with a consented family member" is acceptable before building.
- Whether `PACKAGE_USAGE_STATS` needs a Console declaration: UNVERIFIED (background knowledge: it is a special permission user-granted in Settings; Play reviews "usage tracking" under User Data and the VPN/monitoring rules).
- Whether Android 16 call-time protections are Pixel-only vs. all devices including Samsung One UI 8: not confirmed.
- Samsung Auto Blocker details (blocks installs from outside Galaxy Store/Play, blocks USB commands, scans for malware) surfaced in search ([Samsung Knox docs](https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/samsung-auto-blocker/)) but the page was not read; the Samsung US support page returned 404. An Android Authority teardown suggests One UI 8.5 may auto-turn-on Auto Blocker ([link](https://www.androidauthority.com/one-ui-8-5-auto-blocker-auto-turn-on-apk-teardown-3599873)); not verified.

## 2. Detecting malicious / fake-bank apps (signals, Play Protect APIs, Integrity, threat lists, VirusTotal)

### Takeaway
Without Accessibility, an app can reliably check installer source and declared permission combos for installed apps (requires package visibility) and can learn Play Protect state/risk only through the Play Integrity API (needs a backend). The SafetyNet Verify Apps API still documents `listHarmfulApps()` (page updated 2026-03-06, no stated shutdown) but points to Play Integrity. Free threat feeds exist (Echap stalkerware-indicators CC-BY, abuse.ch MalwareBazaar with free Auth-Key), but VirusTotal's free API is non-commercial, 4/min and 500/day.

### Cited Findings
- Play Integrity `playProtectVerdict`: NO_ISSUES (on, clean), NO_DATA (on, not yet scanned), POSSIBLE_RISK (Play Protect OFF), MEDIUM_RISK (potentially harmful apps installed), HIGH_RISK (dangerous apps installed), UNEVALUATED. Requires opting in via Play Console. — [Integrity verdicts](https://developer.android.com/google/play/integrity/verdicts)
- `appAccessRiskVerdict.appsDetected`: KNOWN_/UNKNOWN_ (Play/preloaded vs elsewhere) + INSTALLED / CAPTURING (could view screen) / CONTROLLING (could control device and inputs) / OVERLAYS; verified Play accessibility services excluded; empty if not evaluated (no Play license for user, untrusted device, app version unknown to Play, outdated Play Store); Android 6+, phone/tablet/foldable; remediation dialogs CLOSE_UNKNOWN_ACCESS_RISK / CLOSE_ALL_ACCESS_RISK. — [App access risk](https://developer.android.com/google/play/integrity/app-access-risk), [verdicts](https://developer.android.com/google/play/integrity/verdicts)
- SafetyNet Verify Apps API: `isVerifyAppsEnabled()` (status only), `enableVerifyApps()` (consent dialog), `listHarmfulApps()` returns lastScanTimeMs and list of apkPackageName/apkSha256/apkCategory for the active user; page says Play Integrity "offers several benefits"; no shutdown date on page; page last updated 2026-03-06. — [SafetyNet Verify Apps](https://developer.android.com/privacy-and-security/safetynet/verify-apps)
- Echap stalkerware-indicators: `ioc.yaml` (package names, signing certs, websites, C2 domains/IPs), `watchware.yaml`, `samples.csv`; 174 apps (147 stalkerware, 27 watchware), 3,228 samples; CC-BY 4.0; auto-regenerated on master; maintainers say negative result does not prove clean. — [AssoEchap/stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators)
- abuse.ch MalwareBazaar API: free, requires `Auth-Key` header from abuse.ch auth portal, "fair use"; hash lookup via `query=get_info` with SHA256 (page inconsistent about MD5/SHA1); commercial/for-profit needs may require paid subscription; licensing/CC0 status not confirmed on fetched text. — [MalwareBazaar API](https://bazaar.abuse.ch/api/)
- VirusTotal public API: 4 requests/min, 500/day; "must not be used in commercial products or services"; free community key. — [VT docs](https://docs.virustotal.com/reference/public-vs-premium-api)
- AndroDR / android-sigma-rules: github.com/androdr has no public repositories; the repo name could not be verified and no license found. — [GitHub androdr](https://github.com/AndroDR)
- MVT androidqf is a USB forensic collector under MVT License 1.1 with consent-only use restriction; not an on-device detector. — [androidqf](https://github.com/mvt-project/androidqf)

### Inferences
- Local heuristics available with package visibility: `PackageManager.getInstallSourceInfo()` (non-Play/non-Samsung installers), `requestedPermissions` + `getPackageInfo` flags for combos (BIND_ACCESSIBILITY_SERVICE, SYSTEM_ALERT_WINDOW, RECEIVE_SMS/READ_SMS, BIND_NOTIFICATION_LISTENER_SERVICE, REQUEST_INSTALL_PACKAGES), signing-cert hash match against Echap IOCs, and name lookalikes for bank apps. Whether `requestedPermissions` reveals service bindings requires querying `ServiceInfo` (UNVERIFIED detail). Enabled-accessibility-services list is readable via `AccessibilityManager.getEnabledAccessibilityServiceList` or Settings.Secure without being an accessibility service (UNVERIFIED; commonly used by AV apps).
- Bundle a periodically refreshed (CI-built) package/cert blocklist from Echap into the app rather than doing live lookups; avoids VT limits. Keep CC-BY attribution. A fake-bank-app list is not provided by these sources; ThreatFabric/Cleafy publish reports not machine-readable feeds (not verified here).
- Play Integrity needs a server to decode verdicts (or Google-managed decryption still invoked server-side) and the verdict is per-request about the calling app's environment; usable as a "Play Protect off / harmful apps" signal but adds backend and cost. Cheaper path: guide the user to Play Protect settings and rely on Verify Apps `isVerifyAppsEnabled`/`listHarmfulApps` if still functional on-device (test on an S23+; do not rely on it long term).

### Gaps
- Not fetched: ThreatFabric, Cleafy, Exodus Privacy (ETIP/API terms), MalwareBazaar exact license, VT file-hash upload privacy implications.
- Not confirmed whether `listHarmfulApps()` returns data on Android 16 in practice.
- Play Integrity quota/pricing (free tier daily limits) not retrieved (UNVERIFIED: default 10k/day background knowledge).

## 3. Banking app + remote-access/screen-share combination warnings

### Takeaway
Reliable detection of "screen sharing active while banking app foreground" without Accessibility is only partly feasible. Play Integrity's CAPTURING/CONTROLLING/OVERLAYS verdict is designed for exactly this but evaluates the calling app's own session, so it protects an app you ship, not a third-party bank app. Usage Access can tell which app is foreground; the installed-remote-access-app list is the practical proxy.

### Cited Findings
- App access risk verdict flags running apps that could view screen/control/overlay and is intended to be checked before sensitive user actions in the calling app. — [App access risk](https://developer.android.com/google/play/integrity/app-access-risk)
- Android 16 blocks enabling accessibility access during active calls. — [Android Authority](https://androidauthority.com/android-16-phone-call-protections-3526068)

### Inferences
- Feasible, policy-light approach: (a) maintain a list of known remote-support packages (AnyDesk, TeamViewer QuickSupport, RustDesk, etc.; list compiled by us, not sourced) and warn if installed/recently installed or if the installer is non-Play; (b) with Usage Access (`UsageStatsManager.queryEvents`), detect those packages in foreground near a bank package; (c) `MediaProjection` active state has no public query API (UNVERIFIED), so screen-share detection is heuristic. Usage Access is a special-access permission the user grants in Settings; it is "sensitive device or usage data" per User Data policy so disclosure applies.
- Do not auto-intervene (no overlays or killing apps); show a notification and, via user-initiated flow, a call-the-family-member prompt.

### Gaps
- No source found for a public API reporting active MediaProjection/screen-casting sessions; Samsung-specific (Smart View, Samsung Secure Folder) behavior not researched.
- Google Play Protect "live threat detection" and Android 16 / Samsung scam-detection features beyond what is cited were not researched.

## 4. Play Store / Google account settings: verify vs guide

### Takeaway
Almost none of these can be read by a third-party app; plan to guide with deep links and a checklist. Play Integrity gives the one verifiable signal (Play Protect on/off).

### Cited Findings
- Play purchase verification: options include account password, biometrics, device screen lock; frequency "Always" (default) / every 30 minutes / Never; set in Play > profile > Payments & subscriptions > Purchase verification; the page (India variant) does not say whether an app can read this setting. — [Play purchase verification](https://support.google.com/googleplay/answer/1626831)
- Advanced Protection on Android: requires passkey or FIDO security key; only installs from verified stores (Google Play, manufacturer store); stricter download checks; only Google and verified third-party apps can access Google Account data; page gives no formal eligibility and lists no drawbacks (implied: mandatory key/passkey sign-in, restrictions). — [Google Advanced Protection](https://landing.google.com/advancedprotection/)
- Play Protect state: only via Play Integrity `POSSIBLE_RISK` (off) or Verify Apps `isVerifyAppsEnabled`. — [verdicts](https://developer.android.com/google/play/integrity/verdicts), [Verify Apps](https://developer.android.com/privacy-and-security/safetynet/verify-apps)

### Inferences
- App can verify: Play Protect (via Integrity), whether Play Store/Play services are current-ish, per-app install-unknown-apps via `PackageManager.canRequestPackageInstalls()` only for itself; other apps' install-unknown-apps grants are visible via `AppOpsManager`/Settings only to privileged apps (UNVERIFIED), but apps holding REQUEST_INSTALL_PACKAGES can be listed from manifests.
- App can only guide: purchase authentication, 2-Step Verification, passkeys, Security Checkup, Advanced Protection enrollment (note: AP requires a passkey/security key and may be too heavy for an 80+ user without a helper; weigh against account-lockout risk), recovery phone/email check.

### Gaps
- Not fetched: Google Security Checkup, 2SV, passkey pages; Samsung Knox/Auto Blocker; US-specific Play purchase page (fetched content was India variant).

## 5. Account takeover / SIM swap

### Takeaway
All of this is guidance only (carrier app steps, credit freezes); the app can host a checklist and deep-link, and HIBP's useful email-breach lookup is paid.

### Cited Findings
- Carrier controls (from a 2025-ish consumer article, undated): AT&T "Wireless Account Lock" in the AT&T app (plus passcode); Verizon "Number Lock" + "SIM Protection" + Account PIN per line; T-Mobile "SIM Protection" plus "Account Takeover Protection" (port-out) per line and PIN. — [Techlicious](https://www.techlicious.com/tip/lock-sim-card-sim-swapping/)
- HIBP: Pwned Passwords k-anonymity range API free, no key and no rate limit stated, no attribution required; `/breaches` catalogue appears keyless; `/breachedaccount/{email}`, domain, paste endpoints require a paid API key; breach data CC BY 4.0 requiring attribution. — [HIBP API v3](https://haveibeenpwned.com/API/v3)
- Credit freeze page from FTC failed to load (503). — attempted [FTC credit freezes](https://consumer.ftc.gov/articles/what-know-about-credit-freezes-fraud-alerts)

### Inferences
- A free HIBP option: send users to the website's own email search (manual) or use the Pwned Passwords API only if the app ever checks passwords (unlikely; discouraged for 80+ use case).
- Carrier menu names change; deep-link to the carrier app and confirm in-store.

### Gaps
- FTC credit freeze/fraud alert facts (free at Equifax/Experian/TransUnion; guardian/protected-consumer freeze) not verified in this session; FCC SIM-swap/port-out rules not researched; Samsung/carrier support pages not fetched.

## 6. What NOT to attempt (summary with support)

### Takeaway
Stay out of SMS/OTP reading, call recording, accessibility automation, VPN inspection, overlays, and privileged/ADB/root techniques; each is either policy-restricted for non-default-handler apps or flagged as the malware behavior being defended against.

### Cited Findings
- READ_SMS/RECEIVE_SMS/READ_CALL_LOG only for default handlers; cannot derive the data via other APIs. — [SMS policy](https://support.google.com/googleplay/android-developer/answer/9888170#sms-call-log)
- Accessibility cannot record remote call audio, auto-act autonomously, or circumvent platform privacy controls; monitoring/AV apps not accessibility tools. — [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- VpnService limited to listed core functions with encryption and disclosure. — [Permissions and APIs](https://support.google.com/googleplay/android-developer/answer/9888170)
- Apps may not circumvent sandbox to derive activity from other apps or install apps without consent. — [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888170)

### Inferences
- Overlays (SYSTEM_ALERT_WINDOW): no Play policy text found, but it is the core banking-trojan technique and Google's own verdict treats overlays as risk; avoid.
- Root/ADB tricks conflict with Device and Network Abuse and Integrity (device integrity verdict) and would fail on a normal S23+ anyway.

### Gaps
- Call recording policy specifics (Android blocks third-party call audio capture; Play accessibility ban cited) not independently researched.
