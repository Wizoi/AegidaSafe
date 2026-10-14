> Research from the design phase (October 2026, when the app was called Phone Angel). Nothing here is built or promised; what is built is in [docs/product-brief.md](../docs/product-brief.md) and ideas are in [docs/ideas.md](../docs/ideas.md).

# Free scam/fraud/threat data sources for Phone Angel (Android, Play, non-commercial, 2026-10-07)

Research note: ~25 fetches/searches. Several pages 404'd or were robots-blocked; those are listed under Gaps rather than guessed. Licence quotes are as relayed by the fetch tool's summaries of the primary pages, not always verbatim page text.

## 1. URL/domain reputation feeds (Safe Browsing, abuse.ch, OpenPhish, PhishTank, Phishing.Database, Spamhaus, urlscan, VirusTotal)

### Takeaway
Best embed candidates: Phishing.Database (MIT repo, no key), abuse.ch URLhaus (free but Auth-Key now mandatory, fair-use, commercial may need paid), PhishTank (free with app key, hourly). Google Safe Browsing is non-commercial-only and v4 is deprecated in favour of v5 (fits a free non-revenue app, but must disclose/obey terms). VirusTotal public API explicitly forbids commercial products and is only 4 req/min, 500/day, so not suitable for per-user lookups in a distributed app.

