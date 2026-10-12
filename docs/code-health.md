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
