# Findings

Every review finding, bug and surprise gets a numbered row here **before** it is fixed. A finding that
lives only in a conversation gets lost and decided twice.

Each row says: what was seen, the premise checked (with a count for any "all" or "none" claim), the test
that failed first, and the commit.

| # | Date | Found by | Finding | Premise checked | Test that failed first | Commit / status |
|---|---|---|---|---|---|---|
| 1 | 2026-10-08 | design | Samsung may put Aegida to sleep after about 3 unopened days (docs only, not yet seen on a device) | From documentation, not run | - | Open: validate on a Samsung (see samsung-validation-options.md) |
| 2 | 2026-10-08 | design | Samsung Auto Blocker "Maximum restrictions" hides links in messages, so link checks would have nothing to check (docs only) | From documentation, not run | - | Open: validate on a Samsung; must not read as "no known problems" |
| 3 | 2026-10-08 | skeleton build | Design section 2 and CLAUDE.md draw `:detectors` above `:platform`/`:store`, but `:detectors` must be pure JVM, and a JVM module cannot depend on an Android library module. Same for `Problem`/`Texts` in `:alerts`: `NotificationPoster` in `:platform` needs channel names | Checked against Gradle: a `kotlin.jvm` module cannot consume an `android.library` | `LayerTest.moduleDependenciesPointDownOnly` encodes the real graph | Skeleton: ports (`Clock`, `HttpClient`, `OemSource`, `CheckLog`) and `Texts` live in `:core`; `:detectors`/`:alerts` depend on `:core` only; `:platform`/`:store` implement the ports. Diagram in design doc and CLAUDE.md still to update (Kevin to confirm) |
| 4 | 2026-10-08 | skeleton build | `SqlSingleOwnerTest.everySchemaVersionIsExported` failed on the first build although Room wrote `1.json` later in the same build: the guard ran in parallel before `:store` kapt | Seen once, on the first build; the file existed after | That guard, on the first build | Fixed in skeleton: guard test `mustRunAfter` `:store` kapt tasks |
| 5 | 2026-10-08 | guard (CopyRulesTest) | Caught "URL-safe" in a developer-only `require` message in `Endpoint.kt` | 1 hit over all shipped string literals | `CopyRulesTest.neverSaySafe` | Fixed in skeleton by rewording; rule kept strict (every string literal, not only `Texts`) |
| 6 | 2026-10-08 | skeleton build | No API 36 Google Play system image on this PC (installed: `android-34;google_apis`, `android-37.0;google_apis`). Needed for the emulator spike | `ls` of the SDK `system-images` folder | - | Open: `scripts/emulator-checks.sh avd` downloads it; do at the start of the spike |
| 7 | 2026-10-08 | skeleton build | Merged manifest gets `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (WorkManager) and `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx.core). None banned. `INTERNET` is deliberately not requested yet | Read from `app/build/intermediates/merged_manifest/debug` | `ManifestPolicyTest` (shown to fail with `SYSTEM_ALERT_WINDOW` planted) | Open: `INTERNET` needed for ntfy in build step 1; ask Kevin first |
