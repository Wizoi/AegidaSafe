"""Duplication report for the main Kotlin sources (code-health item 7). Python standard library only.

    python scripts/duplication-report.py            # print repeated blocks and the totals
    python scripts/duplication-report.py --window 6 # use a different window

A "window" is WINDOW consecutive normalized lines. A window that appears in two or more places is a repeat. The
headline number is the count of source lines that sit inside any repeated window ("duplicated lines").

The same rules are implemented in guards/.../CodeHealthTest.kt (DuplicationTest), which fails the build when that number grows past
the recorded one. Keep the two in step: lines are trimmed, runs of spaces become one, and these are skipped: blank
lines, import/package lines, comment lines (starting with //, /*, * ) and lines of two characters or fewer ("}", ")").
"""
import argparse
import pathlib
import sys

WINDOW = 8
MODULES = ["core", "detectors", "platform", "store", "alerts", "app"]


def normalized_lines(text):
    """(original line number, normalized text) for each line that counts."""
    out = []
    for number, raw in enumerate(text.splitlines(), start=1):
        line = " ".join(raw.split())
        if len(line) <= 2:
            continue
        if line.startswith(("import ", "package ", "//", "/*", "*")):
            continue
        out.append((number, line))
    return out


def scan(root, window):
    files = {}
    for module in MODULES:
        for path in sorted((root / module / "src" / "main").rglob("*.kt")):
            files[path.relative_to(root).as_posix()] = normalized_lines(path.read_text(encoding="utf-8"))
    seen = {}
    for name, lines in files.items():
        for i in range(len(lines) - window + 1):
            key = "\n".join(text for _, text in lines[i:i + window])
            seen.setdefault(key, []).append((name, i))
    covered = {}  # file -> set of indexes into its normalized lines
    groups = []
    for key, places in seen.items():
        if len(places) < 2:
            continue
        groups.append(places)
        for name, i in places:
            covered.setdefault(name, set()).update(range(i, i + window))
    return files, groups, covered


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--window", type=int, default=WINDOW)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent.parent
    files, groups, covered = scan(root, args.window)
    total = sum(len(v) for v in covered.values())
    for name in sorted(covered, key=lambda n: -len(covered[n])):
        idx = sorted(covered[name])
        first, last = files[name][idx[0]][0], files[name][idx[-1]][0]
        print(f"{len(idx):4d} duplicated lines  {name}  (lines {first}-{last})")
    print(f"\nwindow={args.window}: {len(groups)} repeated windows, {total} duplicated lines in {len(covered)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