### Cited Findings
- Safe Browsing v4 APIs are marked deprecated; Lookup API sends URLs to Google (privacy cost, no response-time guarantee); Update API downloads hashed list data for local checks (better privacy, needs canonicalisation + SHA256). — [Google SB v4](https://developers.google.com/safe-browsing/v4)
- Safe Browsing v5 replaces the v4 Update API: hash lists via `hashLists.batchGet`, lookup via `hashes.search` (4-byte prefixes); no v4 shutdown date stated; migration without resetting local DB. — [Migration From V4](https://developers.google.com/safe-browsing/reference/Migration.From.V4)
- Safe Browsing terms: "The Safe Browsing API is for non-commercial use only"; non-commercial = "not for sale or revenue generating purposes"; commercial users directed to Web Risk API. API has no usage cost; default quota set in Developer Console, increase on request. No mobile-specific terms stated. — [Usage limits](https://developers.google.com/safe-browsing/v4/usage-limits), [Migration](https://developers.google.com/safe-browsing/reference/Migration.From.V4)
- abuse.ch: authentication mandatory from 2025-06-30 for all platforms; sign up at auth.abuse.ch (X/LinkedIn/Google/GitHub login, link a second provider), create Auth-Key, send as `Auth-Key` HTTP header; MalwareBazaar/YARAify hunting required auth from Feb 2025. — [abuse.ch blog](https://abuse.ch/blog/community-first/)
- URLhaus: Community API "free of charge under the fair use principles"; commercial/for-profit needs may need a paid enhanced API; asks no dump fetches more often than every 5 minutes; feeds: CSV/JSON dumps, RPZ, hostfile, Snort/Suricata, text URL list, payload CSV, ClamAV sigs, MISP; Auth-Key needed in download URLs; bulk query API at urlhaus-api.abuse.ch. Page itself states no CC0 licence. — [URLhaus API](https://urlhaus.abuse.ch/api/)
- OpenPhish Community feed: text file, updated every 12 hours, free, URL-only (metadata columns blank); "By using the Community Feed, you accept the Terms of Use"; licence/redistribution not on the page (in the linked Terms). — [OpenPhish feeds](https://openphish.com/phishing_feeds.html)
- PhishTank: registration gives app key (still offered per developer page); key goes in download URL (`data.phishtank.com/data/<key>/online-valid.json.bz2`); without key only a few downloads/day; files update hourly; formats XML/CSV/PHP/JSON (+gz/bz2); no data licence stated, links to Cisco terms. — [PhishTank developer info](https://phishtank.org/developer_info.php)
- Phishing.Database: repo MIT licence (copyright Krog, Chababy and contributors 2018-2025); ~497k phishing domains, ~784k links; ACTIVE/INACTIVE/INVALID/NEW lists in .txt/.lst/.adblock/tar.gz, checksums provided; version V.2026-10-02.17 (live as of this month); updated "regularly" (no schedule); page does not say whether MIT covers the data files. — [Phishing.Database](https://github.com/Phishing-Database/Phishing.Database)
- VirusTotal Public API: free with Community key; "500 requests per day and a rate of 4 requests per minute"; "must not be used in commercial products or services"; no multiple accounts. — [VT public vs premium](https://docs.virustotal.com/reference/public-vs-premium-api)
- urlscan.io: API key in `api-key` header; unauthenticated users get "minor quotas"; per-minute/hour/day quotas; public scans visible on front page unless visibility set. Free-tier numbers and commercial terms not on the fetched page. — [urlscan docs](https://urlscan.io/docs/api/)

### Inferences
- A privacy-respecting design is a periodically synced local blocklist (Phishing.Database domains, URLhaus, PhishTank, SB v5 local hash list) rather than live per-URL calls to VT/urlscan. Shipping an abuse.ch/PhishTank key inside the APK would be extractable; better to sync via a small own backend or have the user supply a key.
- Safe Browsing's "non-commercial" wording fits a free, non-revenue family app; keep the app ad-free and unmonetised, and recheck if ever sold.
- Android also exposes on-device Safe Browsing via Play services; not verified here (see Gaps).

### Gaps
- Spamhaus DBL terms: both candidate pages returned 404 (`/resource-hub/dnsbl/dbl-usage-terms/`, `/using-our-public-mirrors/`). Not verified. Unverified background recollection: free public-mirror use is limited to low volume non-commercial and not via large public resolvers; DBL is domain-based so mobile-app direct DNS querying would likely breach it. Treat as "probably not usable"; confirm on spamhaus.org.
- abuse.ch licence (CC0 claim), ThreatFox and MalwareBazaar specific terms: FAQ URL 404; URLhaus page does not state licence. Not verified.
- OpenPhish and PhishTank Terms of Use text not fetched (redistribution/commercial clauses unknown).
- urlscan free-tier numeric quotas and ToS not retrieved.
- Spamhaus DBL, Web Risk pricing not retrieved.

## 2. Breach/password, stalkerware, malware-behaviour and app-intelligence (HIBP, Echap, android-sigma-rules/AndroDR, Exodus, Play Protect/Integrity)

### Takeaway
HIBP Pwned Passwords range API is free, keyless, unlimited and attribution-optional, ideal for a "is this password breached" check. Email search needs a paid key. Echap stalkerware-indicators (CC-BY 4.0) is the best-fitting on-device dataset for a family-safety app. Sigma rules/AndroDR/Exodus could not be verified.

### Cited Findings
- HIBP Pwned Passwords: `https://api.pwnedpasswords.com/range/{first 5 SHA-1 chars}`, free, no key, "There is no rate limit"; no licence/attribution requirement though welcomed; optional `Add-Padding` header. — [HIBP API v3](https://haveibeenpwned.com/API/v3)
- HIBP breached-account search needs `hibp-api-key` and a `user-agent` header (403 without); available on paid Core/Pro/High RPM plans; k-anonymity email search only on Pro and High RPM; rate limits per plan (example 1,000 RPM on Pro); breach/paste data under CC BY 4.0, must clearly identify HIBP as source. Prices not on page. — [HIBP API v3](https://haveibeenpwned.com/API/v3)
- Echap stalkerware-indicators: licence CC-BY 4.0 (maintainers open to alternatives); `ioc.yaml` (package names, Android app certificates, websites, C2 domains/IPs), `watchware.yaml`, `samples.csv` (hashes, packages, certs); generated files: hosts, hosts_full, tinycheck JSON, MISP event, network.csv, quad9_blocklist.txt, stalkerware.stix2, suricata.rules; PR-driven with GitHub Action regenerating `master`; no schedule stated. — [stalkerware-indicators](https://github.com/AssoEchap/stalkerware-indicators)

### Inferences
- Echap's package-name + cert-hash lists can be bundled in the APK and matched against installed packages (needs `QUERY_ALL_PACKAGES` justification on Play, or targeted `<queries>`; policy not researched). CC-BY needs an in-app attribution line.
- Pwned Passwords is trivially safe to embed (k-anonymity) for a "check my mum's email-account password" feature; do not send emails to HIBP without paid key.

### Gaps
- android-sigma-rules and AndroDR: repo URLs guessed (github.com/AndroidSigma/..., ohmysalem/AndroDR) returned 404/robots block. Licence, status and actual repo locations unverified.
- Exodus Privacy API: fetch blocked by robots.txt. Access/licence unverified (background recollection only: tracker list is publicly browsable; API needs token; AGPL project).
- Google Play Protect / Play Integrity: not researched; no fetch made. Note Play Integrity verifies the app/device to your own backend; it is not a scam-data source. Unverified.
- Public banking-trojan reports (e.g. ThreatFabric, Cleafy, abuse.ch/MalwareBazaar package lists): not researched.

## 3. Phone numbers, regulator data and "current scams" digests (FTC DNC, Consumer Sentinel, FCC RMD, IC3, FTC alerts, Tellows/Hiya/Truecaller)

### Takeaway
FTC's daily Do Not Call complaint CSV/API is a usable free number-reputation feed (unverified, self-reported, US-only, weekday updates). IC3 PSAs have an RSS feed for a scam digest. Commercial caller-ID databases (Truecaller, Hiya, Tellows) have no confirmed free embeddable API.

### Cited Findings
- FTC DNC Reported Calls Data: fields = originating phone number, complaint date, call time, consumer city/state, subject, robocall flag; "None of the information about the reported calls is verified"; updated each weekday around noon Eastern (weekends rolled into Monday); daily CSVs e.g. `DNC_Complaint_Numbers_2026-10-06.csv` (~0.3-1.7 MB); developer API endpoint since April 2020; no licence/terms stated on page; dataset covers DNC/robocall complaints, texts not mentioned. — [FTC DNC data](https://www.ftc.gov/policy-notices/open-government/data-sets/do-not-call-data)
- FBI IC3 PSA page lists 2026 PSAs (e.g. 17 Sep "Scammers Impersonating Law Enforcement and Government Officials in Fraud Schemes", 1 Sep consent-phishing); RSS feed at https://www.ic3.gov/PSA/RSS; no reuse terms on the page. — [IC3 PSA](https://www.ic3.gov/PSA)
- Truecaller historically offered a paid API to select developers (2013 report); no current free developer API found. — [TechCrunch 2013](https://techcrunch.com/2013/05/22/truecaller-api)

### Inferences
- A rolling local set of the last ~30 days of FTC DNC numbers (a few MB) could give "this number was reported" hints, but spoofed caller IDs make false positives likely; present as a soft signal. Numbers reported are US-centric.
- Because FTC content is US government work, reuse is probably permissive, but this was not confirmed on the page; verify before shipping.

### Gaps
- FTC consumer alerts RSS URL: `/stay-informed/rss` 404. Real feed URL unverified.
- FTC Consumer Sentinel: not researched (background: law-enforcement-only access; public data is aggregated reports, not number-level) - unverified.
- FCC Robocall Mitigation Database: search returned only FCC instructions/third-party pages; download/API terms not fetched. Unverified; it lists provider filings, not scam numbers, so low value for this app.
- AARP Fraud Watch RSS: not researched.
- Tellows, Hiya, Truecaller current API/terms: search yielded no primary developer pages. Unverified; assume paid/partner-only.

## 4. DNS filtering resolvers (Quad9, Cloudflare 1.1.1.2/3, CleanBrowsing)

### Takeaway
All three are free and work via Android 9+ Private DNS with zero code; Quad9 and Cloudflare Security are the best "malware/phishing blocking at DNS level" no-effort options, but the app can only guide the user (or use a VpnService), not control DNS silently.

### Cited Findings
- Quad9 Recommended (malware blocking, DNSSEC): 9.9.9.9 / 149.112.112.112; DoT `dns.quad9.net`, DoH `https://dns.quad9.net/dns-query`; Unsecured 9.9.9.10 does not block; usable via Android Private DNS; site content CC BY-NC-SA 4.0 (this is the website content licence, not a resolver usage term). — [Quad9 addresses](https://www.quad9.net/service/service-addresses-and-features/)
- Cloudflare 1.1.1.1 for Families: `security.cloudflare-dns.com` (1.1.1.2/1.0.0.2, blocks malware); `family.cloudflare-dns.com` (1.1.1.3/1.0.0.3, malware + adult); set under Android 9+ Settings > Private DNS. — [Cloudflare Android setup](https://developers.cloudflare.com/1.1.1.1/setup/android/)
- CleanBrowsing free: Security filter (185.228.168.9 / 185.228.169.9, `security-filter-dns.cleanbrowsing.org`, blocks phishing/spam/malware), Family and Adult filters; free plan has no account, "throttled", no support, "Free DNS filtering for parents, not organizations". — [CleanBrowsing filters](https://cleanbrowsing.org/filters)

### Inferences
- For a family app, recommending Private DNS = `security.cloudflare-dns.com` or `dns.quad9.net` is zero-cost and ToS-light. Hard-coding them into a distributed VPN-based filter may need each provider's terms checked (not fetched).

### Gaps
- Resolver ToS for programmatic/embedded use, and query-logging/privacy policies, not fetched.

## 5. Local classifier data and on-device ML (UCI SMS Spam, Kaggle, ML Kit, TFLite, Gemini Nano/AICore)

### Takeaway
UCI SMS Spam Collection is CC BY 4.0 but tiny (5,574 msgs, old UK-style spam), so only a baseline. Gemini Nano via ML Kit GenAI does NOT list Galaxy S23; supported Samsung devices are S25/S26 and recent foldables, so a Galaxy S23 needs a TFLite/own-model fallback.

### Cited Findings
- UCI SMS Spam Collection: CC BY 4.0; 5,574 labelled ham/spam messages; ZIP 198.6 KB at https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip. — [UCI](https://archive.ics.uci.edu/dataset/228/sms+spam+collection)
- ML Kit GenAI is built on AICore (system module; Gemini Nano delivered/updated by AICore; inference local, inputs/outputs not stored); separate ML Kit GenAI additional ToS apply; developer "solely responsible for the safety" of the app. Prompt API is text/multimodal custom prompt. — [Android Gemini Nano](https://developer.android.com/ai/gemini-nano)
- Device support (overview page): Summarization/Proofreading/Rewriting/Image Description list Samsung Galaxy S25 series, S26 series, Z Flip8, Z Fold8, Z Fold7, Z TriFold; Prompt API Samsung entries are Z Fold7, Z TriFold (nano-v2), S26 series (nano-v3), Z Flip8/Z Fold8 (nano-v4). The Galaxy S23 is not listed for any GenAI API. — [ML Kit GenAI overview](https://developers.google.com/ml-kit/genai)

### Inferences
- Galaxy S23 (the user's likely device) cannot rely on Gemini Nano; plan on a small TFLite text classifier + regex/keyword heuristics, with Gemini Nano as an optional enhancement on supported phones.
- UCI alone is too old/small for modern smishing (parcel, toll, bank-impersonation); you'd need to author synthetic/augmented examples or find a larger CC-licensed set.

### Gaps
- Kaggle smishing datasets and their licences: not researched.
- TFLite / ML Kit Smart Reply / classic ML Kit text APIs: not fetched; no cited facts.
- Whether ML Kit GenAI "Prompt API" allows scam-classification use under its ToS: not read.

## Ranking: top 8 by usefulness-to-effort (judgement, based on findings above)

1. Quad9 / Cloudflare Security Private DNS - zero code, free, blocks malware/phishing (user setup guidance only).
2. Pwned Passwords range API - free, keyless, unlimited, no attribution obligation.
3. Echap stalkerware-indicators - CC-BY 4.0, bundle packages/certs for on-device scan; core to a family-safety app.
4. Phishing.Database - MIT repo, no key, GitHub-hosted, large domain list; confirm data-file licence.
5. Google Safe Browsing v5 (local hash list) - best coverage and privacy model; non-commercial only; moderate integration effort.
6. URLhaus (abuse.ch) - high-quality malware URLs; Auth-Key required, fair-use, avoid shipping key in APK.
7. FTC DNC complaints daily CSV/API - free number-level signal, unverified data, US only.
8. IC3 PSA RSS (plus FTC alerts once URL confirmed) - cheap "current scams" digest for guardians.

Honourable/avoid: PhishTank (works, key + hourly, terms unverified), OpenPhish community (12-hourly, URLs only, terms unread), VirusTotal public (forbids commercial products, 4/min), UCI SMS (baseline only), Gemini Nano (not on S23), Truecaller/Hiya/Tellows (no free API found).
