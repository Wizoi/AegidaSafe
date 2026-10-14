> Research from the design phase (October 2026, when the app was called Phone Angel). Nothing here is built or promised; what is built is in [docs/product-brief.md](../docs/product-brief.md) and ideas are in [docs/ideas.md](../docs/ideas.md).

# Scam/phishing text, link and QR protection for a non-default-SMS Play app (Galaxy S23+, Android 16 / One UI 8.x, 80+ user), as of 2026-10-07

Method note: findings come from WebFetch summaries (small-model extractions) of primary pages; items marked [training knowledge] were NOT verified in this session. Several Google help pages 404'd or returned index pages.

## 1. What can a non-default-SMS app do? (Play SMS/Call Log policy; NotificationListenerService)

### Takeaway
READ_SMS/RECEIVE_SMS are effectively off the table: Play allows them only to the default SMS/Phone/Assistant handler or via a Play-granted exception, and the anti-smishing exception requires "a track record of significant protection for users"; "family or device locator" is explicitly an invalid use. The realistic route is NotificationListenerService (user-granted Notification access) plus a "share/paste a suspicious text into the app" flow, but notification content for OTP-type messages is redacted on Android 15+ and Messages-app notification text may be truncated.

### Cited Findings
- Permitted uses of SMS/Call Log permissions: default SMS, Phone or Assistant handler; the app must be actively registered as default handler before requesting the permission. — [Play SMS & Call Log policy](https://support.google.com/googleplay/android-developer/answer/10208820)
- Non-default apps may get a temporary exception only if the permission enables a listed core function and there is "no alternative method"; listed exceptions include anti-smishing, caller ID/spam detection/blocking, backup/restore, device automation, etc. Anti-smishing requires "a track record of significant protection for users." — [same](https://support.google.com/googleplay/android-developer/answer/10208820)
- "Family or device locator" is listed as an invalid use; exceptions go through the Play Console Permissions Declaration Form; apps lacking approval are removed and undeclared use can lead to suspension. — [same](https://support.google.com/googleplay/android-developer/answer/10208820)
- Account verification via phone call stops being a permitted READ_CALL_LOG use effective Jan 27, 2027 (not directly relevant). — [same](https://support.google.com/googleplay/android-developer/answer/10208820)
- Restricted-permission data is personal and sensitive user data: must be necessary for core functionality, requested in context, never sold/shared for sale, new consent needed for new purposes; users who decline should get a reasonable alternative (e.g., manual entry). — [Play permissions/sensitive info policy](https://support.google.com/googleplay/android-developer/answer/9888170)
- AccessibilityService: the isAccessibilityTool flag is only for apps directly supporting people with disabilities (an anti-scam app generally does not qualify as such); otherwise requires Play Console declaration, demo video, prominent in-app disclosure and consent; can't be used to bypass platform security/notifications. Use narrower APIs where possible. — [same](https://support.google.com/googleplay/android-developer/answer/9888170)
- Notification listener mechanics: manifest service with BIND_NOTIFICATION_LISTENER_SERVICE; user enables it in Settings > Notification access (cannot be requested via runtime dialog); app can send user via Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS and check NotificationManagerCompat.getEnabledListenerPackages(); StatusBarNotification extras give title/text/big text. — [developer.android.com NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) (fetch only returned nav; details are from the fetch model's general knowledge, so verify)
- Sideloaded apps on Android 13+ face "restricted settings" for notification access [training knowledge, unverified here]; apps installed from Play are generally not subject to it.
- Android 15 stops untrusted notification listeners from reading sensitive 2FA/OTP notification content: Android System Intelligence identifies and redacts those before delivery to third-party listeners; Android 16 extends redaction to the lock screen in higher-risk situations. — [Android Authority](https://www.androidauthority.com/android-16-sensitive-notifications-lock-screen-3501564), also [Android Authority (A15)](https://www.androidauthority.com/android-15-sensitive-notifications-3416414)
- Android docs warn SMS is unauthenticated/spoofable and readable by apps with READ_SMS. — [Android security tips](https://developer.android.com/privacy-and-security/security-tips)

### Inferences
- A Play-listed "Phone Angel" for one family member will almost certainly not meet the anti-smishing "track record" bar; do not plan on READ_SMS. Don't ship the permission, even in a flavor, because a failed declaration risks removal.
- Notification-listener design: scan only notifications from allowlisted messaging packages (com.google.android.apps.messaging, com.samsung.android.messaging), extract URLs/phone numbers from EXTRA_TEXT/BIG_TEXT/MessagingStyle, process on-device, never upload message bodies (send only hashes/host names to lookup services). Needs a prominent disclosure and Play Console declaration; mark as uncertain whether Play requires a specific form for NotificationListener (the fetched policy page did not mention it).
- Since the app only needs links, OTP redaction is mostly harmless; do not try to read or relay OTP codes.
- Fallback that needs no sensitive permission: Android Share sheet / "Check this message" (ACTION_SEND text) and ACTION_PROCESS_TEXT so the user (or family member) can long-press text > share to Phone Angel. Lower friction for an 80+ user: a big "Check a text" button that reads clipboard only after user taps paste.
- Messages notifications for long texts may truncate URLs; Samsung Messages/Google Messages may hide content on lock screen; treat misses as expected [unverified].

### Gaps
- Could not retrieve the exact Play policy language on NotificationListenerService disclosure; no verified statement on whether a declaration form is required.
- Did not verify Samsung/Google Messages notification truncation behavior or whether Google Messages sets notification visibility private by default.

## 2. Link checking: free data sources

### Takeaway
Best free stack: Google Safe Browsing (v5 hashes.search, privacy-preserving; non-commercial only, with warning-wording/attribution rules) as primary, plus locally cached URLhaus and OpenPhish/Phishing.Database lists as secondary; use VirusTotal only sparingly (public API forbidden in commercial products, 4/min, 500/day). PhishTank registration/availability is uncertain.

### Cited Findings
- Safe Browsing API is "for non-commercial use only"; commercial use directed to Web Risk; no cost. The pages do not address mobile apps or Play distribution specifically. — [Permitted Use](https://developers.google.com/safe-browsing/reference/Appropriate.Usage), [Terms](https://developers.google.com/safe-browsing/terms)
- Default quota is assigned on enabling the API in Google Cloud console; viewable there; increase can be requested (Google asks for contact). — [Usage limits](https://developers.google.com/safe-browsing/v4/usage-limits) (no numeric quota found)
- Warnings must use qualifying language ("suspected", "potentially", "likely"); show "Advisory provided by Google" for Google-derived warnings only; disclose beforehand that protection is imperfect; show warnings only when cached response still valid. — [Usage limits / user warnings](https://developers.google.com/safe-browsing/v4/usage-limits)
- Terms: cannot warn/block on a listed URL unless updated info received within the last 30 minutes; URLs sent via SearchUrls may be used/shared by Google. — [Terms](https://developers.google.com/safe-browsing/terms)
- v5: `urls.search` (v5alpha1) sends raw URLs (no confidentiality); `hashes.search` sends only a 4-byte hash prefix and client canonicalizes/hashes; hashLists + Global Cache allow Real-Time, Local List, No-Storage Real-Time modes; optional Oblivious HTTP gateway hides IPs. — [Safe Browsing v5 reference](https://developers.google.com/safe-browsing/reference)
- URLhaus: free Auth-Key required (abuse.ch portal); dumps refreshed every 5 minutes (don't fetch more often); hostfile of malware domains for last 48h excluding Tranco top 1M; free under fair-use, commercial/for-profit users may need paid API. Rate limits not stated. — [URLhaus API](https://urlhaus.abuse.ch/api/)
- OpenPhish Community feed: free, updated every 12 hours, text file, commercial use listed as "Limited" (unexplained); use implies acceptance of Terms of Use. — [OpenPhish feeds](https://openphish.com/phishing_feeds.html)
- PhishTank: app key via api_register.php; without key only "a few downloads per day"; with key, hourly file online-valid.json.bz2, unlimited HEAD checks; Cisco EULA; page doesn't say whether new registrations are open. — [PhishTank developer info](https://phishtank.org/developer_info.php)
- VirusTotal Public API: 4 requests/min, 500/day; must not be used in commercial products/services. — [VT docs](https://docs.virustotal.com/reference/public-vs-premium-api)
- Phishing.Database (mitchellkrogza GitHub) and Spamhaus DBL: not researched/verified in this session. Spamhaus DBL public DNS mirrors historically prohibit heavy/commercial use and block queries from large public resolvers [training knowledge, unverified].

### Inferences
- Design: a small backend is probably unnecessary; but Safe Browsing key embedded in the APK can be extracted; either proxy through a tiny server (adds hosting/privacy burden) or use the Local List mode. A personal, single-family app is arguably "non-commercial" but the terms don't explicitly address Play distribution; if the app is distributed publicly on Play, confirm with Google (uncertain). If it is private/internal testing for one family, risk is lower.
- Prefer hashes.search (prefix) over raw URLs for privacy given Kevin's mom's messages.
- Phishing feeds for texts are dominated by short-lived URLs; most smishing domains are new and unlisted, so heuristics (below) add real value; blocklists alone will miss many.
- Daily download of URLhaus/OpenPhish (12h cadence) to a local Bloom filter/Room table keeps lookups offline and private; verify license permission before bundling.

### Gaps
- Numeric Safe Browsing default quota, Play-app eligibility, Lookup API v4 specifics, Phishing.Database license, Spamhaus terms, and OpenPhish "Limited" definition not confirmed.

## 3. Filtering DNS via Private DNS; can the app guide setting it on Samsung?

### Takeaway
Yes, guidance is straightforward: Samsung path is Settings > Connections > More connection settings > Private DNS > "Private DNS provider hostname". Free hostnames exist for Cloudflare Families, CleanBrowsing and Quad9. The app can read the state (but not change it) and deep-link toward settings.

### Cited Findings
- Samsung menu path and sample hostnames (dns.google, 1dot1dot1dot1.cloudflare-dns.com, dns.quad9.net). — [Samflux](https://www.samflux.com/2025/12/how-to-turn-on-private-dns-on-samsung.html)
- Cloudflare for Families: `security.cloudflare-dns.com` (malware, 1.1.1.2) and `family.cloudflare-dns.com` (malware + adult, 1.1.1.3); Private DNS applies on cellular and Wi-Fi. — [Cloudflare docs](https://developers.cloudflare.com/1.1.1.1/setup/android/)
- CleanBrowsing free hostnames: `family-filter-dns.cleanbrowsing.org`, `adult-filter-dns.cleanbrowsing.org`, `security-filter-dns.cleanbrowsing.org`; Samsung path under Connections > More Connection Settings; DoT port 853 may be blocked on some networks. — [CleanBrowsing guide](https://cleanbrowsing.org/guides/android-private-dns/)
- Quad9 hostname dns.quad9.net (malware-blocking) — listed on Samflux above; Quad9 terms/privacy not separately verified.

### Inferences
- Recommended: security.cloudflare-dns.com (malware/phishing only; avoids adult-content over-blocking surprises) or Quad9. Family filters add adult-content blocking, which may be unwanted.
- App reads state via ConnectivityManager/LinkProperties (isPrivateDnsActive, privateDnsServerName; needs only ACCESS_NETWORK_STATE) [training knowledge; the Android reference page fetch 404'd]. App cannot set it without privileged permission (WRITE_SECURE_SETTINGS), so show a step-by-step screen with a copy-hostname button and a deep link Settings.ACTION_WIRELESS_SETTINGS or best-effort ACTION_SETTINGS; Samsung has no known direct Private DNS intent [unverified].
- DNS filtering only protects when the link opens in a browser/app using system DNS; apps with built-in DoH (e.g., Chrome Secure DNS "use current provider" is fine, but custom DoH bypasses) can bypass. A VPN active also bypasses Private DNS. [training knowledge]
- Private DNS may break captive portals/public Wi-Fi; mention a rescue step ("turn Private DNS to Automatic").

### Gaps
- No verification of Cloudflare/Quad9/CleanBrowsing free-use limits or logging policies; no Samsung-published instructions fetched.

## 4. Link-shortener expansion, lookalike domains, domain age (RDAP), false positives

### Takeaway
Not researched with sources in this session (budget used on policy/data-source questions); only design-level guidance from training knowledge is available.

### Cited Findings
- Safe Browsing and URLhaus both only flag known-bad hosts; Safe Browsing hashing requires client canonicalization (so expand redirects before lookups). — [SB v5 reference](https://developers.google.com/safe-browsing/reference)
- URLhaus hostfile excludes domains in the Tranco top 1M (a built-in false-positive control). — [URLhaus API](https://urlhaus.abuse.ch/api/)

### Inferences
- [training knowledge, unverified] Expand shorteners (bit.ly, tinyurl, t.co etc.) with HEAD/GET following redirects without cookies, with hop limit and no content download; a network request to a scam URL leaks the phone's IP to the scammer and confirms a live number, so prefer doing expansion server-side or via a third-party unshortening service, or skip and warn "short link, destination hidden."
- [training knowledge] RDAP: IANA bootstrap (data.iana.org/rdap/dns.json) then registry RDAP; "registered <30 days ago" is a strong signal, but RDAP privacy/rate limits vary and some ccTLDs lack RDAP. Check against Tranco top-1M to reduce false positives.
- [training knowledge] Lookalike heuristics: punycode/IDN homoglyphs, brand name + extra words (usps-tracking.xyz), cheap TLDs, brand-with-hyphen, @ in URL, raw IPs. Surface as "be careful" not "this is a scam".

### Gaps
- No sources fetched for shortener behavior, RDAP specifics, or false-positive rates; the report writer should treat Section 4 as unverified.

## 5. QR code scanning

### Takeaway
Yes. Google's code scanner (ML Kit, play-services-code-scanner) lets the app scan with no CAMERA permission and returns the raw value; the app can then check the URL before opening it.

### Cited Findings
- Code scanner: no camera permission requested by the app; scanning delegated to Google Play services; on-device processing, no image/result storage by Google; API level 23+; module may need to be downloaded (can be preinstalled via manifest meta-data); returns Barcode with rawValue. — [ML Kit code scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)

### Inferences
- Flow: big "Scan a QR code" button > code scanner > parse rawValue > if http(s) run same pipeline as texts (expand, blocklist, heuristics) > show plain-language verdict > only then offer "Open". Handle non-URL payloads (WIFI:, SMSTO:, tel:, otpauth:, payment URIs) with a "this is not a website" warning. Parking-meter/"quishing" stickers are a prime elder scam [training knowledge]; the stock Samsung camera auto-opens links, so advise using Phone Angel's scanner.
- Needs Google Play services (fine on S23+).

### Gaps
- No evidence gathered on quishing prevalence stats.

## 6. Existing protections: Google Messages, Samsung Messages / Auto Blocker / Knox; verifying they are on

### Takeaway
Google Messages has on-device Scam Detection (on by default for non-contacts; English in US/UK/Canada at launch) and spam protection; Samsung has Auto Blocker, Message Guard (Knox) and, on newer devices, AI "Intelligence Blocking." A non-privileged app can't read most of these settings, so it should guide the user and ask for a confirmation (family-member checklist), not claim to verify.

### Cited Findings
- Google Messages Scam Detection (announced March 4, 2025): on-device analysis of SMS/MMS/RCS; warns during conversation; on by default under Spam Protection; only non-contacts; options to dismiss or report and block; launched English in US/UK/Canada. — [Google blog](https://blog.google/security/new-ai-powered-scam-detection-features/)
- Samsung (June 16, 2026 announcement): "Intelligence Blocking" (AI classification/blocking of suspicious messages) added from Galaxy S25 series on; stronger Phishing App Risk Notification; malicious message blocking using KISA data is Korea-only; features tied to One UI 8.5+/9.0 for S26 onward. — [Asiae](https://view.asiae.co.kr/en/article/2026061609111358917)
- Samsung Auto Blocker (One UI 6+) blocks installs from unauthorized sources/ADB commands etc. — [Android Authority](https://androidauthority.com/samsung-auto-blocker-3380745), [Bleeping Computer](https://www.bleepingcomputer.com/news/security/samsung-galaxy-gets-new-auto-blocker-anti-malware-feature/) (search snippets only, not fetched in detail)
- Samsung Message Guard (Knox) sandboxes image-based zero-click exploits in Samsung Messages. — [Knox docs](https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/application-security/samsung-message-guard/) (search result only)
- Google Messages help article on scam detection / link warnings not retrievable (index page only; link-warning behavior unconfirmed). — [Messages help](https://support.google.com/messages/answer/11231641)

### Inferences
- Galaxy S23+ is not in the "S25 and later" group for Intelligence Blocking, so do not assume it is available. [inference from the Asiae text]
- Ask: which messaging app is default (Samsung Messages vs Google Messages)? Recommend Google Messages as default for scam detection, but this is a behavior change for an 80+ user; the app itself must not become default SMS.
- Verification: app can check default SMS package via Telephony.Sms.getDefaultSmsPackage (no permission) [training knowledge]; cannot read Messages' spam settings; Auto Blocker state is not exposed to third parties [unverified]. Use a "show me your screen / family helper" checklist with screenshots.

### Gaps
- Exact current menu paths for Google Messages scam detection and Auto Blocker on One UI 8, whether Scam Detection is available on S23+ in the US, and non-English regions were not verified.

## 7. Chrome Enhanced Safe Browsing

### Takeaway
An app cannot detect or set Chrome's protection level; it can only guide the user. Desktop details are verified; Android menu path is from training knowledge.

### Cited Findings
- Enhanced protection warns about risky sites/downloads/extensions, sends URL and small page-content sample and system information to Google Safe Browsing; desktop path Settings > Privacy and security > Security > Enhanced protection. — [Chrome help](https://support.google.com/chrome/answer/9890866)
- Android path not retrieved.

### Inferences
- [training knowledge] On Android: Chrome > ⋮ > Settings > Privacy and security > Safe Browsing > Enhanced protection. No public API for another app to read Chrome's setting; ask the user to show the screen. Trade-off: more data shared with Google, a privacy consideration for the user's consent.
- Samsung Internet is the default browser on many Galaxy phones and has its own protection (Settings > Privacy... "Protect against..." ) [unverified]; mention to guide user to Chrome or confirm default browser.

### Gaps
- Android-specific Chrome help page not verified; no source for Samsung Internet protections.

## 8. Clipboard and OTP risks; what NOT to attempt

### Takeaway
Avoid anything that reads OTPs, intercepts SMS, uses Accessibility to scrape Messages, or monitors the clipboard in the background. Android restricts background clipboard reads; OTPs are redacted for listeners.

### Cited Findings
- Android 15+ redacts OTP notification content for untrusted listeners; Android 16 hides OTP content on lock screen in higher-risk contexts. — [Android Authority](https://www.androidauthority.com/android-16-sensitive-notifications-lock-screen-3501564)
- Play: AccessibilityService can't be used to circumvent platform privacy controls or notifications, requires declaration/disclosure; the isAccessibilityTool flag is reserved for disability support. — [Play permissions policy](https://support.google.com/googleplay/android-developer/answer/9888170)
- Play recommends SMS Retriever API as alternative to READ_CALL_LOG/SMS for verification flows. — [Play SMS policy](https://support.google.com/googleplay/android-developer/answer/10208820)
- SMS is unauthenticated and capturable by READ_SMS apps. — [Android security tips](https://developer.android.com/privacy-and-security/security-tips)

### Inferences
- Do NOT: request READ_SMS/RECEIVE_SMS; use Accessibility to read Messages; become default SMS; auto-open or auto-fetch unknown links on the phone; read clipboard in the background (Android 10+ only allows foreground app/IME; Android 12+ shows a "pasted from clipboard" toast) [training knowledge]; upload message bodies; display "this is safe" (only "no known problems found").
- Do: tell users never to read OTP codes aloud; add a plain-language "If someone asks for a code, it's a scam" card. SMS Retriever is for the app's own verification flows, not scanning.

### Gaps
- Clipboard and SMS Retriever official docs were not fetched; Android 16 behavior-change doc for OTP redaction not directly fetched (relied on news coverage).
