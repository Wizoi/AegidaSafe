---
name: publish-app
disable-model-invocation: true
description: Cut an Aegida release - bump the version, write release notes, run the gate, build the signed bundle and (only with Kevin's yes) publish it to Google Play with Gradle Play Publisher.
---

# Aegida release (publish-app)

Follow [docs/PUBLISH.md](../../../docs/PUBLISH.md), section "Cutting a release". Short version:

1. Ask which version Kevin wants. Bump `versionCode` (never reuse one) and `versionName` in `app/build.gradle.kts`.
2. Write `app/src/main/play/release-notes/en-US/<versionCode>.txt` in plain words (500 chars, never "safe"). Show it to Kevin.
3. Run the gate: `./gradlew build` (look for `BUILD SUCCESSFUL`, do not trust a piped exit code), the guard tests, and
   `python scripts/mutation-check.py`. Gradle needs the Android Studio JBR.
4. Build `./gradlew :app:bundleRelease` without `-PlocalBuild`, then check it with `jarsigner -verify`.
5. **Stop and ask Kevin before anything that talks to Play**: `publishReleaseBundle`, `publishListing`, `promoteArtifact`.
   Say which task, which track and which versionCode. Never run them on your own.
6. After he says yes, run the one task he approved, then report what Play accepted. Copy `mapping.txt` for that versionCode
   to a safe place (it is in `release-mappings/<versionCode>/`, git-ignored).
7. Add a row to `docs/findings.md` for anything odd. Remind Kevin to update from Play on the Pixel and uninstall any `-local`
   build first.

A Room schema change in the release needs a tested Migration that Kevin approved. Do not publish without one.
