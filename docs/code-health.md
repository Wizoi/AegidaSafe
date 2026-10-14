# Code health with an AI agent writing most of the code (Oct 11, 2026)

Research and measurements by an Opus 5.5 high agent (Kevin: "keep the codebase healthy and efficient"). Nothing here is built yet
unless marked; decisions are Kevin's.

## What the evidence says

Measured:
- **Less refactoring, more copying.** GitClear (211M and 623M changed lines): moved/refactored code fell from 25% of changes (2021) to
  under 10% (2024) and 3.8% (2026); copy/paste rose from 8.3% to 15.7%; two-week churn up 15%. Trend data, not cause and effect.
  ([2025](https://www.gitclear.com/ai_assistant_code_quality_2025_research), [2026](https://www.gitclear.com/the_ai_code_quality_maintainability_gap))
- **Warnings and complexity that stay.** CMU study of 806 projects that adopted Cursor vs 1,380 matched: a short speed-up, then about
  +30% static-analysis warnings and +25-42% complexity that did not go away ([He et al., MSR '26](https://arxiv.org/abs/2511.04427)).
- **AI amplifies what is there.** DORA 2024: more AI use went with -7.2% delivery stability; 2025: throughput up, stability still down;
  teams with automated tests, small batches and fast feedback benefit ([2024](https://cloud.google.com/blog/products/devops-sre/announcing-the-2024-dora-report), [2025](https://cloud.google.com/blog/products/ai-machine-learning/announcing-the-2025-dora-report)).
- **Feeling fast is not being fast.** METR's trial: experienced developers were 19% slower with AI while believing they were 20% faster ([2025](https://metr.org/blog/2025-07-10-early-2025-ai-experienced-os-dev-study/)).
- **Tests that lock in bugs.** LLM-written assertions capture what the code does, not what it should do ([Konstantinou et al.](https://arxiv.org/abs/2410.21136)).
- **Invented packages.** 19.7% of 2.23M AI-suggested packages did not exist, and many recur, so they can be registered by attackers ([USENIX Sec 2025](https://arxiv.org/abs/2406.10279)).

What helps (practice): checks a machine runs every time (hooks, guards, static analysis), small batches, mutation testing, a review in a
fresh context, and deleting what a change makes unused.

## This repo on Oct 11

- 6,678 main and 6,653 test lines of Kotlin, 2,383 lines of scripts; 142 commits in four days.
- Kotlin lines added 14,270, deleted 939 (6.6%); no commit is a refactor or cleanup.
- Seven test fakes copied between `detectors/.../Fixtures.kt` and `alerts/.../AlertFakes.kt`, already drifting.
- Unused texts: `CANT_CHECK`, `YOU_TOLD_ME`, `OPEN_SETTINGS`, `CANT_TELL` (the first two carry CLAUDE.md's "can't check / you told me"
  rule, and nothing shows them: is the state missing from the screens, or are they dead?).
- `NoSecretsTest` takes about 30 s: it reads about 275 MB, mostly `release-mappings/*/mapping.txt` and other agents' `.claude/worktrees`.
- Unit tests run twice in `:app`, `:platform`, `:store` (Debug and Release), about 48 s of about 133 s.
- `docs/findings.md` is 85 KB and grows about 20 KB a day; agents read it every session.
- The design promises CI, lint and nightly emulator runs; none exist.
- Guards cover the product rules well (layering, one owner, copy rules, mutation checks); nothing guards duplication, size, dead code,
  complexity, test time or dependencies.

## Recommendations, cheap first

1. Secrets scan reads only tracked files (`git ls-files`), keeping a self-test that a planted secret is caught. Saves about 28 s.
2. One shared fake per port in a `:core` test-fixtures set; contract tests run the same scenarios against each fake and its Room store.
3. A guard that every `Texts`/`Problem` constant is used (after deciding the "can't check" question).
4. Agent habits (CLAUDE.md): search before adding a helper, fake or text; delete what a change makes unused in the same change; no new
   dependency without Kevin's approval and a check that it exists with real history; tests assert behaviour, never a count of call sites.
5. Run unit tests once (Debug only), if no test needs the release manifest.
6. detekt and Android Lint with a baseline (only new issues fail); a file-size guard (main over 350 lines, tests over 450, with an allow list).
7. A weekly duplication report (PMD CPD covers Kotlin; jscpd for Python), a test-time budget guard, and CI when Kevin wants it.
8. A weekly 45-minute cleanup run on Opus with a fixed checklist, producing deletion-only commits; `findings.md` rows kept to two lines,
   closed rows moved to an archive.

Track weekly: Kotlin deletion ratio (6.6% now), duplicated fakes (7), unused texts (4), largest main file (345 lines), suite time
(about 133 s) and slowest class (30 s), detekt baseline count, mutation-check pass rate.

## Done (Oct 13)

- Secrets scan reads tracked files only (`git ls-files`); `.claude/worktrees/` is git-ignored. It no longer takes ~30 s.
- Unit tests run once (debug variant) in `:app`, `:platform`, `:store`.
- The four unused texts are deleted; `UnusedTextsTest` fails the build on any unused text.
- One shared set of fakes in `core/src/testFixtures` (`com.aegida.testkit`), used by `:core`, `:detectors`, `:alerts` and `:app` tests;
  the seven duplicates and two more local copies are gone (purpose-built fakes that count reads or wait on a gate stay local).
- CLAUDE.md "Keeping the code clean": search before adding, delete what a change makes unused, no new dependency without Kevin's
  OK, tests assert behaviour.

## Done (Oct 14, items 6-7 without new tools)

No dependency or plugin was added (Android Lint is part of AGP; the rest is Gradle, Kotlin and Python standard library).

- **Android Lint with a baseline** for `:app`, `:platform`, `:store` (`lint { baseline = lint-baseline.xml; abortOnError = true }`).
  `./gradlew lint` (part of `build`) fails only on issues not in the baseline. Baseline today: 11 entries (`:app` 5, `:platform` 5, `:store` 1; rules UseKtx, ModifierParameter, MonochromeLauncherIcon). Regenerate with `./gradlew updateLintBaseline` only when an
  entry is fixed, never to hide a new one.
- **File-size guard** (`FileSizeTest` in `:guards`): main Kotlin over 350 lines and test Kotlin over 450 fail, with an allow list that holds each
  offender's exact size (grow or shrink-without-updating both fail). No file is over today (`ScreensTest.kt`, 547 lines, was split by screen on Oct 13). Largest main file is 345.
- **Duplication report** (`python scripts/duplication-report.py`, stdlib only) and a guard (`DuplicationTest`) with the same rules in Kotlin:
  windows of 8 normalized lines repeated in main Kotlin. Today: 18 duplicated lines, 2 repeated windows, all in `OkHttpHttpClient.kt`. The
  guard fails if the number grows and also if it falls (so the allowed number is lowered). The Kotlin and Python numbers must match.
- **Test-time budget** (`./gradlew testTimeBudget`, runs in the root `check`/`build` after the tests): reads the JUnit XML. It warns at the
  CLAUDE.md numbers (suite 5 min of summed class time, one class 60 s) and fails only at twice that (10 min, 120 s), because a cold machine
  (first Robolectric load, empty Gradle cache) must not fail a build by itself. Today: 111 s summed over 89 classes (Debug and Release counted
  separately, so it is not wall time), slowest `ScreensTest` 36 s.
- **CI** `.github/workflows/ci.yml`: `./gradlew build` on JDK 17 (the project's Java and Kotlin targets), official actions only
  (`actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`, `actions/upload-artifact` for failure reports), no secrets, no
  publishing. It has never run: it starts on Kevin's first push.

Still open (Kevin's decision): detekt (a new Gradle plugin; the lint baseline covers the Android rules only), PMD CPD or jscpd (the stdlib
report above covers repeated lines, not near-copies), item 8 (the weekly cleanup run and archiving closed `findings.md` rows).

## Kevin's answers (Oct 14)

- **detekt added** (1.23.8, Kevin: "if it is valuable, add it"): on every Kotlin module except the fixture app, `config/detekt/detekt.yml` tuned to
  house style (lines up to 160, named thresholds, Compose names), a baseline per module (312 entries: 124 long lines, 99 bare numbers,
  22 swallowed exceptions, 19 return counts, ...). Only new issues fail. The two unused items it found were deleted at once.
- **PMD CPD: considered, skipped.** It would add near-copies with renamed names; today's exact-copy count is 18 lines, so the benefit is small.
- **CI** runs on every push and pull request (Kevin).

## No baselines (Oct 14, Kevin: "there should not be things grandfathered")

Every issue detekt and Lint found was fixed in code, none suppressed, and the baseline files are gone: 312 detekt issues across eight
modules and 8 Lint issues. Lint now fails on warnings too (`warningsAsErrors`). The duplication guard allows 0 copied lines (the
one copied block in `OkHttpHttpClient` became one helper). The rule settings in `config/detekt/detekt.yml` (lines up to 160,
named thresholds, Compose function names) are the only tuning; a new one needs a strong reason written next to it.
The sections above describe the baselines as they were on the way here.
