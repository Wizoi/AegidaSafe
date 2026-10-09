# Publishing Aegida to Google Play

Same flow as SmugView (Gradle Play Publisher, listing in the repo), with Aegida's rules on top. **Every task that talks to
Play needs Kevin's explicit yes first, every time** (CLAUDE.md). A rejected upload can still burn its versionCode.

## One-time setup (done = ticked)
- [x] Play Console app created, `com.aegida.app` (permanent), Free.
- [x] Upload key made, kept in OneDrive `AppKeys\aegida-upload.jks`; Play App Signing holds the real key.
- [x] `~/.gradle/gradle.properties` has `AEGIDA_UPLOAD_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`
      (never in the repo). Without them `bundleRelease` makes an unsigned bundle.
- [x] First bundle (versionCode 1, 0.1.0) uploaded by hand to **Internal testing**.
- [x] **Service account for Gradle Play Publisher** (same account as SmugView, checked read-only Oct 8). In Play Console: Users and permissions, invite a Google Cloud service
      account (create it in the Cloud project linked under Setup, API access) and give it release permissions for this app.
      Keep its JSON key **outside the repo** (OneDrive `App Keys`, next to the upload key) and name it in
      `~/.gradle/gradle.properties`: `AEGIDA_PLAY_SERVICE_ACCOUNT=<folder outside the repo>/play-service-account.json`.
      `NoSecretsTest` scans the whole folder, so a key copied into the repo fails the build (finding #68).
- [ ] Privacy policy at a public URL (host `docs/privacy-and-data.md`, for example on GitHub Pages) and the Play Console
      forms: Data safety, content rating, ads (none), target audience (adults), app access (no login; a reviewer cannot pair a
      guardian, so give the demo path), `QUERY_ALL_PACKAGES`, notification-listener and foreground-service (`specialUse`)
      declarations.
- [ ] Ask Play Developer Support about automatic consented alerts before any release beyond the closed test (build-plan phase 4).

## Where things live
| What | Where |
| :-- | :-- |
| Title, short and full description | `app/src/main/play/listings/en-US/*.txt` (limits 30 / 80 / 4000) |
| Icon 512x512, feature graphic 1024x500 | `.../graphics/icon`, `.../graphics/feature-graphic`; made by `python -I scripts/make-launcher-icon.py` from `design/logo` |
| Phone screenshots (2 to 8) | `.../graphics/phone-screenshots/` (real captures from the emulator, see below) |
| Release notes | `app/src/main/play/release-notes/en-US/<versionCode>.txt` (500 chars) |
| Launcher icon in the app | `app/src/main/res/mipmap-anydpi`, `drawable/ic_launcher_foreground.xml` (same script) |
| R8 mapping per version | `release-mappings/<versionCode>/mapping.txt` (git-ignored; keep a copy in OneDrive) |

`PlayListingTest` (in `:app`) fails the build when a limit is broken, a word like "safe" or "protected" slips into the store
text, an image has the wrong size, or the current versionCode has no release notes. `ManifestPolicyTest` fails if the app has
no launcher icon.

## Cutting a release
1. Bump `versionCode` (always a new number, never reused) and `versionName` in `app/build.gradle.kts`.
2. Write `release-notes/en-US/<versionCode>.txt`: plain words, what changed for Mom. Show Kevin before publishing.
3. A Room schema change needs a tested Migration, asked first (CLAUDE.md).
4. Full gate: `./gradlew build` (check for `BUILD SUCCESSFUL`), the guard tests, `python scripts/mutation-check.py`.
5. Build the bundle **without** `-PlocalBuild`: `./gradlew :app:bundleRelease`. Check it is signed:
   `"$JAVA_HOME/bin/jarsigner" -verify app/build/outputs/bundle/release/app-release.aab` says "jar verified".
6. **Ask Kevin**, then upload:
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   ./gradlew publishReleaseBundle                      # sign, bundle, notes, upload to internal
   ./gradlew publishListing                            # text, images and notes only, no new build
   ./gradlew promoteArtifact --from-track internal --promote-track alpha   # same build, no new versionCode
   ```
   While the app is still a draft in the Console (forms not all done), Play only accepts **draft** releases outside the internal track:
   add `--release-status draft` to `promoteArtifact` (`./gradlew :app:promoteReleaseArtifact --from-track internal --promote-track alpha --release-status draft`),
   then finish and send it for review in the Console (Oct 9, versionCode 2: "Only releases with status draft may be created on draft app").
   `publishReleaseBundle` always uploads a new bundle and needs a never-used versionCode. To move a build Play already has to
   another track, use `promoteArtifact`. Do not add `--release-status draft` unless Play rejects with "draft app".
7. Install from the opt-in link on the Pixel (uninstall any `-local` build first: different signature) and update from Play to
   check the update path. Add what you see to `findings.md`.

Play also refuses to save any listing change without a contact email ("This app does not have a contact email address set"); it lives in
`app/src/main/play/contact-email.txt` and is uploaded with the app details. A bare `BUILD SUCCESSFUL` from `publishReleaseListing` can mean an
empty draft was saved when the upload step was skipped as up to date: add `--rerun` and read back with `bootstrapReleaseListing`.

`bootstrapReleaseListing` shows what is live but **overwrites local listing files**. Run `git status` straight after and
`git restore` anything it clobbered.

## Screenshots
Real captures from the emulator (API 36 Play image, 1080x2400), light theme, 100% font, one per screen Mom sees: welcome, home
with "no known problems found", a warning with its plain-words fix, history, my settings. No real names or numbers on screen (use the
fixture owner). Look at each before committing (CLAUDE.md: both themes, 200% font are checked separately, the store gets the
normal one).

## Track notes
- Internal, closed (alpha), open (beta) and production are tracks of one app: the same signing key, so a tester moves between them
  with an in-place update. A tester cannot go to a lower versionCode without uninstalling.
- A personal Play account made after Nov 2023 needs a closed test with 12 opted-in testers for 14 days before it can ask for
  production.
